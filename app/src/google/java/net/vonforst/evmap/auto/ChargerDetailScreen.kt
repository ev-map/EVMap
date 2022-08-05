package net.vonforst.evmap.auto

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.scale
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.vonforst.evmap.*
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.getAvailability
import net.vonforst.evmap.api.chargeprice.ChargepriceApi
import net.vonforst.evmap.api.createApi
import net.vonforst.evmap.api.nameForPlugType
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Favorite
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.ChargerIconGenerator
import net.vonforst.evmap.ui.availabilityText
import net.vonforst.evmap.ui.getMarkerTint
import net.vonforst.evmap.viewmodel.Status
import net.vonforst.evmap.viewmodel.getReferenceData
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

class ChargerDetailScreen(ctx: CarContext, val chargerSparse: ChargeLocation) : Screen(ctx) {
    var charger: ChargeLocation? = null
    var photo: Bitmap? = null
    private var availability: ChargeLocationStatus? = null

    val prefs = PreferenceDataSource(ctx)
    private val db = AppDatabase.getInstance(carContext)
    private val api by lazy {
        createApi(prefs.dataSource, ctx)
    }
    private val referenceData = api.getReferenceData(lifecycleScope, carContext)

    private val imageSize = 128  // images should be 128dp according to docs
    private val imageSizeLarge = 480  // images should be 480 x 480 dp according to docs

    private val iconGen =
        ChargerIconGenerator(carContext, null, height = imageSize)

    private val maxRows = if (ctx.carAppApiLevel >= 2) {
        ctx.constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PANE)
    } else 2
    private val largeImageSupported =
        ctx.carAppApiLevel >= 4  // since API 4, Row.setImage is supported

    private var favorite: Favorite? = null
    private var favoriteUpdateJob: Job? = null

    init {
        referenceData.observe(this) {
            loadCharger()
        }
    }

    override fun onGetTemplate(): Template {
        if (charger == null) loadCharger()

        return PaneTemplate.Builder(
            Pane.Builder().apply {
                charger?.let { charger ->
                    if (largeImageSupported) {
                        photo?.let {
                            setImage(CarIcon.Builder(IconCompat.createWithBitmap(it)).build())
                        }
                    }
                    generateRows(charger).forEach { addRow(it) }
                    addAction(
                        Action.Builder()
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
                    charger.chargepriceData?.country?.let { country ->
                        if (ChargepriceApi.isCountrySupported(country, charger.dataSource)) {
                            addAction(Action.Builder()
                                .setIcon(
                                    CarIcon.Builder(
                                        IconCompat.createWithResource(
                                            carContext,
                                            R.drawable.ic_chargeprice
                                        )
                                    ).build()
                                )
                                .setTitle(carContext.getString(R.string.auto_prices))
                                .setOnClickListener {
                                    screenManager.push(ChargepriceScreen(carContext, charger))
                                }
                                .build())
                        }
                    }
                } ?: setLoading(true)
            }.build()
        ).apply {
            setTitle(chargerSparse.name)
            setHeaderAction(Action.BACK)
            charger?.let { charger ->
                setActionStrip(
                    ActionStrip.Builder().apply {
                        if (BuildConfig.FLAVOR_automotive != "automotive") {
                            // show "Open in app" action if not running on Android Automotive
                            addAction(
                                Action.Builder()
                                    .setTitle(carContext.getString(R.string.open_in_app))
                                    .setOnClickListener(ParkedOnlyOnClickListener.create {
                                        val intent = Intent(carContext, MapsActivity::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            .putExtra(EXTRA_CHARGER_ID, chargerSparse.id)
                                            .putExtra(EXTRA_LAT, chargerSparse.coordinates.lat)
                                            .putExtra(EXTRA_LON, chargerSparse.coordinates.lng)
                                        carContext.startActivity(intent)
                                        CarToast.makeText(
                                            carContext,
                                            R.string.opened_on_phone,
                                            CarToast.LENGTH_LONG
                                        ).show()
                                    })
                                    .build()
                            )
                        }
                        // show fav action
                        addAction(Action.Builder()
                            .setOnClickListener {
                                favorite?.let {
                                    deleteFavorite(it)
                                } ?: run {
                                    insertFavorite(charger)
                                }
                            }
                            .setIcon(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(
                                        carContext,
                                        if (favorite != null) {
                                            R.drawable.ic_fav
                                        } else {
                                            R.drawable.ic_fav_no
                                        }
                                    )
                                )
                                    .setTint(CarColor.DEFAULT).build()
                            )
                            .build())
                            .build()
                    }.build()
                )
            }
        }.build()
    }

    private fun insertFavorite(charger: ChargeLocation) {
        if (favoriteUpdateJob?.isCompleted == false) return
        favoriteUpdateJob = lifecycleScope.launch {
            db.chargeLocationsDao().insert(charger)
            val fav = Favorite(
                chargerId = charger.id,
                chargerDataSource = charger.dataSource
            )
            val id = db.favoritesDao().insert(fav)[0]
            favorite = fav.copy(favoriteId = id)
            invalidate()
        }
    }

    private fun deleteFavorite(fav: Favorite) {
        if (favoriteUpdateJob?.isCompleted == false) return
        favoriteUpdateJob = lifecycleScope.launch {
            db.favoritesDao().delete(fav)
            favorite = null
            invalidate()
        }
    }

    private fun generateRows(charger: ChargeLocation): List<Row> {
        val rows = mutableListOf<Row>()
        val photo = photo

        // Row 1: address + chargepoints
        rows.add(Row.Builder().apply {
            setTitle(charger.address.toString())

            if (photo == null) {
                // show just the icon
                val icon = iconGen.getBitmap(
                    tint = getMarkerTint(charger),
                    fault = charger.faultReport != null,
                    multi = charger.isMulti()
                )
                setImage(
                    CarIcon.Builder(IconCompat.createWithBitmap(icon)).build(),
                    Row.IMAGE_TYPE_LARGE
                )
            } else if (!largeImageSupported) {
                // show the photo with icon
                setImage(
                    CarIcon.Builder(IconCompat.createWithBitmap(photo)).build(),
                    Row.IMAGE_TYPE_LARGE
                )
            }
            addText(generateChargepointsText(charger))
        }.build())
        if (maxRows <= 3) {
            // row 2: operator + cost + fault report
            rows.add(Row.Builder().apply {
                if (photo != null && !largeImageSupported) {
                    setImage(
                        CarIcon.Builder(IconCompat.createWithBitmap(photo)).build(),
                        Row.IMAGE_TYPE_LARGE
                    )
                }
                val operatorText = generateOperatorText(charger)
                setTitle(operatorText)

                charger.cost?.let { addText(it.getStatusText(carContext, emoji = true)) }
                charger.faultReport?.let { fault ->
                    addText(
                        carContext.getString(
                            R.string.auto_fault_report_date,
                            fault.created?.atZone(ZoneId.systemDefault())
                                ?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
                        )
                    )
                }
            }.build())
        } else {
            // row 2: operator + cost + cost description
            rows.add(Row.Builder().apply {
                if (photo != null && !largeImageSupported) {
                    setImage(
                        CarIcon.Builder(IconCompat.createWithBitmap(photo)).build(),
                        Row.IMAGE_TYPE_LARGE
                    )
                }
                val operatorText = generateOperatorText(charger)
                setTitle(operatorText)
                charger.cost?.let {
                    addText(it.getStatusText(carContext, emoji = true))
                    it.getDetailText()?.let { addText(it) }
                }
            }.build())
            // row 3: fault report (if exists)
            charger.faultReport?.let { fault ->
                rows.add(Row.Builder().apply {
                    setTitle(
                        carContext.getString(
                            R.string.auto_fault_report_date,
                            fault.created?.atZone(ZoneId.systemDefault())
                                ?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
                        )
                    )
                    fault.description?.let {
                        addText(
                            HtmlCompat.fromHtml(
                                it.replace("\n", " · "),
                                HtmlCompat.FROM_HTML_MODE_LEGACY
                            )
                        )
                    }
                }.build())
            }
            // row 4: opening hours + location description
            charger.openinghours?.let { hours ->
                val title =
                    hours.getStatusText(carContext).ifEmpty { carContext.getString(R.string.hours) }
                rows.add(Row.Builder().apply {
                    setTitle(title)
                    hours.description?.let { addText(it) }
                    charger.locationDescription?.let { addText(it) }
                }.build())
            }
        }
        return rows
    }

    private fun generateChargepointsText(charger: ChargeLocation): SpannableStringBuilder {
        val chargepointsText = SpannableStringBuilder()
        charger.chargepointsMerged.forEachIndexed { i, cp ->
            if (i > 0) chargepointsText.append(" · ")
            chargepointsText.append(
                "${cp.count}× ${
                    nameForPlugType(
                        carContext.stringProvider(),
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
        return chargepointsText
    }

    private fun generateOperatorText(charger: ChargeLocation) =
        if (charger.operator != null && charger.network != null) {
            if (charger.operator.contains(charger.network)) {
                charger.operator
            } else if (charger.network.contains(charger.operator)) {
                charger.network
            } else {
                "${charger.operator} · ${charger.network}"
            }
        } else if (charger.operator != null) {
            charger.operator
        } else if (charger.network != null) {
            charger.network
        } else {
            carContext.getString(R.string.unknown_operator)
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
        val referenceData = referenceData.value ?: return
        lifecycleScope.launch {
            favorite = db.favoritesDao().findFavorite(chargerSparse.id, chargerSparse.dataSource)

            val response = api.getChargepointDetail(referenceData, chargerSparse.id)
            if (response.status == Status.SUCCESS) {
                val charger = response.data!!

                val photo = charger.photos?.firstOrNull()
                photo?.let {
                    val density = carContext.resources.displayMetrics.density
                    val url = if (largeImageSupported) {
                        photo.getUrl(size = (imageSizeLarge * density).roundToInt())
                    } else {
                        photo.getUrl(size = (imageSize * density).roundToInt())
                    }
                    val request = ImageRequest.Builder(carContext).data(url).build()
                    var img =
                        (carContext.imageLoader.execute(request).drawable as BitmapDrawable).bitmap

                    // draw icon on top of image
                    val icon = iconGen.getBitmap(
                        tint = getMarkerTint(charger),
                        fault = charger.faultReport != null,
                        multi = charger.isMulti()
                    )

                    img = img.copy(Bitmap.Config.ARGB_8888, true)
                    val iconSmall = icon.scale(
                        (img.height * 0.4 / icon.height * icon.width).roundToInt(),
                        (img.height * 0.4).roundToInt()
                    )
                    val canvas = Canvas(img)
                    canvas.drawBitmap(
                        iconSmall,
                        0f,
                        (img.height - iconSmall.height * 1.1).toFloat(),
                        null
                    )
                    this@ChargerDetailScreen.photo = img
                }
                this@ChargerDetailScreen.charger = charger

                availability = getAvailability(charger).data

                invalidate()
            } else {
                withContext(Dispatchers.Main) {
                    CarToast.makeText(carContext, R.string.connection_error, CarToast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }
}