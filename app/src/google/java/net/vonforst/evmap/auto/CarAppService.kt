package net.vonforst.evmap.auto

import android.Manifest
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.*
import androidx.car.app.model.Distance.UNIT_KILOMETERS
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.*
import net.vonforst.evmap.*
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.api.availability.getAvailability
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.api.goingelectric.GoingElectricApi
import net.vonforst.evmap.api.nameForPlugType
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.ui.ChargerIconGenerator
import net.vonforst.evmap.ui.availabilityText
import net.vonforst.evmap.ui.getMarkerTint
import net.vonforst.evmap.utils.distanceBetween
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt


interface LocationAwareScreen {
    fun updateLocation(location: Location)
}

class CarAppService : androidx.car.app.CarAppService() {
    override fun createHostValidator(): HostValidator {
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session {
        return EVMapSession(this)
    }
}

class EVMapSession(val cas: CarAppService) : Session(), LifecycleObserver {
    var mapScreen: LocationAwareScreen? = null
        set(value) {
            field = value
            location?.let { value?.updateLocation(it) }
        }
    private var location: Location? = null
    private var locationService: CarLocationService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, ibinder: IBinder) {
            val binder: CarLocationService.LocalBinder = ibinder as CarLocationService.LocalBinder
            locationService = binder.service
            locationService?.requestLocationUpdates()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreateScreen(intent: Intent): Screen {
        return if (locationPermissionGranted()) {
            WelcomeScreen(carContext, this)
        } else {
            PermissionScreen(carContext, this)
        }
    }

    private fun locationPermissionGranted() =
        ContextCompat.checkSelfPermission(
            carContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra(CarLocationService.EXTRA_LOCATION) as Location?
            val mapScreen = this@EVMapSession.mapScreen
            if (location != null && mapScreen != null) {
                mapScreen.updateLocation(location)
            }
            this@EVMapSession.location = location
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun bindLocationService() {
        if (!locationPermissionGranted()) return
        cas.bindService(
            Intent(cas, CarLocationService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private fun unbindLocationService() {
        locationService?.let { service ->
            service.removeLocationUpdates()
            cas.unbindService(serviceConnection)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private fun registerBroadcastReceiver() {
        LocalBroadcastManager.getInstance(cas).registerReceiver(
            locationReceiver,
            IntentFilter(CarLocationService.ACTION_BROADCAST)
        );
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    private fun unregisterBroadcastReceiver() {
        LocalBroadcastManager.getInstance(cas).unregisterReceiver(locationReceiver)
    }
}

/**
 * Welcome screen with selection between favorites and nearby chargers
 */
class WelcomeScreen(ctx: CarContext, val session: EVMapSession) : Screen(ctx), LocationAwareScreen {
    private var location: Location? = null

    override fun onGetTemplate(): Template {
        session.mapScreen = this
        return PlaceListMapTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.app_name))
            location?.let {
                setAnchor(Place.Builder(CarLocation.create(it)).build())
            }
            setItemList(ItemList.Builder().apply {
                addItem(Row.Builder()
                    .setTitle(carContext.getString(R.string.auto_chargers_closeby))
                    .setImage(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_address
                            )
                        )
                            .setTint(CarColor.DEFAULT).build()
                    )
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(MapScreen(carContext, session, favorites = false))
                    }
                    .build())
                addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.auto_favorites))
                        .setImage(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_fav
                                )
                            )
                                .setTint(CarColor.DEFAULT).build()
                        )
                        .setBrowsable(true)
                        .setOnClickListener {
                            screenManager.push(MapScreen(carContext, session, favorites = true))
                        }
                        .build())
            }.build())
            setCurrentLocationEnabled(true)
            setHeaderAction(Action.APP_ICON)
            build()
        }.build()
    }

    override fun updateLocation(location: Location) {
        this.location = location
        invalidate()
    }
}

/**
 * Screen to grant location permission
 */
class PermissionScreen(ctx: CarContext, val session: EVMapSession) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(carContext.getString(R.string.auto_location_permission_needed))
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.grant_on_phone))
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener(ParkedOnlyOnClickListener.create {
                        val intent = Intent(carContext, PermissionActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(
                                PermissionActivity.EXTRA_RESULT_RECEIVER,
                                object : ResultReceiver(null) {
                                    override fun onReceiveResult(
                                        resultCode: Int,
                                        resultData: Bundle?
                                    ) {
                                        if (resultData!!.getBoolean(PermissionActivity.RESULT_GRANTED)) {
                                            session.bindLocationService()
                                            screenManager.push(
                                                WelcomeScreen(
                                                    carContext,
                                                    session
                                                )
                                            )
                                        }
                                    }
                                })
                        carContext.startActivity(intent)
                        CarToast.makeText(
                            carContext,
                            R.string.opened_on_phone,
                            CarToast.LENGTH_LONG
                        ).show()
                    })
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.cancel))
                    .setOnClickListener {
                        carContext.finishCarApp()
                    }
                    .build(),
            )
            .build()
    }
}

/**
 * Main map screen showing either nearby chargers or favorites
 */
class MapScreen(ctx: CarContext, val session: EVMapSession, val favorites: Boolean = false) :
    Screen(ctx), LocationAwareScreen {
    private var updateCoroutine: Job? = null
    private var numUpdates = 0
    private val maxNumUpdates = 3

    private var location: Location? = null
    private var lastUpdateLocation: Location? = null
    private var chargers: List<ChargeLocation>? = null
    private val api by lazy {
        GoingElectricApi.create(ctx.getString(R.string.goingelectric_key), context = ctx)
    }
    private val searchRadius = 5 // kilometers
    private val updateThreshold = 2000 // meters
    private val availabilityUpdateThreshold = Duration.ofMinutes(1)
    private var availabilities: MutableMap<Long, Pair<ZonedDateTime, ChargeLocationStatus>> =
        HashMap()
    private val maxRows = 6

    override fun onGetTemplate(): Template {
        session.mapScreen = this
        return PlaceListMapTemplate.Builder().apply {
            setTitle(
                carContext.getString(
                    if (favorites) {
                        R.string.auto_favorites
                    } else {
                        R.string.auto_chargers_closeby
                    }
                )
            )
            location?.let {
                setAnchor(Place.Builder(CarLocation.create(it)).build())
            } ?: setLoading(true)
            chargers?.take(maxRows)?.let { chargerList ->
                val builder = ItemList.Builder()
                chargerList.forEach { charger ->
                    builder.addItem(formatCharger(charger))
                }
                builder.setNoItemsMessage(
                    carContext.getString(
                        if (favorites) {
                            R.string.auto_no_favorites_found
                        } else {
                            R.string.auto_no_chargers_found
                        }
                    )
                )
                setItemList(builder.build())
            } ?: setLoading(true)
            setCurrentLocationEnabled(true)
            setHeaderAction(Action.BACK)
            build()
        }.build()
    }

    private fun formatCharger(charger: ChargeLocation): Row {
        val color = ContextCompat.getColor(carContext, getMarkerTint(charger))
        val place =
            Place.Builder(CarLocation.create(charger.coordinates.lat, charger.coordinates.lng))
                .setMarker(
                    PlaceMarker.Builder()
                        .setColor(CarColor.createCustom(color, color))
                        .build()
                )
                .build()

        return Row.Builder().apply {
            setTitle(charger.name)
            val text = SpannableStringBuilder()

            // distance
            location?.let {
                val distance = distanceBetween(
                    it.latitude, it.longitude,
                    charger.coordinates.lat, charger.coordinates.lng
                ) / 1000
                text.append(
                    "distance",
                    DistanceSpan.create(Distance.create(distance, UNIT_KILOMETERS)),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // power
            if (text.isNotEmpty()) text.append(" · ")
            text.append("${charger.maxPower.roundToInt()} kW")

            // availability
            availabilities[charger.id]?.second?.let { av ->
                val status = av.status.values.flatten()
                val available = availabilityText(status)
                val total = charger.chargepoints.sumBy { it.count }

                if (text.isNotEmpty()) text.append(" · ")
                text.append(
                    "$available/$total",
                    ForegroundCarColorSpan.create(carAvailabilityColor(status)),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            addText(text)
            setMetadata(
                Metadata.Builder()
                    .setPlace(place)
                    .build()
            )

            setOnClickListener {
                screenManager.push(ChargerDetailScreen(carContext, charger))
            }
        }.build()
    }

    override fun updateLocation(location: Location) {
        this.location = location
        if (updateCoroutine != null) {
            // don't update while still loading last update
            return
        }

        invalidate()

        if (lastUpdateLocation == null ||
            location.distanceTo(lastUpdateLocation) > updateThreshold
        ) {
            lastUpdateLocation = location
            // update displayed chargers
            loadChargers(location)
        }
    }

    private val db = AppDatabase.getInstance(carContext)

    private fun loadChargers(location: Location) {
        numUpdates++
        println(numUpdates)
        if (numUpdates > maxNumUpdates) {
            CarToast.makeText(carContext, R.string.auto_no_refresh_possible, CarToast.LENGTH_LONG)
                .show()
            return
        }
        updateCoroutine = lifecycleScope.launch {
            // load chargers
            if (favorites) {
                chargers = db.chargeLocationsDao().getAllChargeLocationsAsync().sortedBy {
                    distanceBetween(
                        location.latitude, location.longitude,
                        it.coordinates.lat, it.coordinates.lng
                    )
                }
            } else {
                val response = api.getChargepointsRadius(
                    location.latitude,
                    location.longitude,
                    searchRadius,
                    zoom = 16f
                )
                chargers =
                    response.body()?.chargelocations?.filterIsInstance(ChargeLocation::class.java)
                chargers?.let {
                    if (it.size < 6) {
                        // try again with larger radius
                        val response = api.getChargepointsRadius(
                            location.latitude,
                            location.longitude,
                            searchRadius * 5,
                            zoom = 16f
                        )
                        chargers =
                            response.body()?.chargelocations?.filterIsInstance(ChargeLocation::class.java)
                    }
                }
            }

            // remove outdated availabilities
            availabilities = availabilities.filter {
                Duration.between(
                    it.value.first,
                    ZonedDateTime.now()
                ) > availabilityUpdateThreshold
            }.toMutableMap()

            // update availabilities
            chargers?.take(maxRows)?.map {
                lifecycleScope.async {
                    // update only if not yet stored
                    if (!availabilities.containsKey(it.id)) {
                        val date = ZonedDateTime.now()
                        val availability = getAvailability(it).data
                        if (availability != null) {
                            availabilities[it.id] = date to availability
                        }
                    }
                }
            }?.awaitAll()

            updateCoroutine = null
            invalidate()
        }
    }
}

class ChargerDetailScreen(ctx: CarContext, val chargerSparse: ChargeLocation) : Screen(ctx) {
    var charger: ChargeLocation? = null
    var photo: Bitmap? = null
    private var availability: ChargeLocationStatus? = null

    val apikey = ctx.getString(R.string.goingelectric_key)
    private val api by lazy {
        GoingElectricApi.create(apikey, context = ctx)
    }

    private val iconGen = ChargerIconGenerator(carContext, null, oversize = 1.4f, height = 64)

    override fun onGetTemplate(): Template {
        if (charger == null) loadCharger()

        return PaneTemplate.Builder(
            Pane.Builder().apply {
                charger?.let { charger ->
                    addRow(Row.Builder().apply {
                        setTitle(charger.address.toString())

                        val icon = iconGen.getBitmap(
                            tint = getMarkerTint(charger),
                            fault = charger.faultReport != null,
                            multi = charger.isMulti()
                        )
                        setImage(
                            CarIcon.Builder(IconCompat.createWithBitmap(icon)).build(),
                            Row.IMAGE_TYPE_LARGE
                        )

                        val chargepointsText = SpannableStringBuilder()
                        charger.chargepointsMerged.forEachIndexed { i, cp ->
                            if (i > 0) chargepointsText.append(" · ")
                            chargepointsText.append(
                                "${cp.count}× ${
                                    nameForPlugType(
                                        carContext,
                                        cp.type
                                    )
                                } ${cp.formatPower()}"
                            )
                            availability?.status?.get(cp)?.let { status ->
                                chargepointsText.append(
                                    " (${availabilityText(status)}/${cp.count})",
                                    ForegroundCarColorSpan.create(carAvailabilityColor(status)),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                        }
                        addText(chargepointsText)
                    }.build())
                    addRow(Row.Builder().apply {
                        photo?.let {
                            setImage(
                                CarIcon.Builder(IconCompat.createWithBitmap(photo)).build(),
                                Row.IMAGE_TYPE_LARGE
                            )
                        }
                        val operatorText = StringBuilder().apply {
                            charger.operator?.let { append(it) }
                            charger.network?.let {
                                if (isNotEmpty()) append(" · ")
                                append(it)
                            }
                        }
                        setTitle(operatorText)

                        charger.cost?.let { addText(it.getStatusText(carContext, emoji = true)) }
                        charger.faultReport?.created?.let {
                            addText(
                                carContext.getString(
                                    R.string.auto_fault_report_date,
                                    it.atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
                                )
                            )
                        }

                        /*val types = charger.chargepoints.map { it.type }.distinct()
                        if (types.size == 1) {
                            setImage(
                                CarIcon.of(IconCompat.createWithResource(carContext, iconForPlugType(types[0]))),
                                Row.IMAGE_TYPE_ICON)
                        }*/
                    }.build())
                    addAction(Action.Builder()
                        .setIcon(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_navigation
                                )
                            ).build()
                        )
                        .setTitle(carContext.getString(R.string.navigate))
                        .setBackgroundColor(CarColor.PRIMARY)
                        .setOnClickListener {
                            navigateToCharger(charger)
                        }
                        .build())
                    addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.open_in_app))
                            .setOnClickListener(ParkedOnlyOnClickListener.create {
                                val intent = Intent(carContext, MapsActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra(EXTRA_CHARGER_ID, charger.id)
                                    .putExtra(EXTRA_LAT, charger.coordinates.lat)
                                    .putExtra(EXTRA_LON, charger.coordinates.lng)
                                carContext.startActivity(intent)
                                CarToast.makeText(
                                    carContext,
                                    R.string.opened_on_phone,
                                    CarToast.LENGTH_LONG
                                ).show()
                            })
                            .build()
                    )
                } ?: setLoading(true)
            }.build()
        ).apply {
            setTitle(chargerSparse.name)
            setHeaderAction(Action.BACK)
        }.build()
    }

    private fun navigateToCharger(charger: ChargeLocation) {
        val coord = charger.coordinates
        val intent =
            Intent(
                CarContext.ACTION_NAVIGATE,
                Uri.parse("geo:0,0?q=${coord.lat},${coord.lng}(${charger.name})")
            )
        carContext.startCarApp(intent)
    }

    private fun loadCharger() {
        lifecycleScope.launch {
            val response = api.getChargepointDetail(chargerSparse.id)
            charger = response.body()?.chargelocations?.get(0) as ChargeLocation

            val photo = charger?.photos?.firstOrNull()
            photo?.let {
                val size = (carContext.resources.displayMetrics.density * 64).roundToInt()
                val url = "https://api.goingelectric.de/chargepoints/photo/?key=${apikey}" +
                        "&id=${photo.id}&size=${size}"
                val request = ImageRequest.Builder(carContext).data(url).build()
                this@ChargerDetailScreen.photo =
                    (carContext.imageLoader.execute(request).drawable as BitmapDrawable).bitmap
            }

            availability = charger?.let { getAvailability(it).data }

            invalidate()
        }
    }
}

fun carAvailabilityColor(status: List<ChargepointStatus>): CarColor {
    val unknown = status.any { it == ChargepointStatus.UNKNOWN }
    val available = status.count { it == ChargepointStatus.AVAILABLE }
    val allFaulted = status.all { it == ChargepointStatus.FAULTED }

    return if (unknown) {
        CarColor.DEFAULT
    } else if (available > 0) {
        CarColor.GREEN
    } else if (allFaulted) {
        CarColor.RED
    } else {
        CarColor.BLUE
    }
}