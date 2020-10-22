package net.vonforst.evmap.auto

import android.content.*
import android.location.Location
import android.os.IBinder
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.libraries.car.app.CarContext
import com.google.android.libraries.car.app.Screen
import com.google.android.libraries.car.app.model.*
import com.google.android.libraries.car.app.model.Distance.UNIT_KILOMETERS
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.api.availability.getAvailability
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.api.goingelectric.GoingElectricApi
import net.vonforst.evmap.ui.availabilityText
import net.vonforst.evmap.ui.getMarkerTint
import net.vonforst.evmap.utils.distanceBetween
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.roundToInt


class CarAppService : com.google.android.libraries.car.app.CarAppService(), LifecycleObserver {
    private lateinit var mapScreen: MapScreen
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

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra(CarLocationService.EXTRA_LOCATION) as Location?
            if (location != null) {
                mapScreen.updateLocation(location)
            }
        }
    }

    override fun onCreate() {
        mapScreen = MapScreen(carContext)
        lifecycle.addObserver(this)
    }

    override fun onCreateScreen(intent: Intent): Screen {
        return mapScreen
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private fun bindLocationService() {
        bindService(
            Intent(this, CarLocationService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private fun unbindLocationService() {
        locationService?.removeLocationUpdates()
        unbindService(serviceConnection)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private fun registerBroadcastReceiver() {
        LocalBroadcastManager.getInstance(this).registerReceiver(
            locationReceiver,
            IntentFilter(CarLocationService.ACTION_BROADCAST)
        );
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    private fun unregisterBroadcastReceiver() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationReceiver)
    }
}

class MapScreen(ctx: CarContext) : Screen(ctx) {
    private var location: Location? = null
    private var lastUpdateLocation: Location? = null
    private var chargers: List<ChargeLocation>? = null
    private val api by lazy {
        GoingElectricApi.create(ctx.getString(R.string.goingelectric_key), context = ctx)
    }
    private val searchRadius = 5 // kilometers
    private val updateThreshold = 50 // meters
    private val availabilityUpdateThreshold = Duration.ofMinutes(1)
    private var availabilities: MutableMap<Long, Pair<ZonedDateTime, ChargeLocationStatus>> =
        HashMap()
    private val maxRows = 6

    override fun getTemplate(): Template {
        return PlaceListMapTemplate.builder().apply {
            setTitle("EVMap")
            location?.let {
                setAnchor(Place.builder(LatLng.create(it)).build())
            }
            chargers?.take(maxRows)?.let { chargerList ->
                val builder = ItemList.Builder()
                chargerList.forEach { charger ->
                    builder.addItem(formatCharger(charger))
                }
                builder.setNoItemsMessage(carContext.getString(R.string.auto_no_chargers_found))
                setItemList(builder.build())
            }
            if (chargers == null || location == null) setIsLoading(true)
            setCurrentLocationEnabled(true)
            build()
        }.build()
    }

    private fun formatCharger(charger: ChargeLocation): Row {
        /*val icon = CarIcon.builder(IconCompat.createWithResource(carContext, R.drawable.ic_map_marker_charging))
            .setTint(CarColor.BLUE)
            .build()*/
        val color = ContextCompat.getColor(carContext, getMarkerTint(charger))
        val place = Place.builder(LatLng.create(charger.coordinates.lat, charger.coordinates.lng))
            .setMarker(
                PlaceMarker.builder()
                    .setColor(CarColor.createCustom(color, color))
                    .build()
            )
            .build()

        return Row.builder().apply {
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
            setMetadata(Metadata.ofPlace(place))
        }.build()
    }

    fun updateLocation(location: Location) {
        this.location = location

        if (lastUpdateLocation == null) invalidate()

        if (lastUpdateLocation == null ||
            location.distanceTo(lastUpdateLocation) > updateThreshold
        ) {
            lastUpdateLocation = location

            // update displayed chargers
            lifecycleScope.launch {
                // load chargers
                val response = api.getChargepointsRadius(
                    location.latitude,
                    location.longitude,
                    searchRadius,
                    zoom = 16f
                )
                chargers =
                    response.body()?.chargelocations?.filterIsInstance(ChargeLocation::class.java)

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

                invalidate()
            }
        }
    }
}

fun carAvailabilityColor(status: List<ChargepointStatus>): CarColor {
    val unknown = status.any { it == ChargepointStatus.UNKNOWN }
    val available = status.count { it == ChargepointStatus.AVAILABLE }

    return if (unknown) {
        CarColor.DEFAULT
    } else if (available > 0) {
        CarColor.GREEN
    } else {
        CarColor.RED
    }
}