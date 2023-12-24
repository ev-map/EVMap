package net.vonforst.evmap.auto

import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.text.SpannableString
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
import net.vonforst.evmap.adapter.formatTeslaParkingFee
import net.vonforst.evmap.adapter.formatTeslaPricing
import net.vonforst.evmap.api.availability.AvailabilityRepository
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.tesla.Pricing
import net.vonforst.evmap.api.availability.tesla.TeslaChargingOwnershipGraphQlApi
import net.vonforst.evmap.api.chargeprice.ChargepriceApi
import net.vonforst.evmap.api.createApi
import net.vonforst.evmap.api.fronyx.FronyxApi
import net.vonforst.evmap.api.fronyx.PredictionData
import net.vonforst.evmap.api.fronyx.PredictionRepository
import net.vonforst.evmap.api.iconForPlugType
import net.vonforst.evmap.api.nameForPlugType
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Cost
import net.vonforst.evmap.model.FaultReport
import net.vonforst.evmap.model.Favorite
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.ChargeLocationsRepository
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.ChargerIconGenerator
import net.vonforst.evmap.ui.availabilityText
import net.vonforst.evmap.ui.getMarkerTint
import net.vonforst.evmap.viewmodel.Status
import net.vonforst.evmap.viewmodel.awaitFinished
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.floor
import kotlin.math.roundToInt


class ChargerDetailScreen(ctx: CarContext, val chargerSparse: ChargeLocation) : Screen(ctx) {
    var charger: ChargeLocation? = null
    var photo: Bitmap? = null
    private var availability: ChargeLocationStatus? = null
    private var prediction: PredictionData? = null
    private var fronyxSupported = false
    private var teslaSupported = false

    val prefs = PreferenceDataSource(ctx)
    private val db = AppDatabase.getInstance(carContext)
    private val repo =
        ChargeLocationsRepository(createApi(prefs.dataSource, ctx), lifecycleScope, db, prefs)
    private val availabilityRepo = AvailabilityRepository(ctx)
    private val predictionRepo = PredictionRepository(ctx)
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    private val imageSize = 128  // images should be 128dp according to docs
    private val imageSizeLarge = 480  // images should be 480 x 480 dp according to docs

    private val iconGen =
        ChargerIconGenerator(carContext, null, height = imageSize)

    private val maxRows = ctx.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PANE)
    private val largeImageSupported =
        ctx.carAppApiLevel >= 4  // since API 4, Row.setImage is supported

    private var favorite: Favorite? = null
    private var favoriteUpdateJob: Job? = null

    init {
        loadCharger()
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
                            .setFlags(Action.FLAG_PRIMARY)
                        .setBackgroundColor(CarColor.PRIMARY)
                        .setOnClickListener {
                            navigateToCharger(charger)
                        }
                        .build())
                        if (ChargepriceApi.isChargerSupported(charger)) {
                            addAction(
                                Action.Builder()
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

                charger.cost?.let { addText(generateCostStatusText(it)) }
                charger.faultReport?.let { fault ->
                    addText(generateFaultReportTitle(fault))
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
                    addText(generateCostStatusText(it))
                    it.getDetailText()?.let { addText(it) }
                }
            }.build())
            // row 3: fault report (if exists)
            charger.faultReport?.let { fault ->
                rows.add(Row.Builder().apply {
                    setTitle(generateFaultReportTitle(fault))
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
        if (rows.count() < maxRows && charger.generalInformation != null) {
            rows.add(Row.Builder().apply {
                setTitle(carContext.getString(R.string.general_info))
                addText(charger.generalInformation)
            }.build())
        }
        if (rows.count() < maxRows && charger.amenities != null) {
            rows.add(Row.Builder().apply {
                setTitle(carContext.getString(R.string.amenities))
                addText(charger.amenities)
            }.build())
        }
        if (rows.count() < maxRows && ((fronyxSupported && prefs.predictionEnabled) || teslaSupported)) {
            rows.add(1, Row.Builder().apply {
                setTitle(
                    if (fronyxSupported) {
                        carContext.getString(R.string.utilization_prediction) + " (" + carContext.getString(
                            R.string.powered_by_fronyx
                        ) + ")"
                    } else carContext.getString(R.string.average_utilization)
                )
                generatePredictionGraph()?.let { addText(it) }
                    ?: addText(carContext.getString(if (prediction != null) R.string.auto_no_data else R.string.loading))
            }.build())
        }
        if (rows.count() < maxRows && teslaSupported) {
            val teslaPricing = availability?.extraData as? Pricing
            rows.add(3, Row.Builder().apply {
                setTitle(carContext.getString(R.string.cost))
                teslaPricing?.let {
                    var text = formatTeslaPricing(teslaPricing, carContext) as CharSequence
                    formatTeslaParkingFee(teslaPricing, carContext)?.let { text += "\n\n" + it }
                    addText(text)
                } ?: {
                    addText(carContext.getString(if (prediction != null) R.string.auto_no_data else R.string.loading))
                }
            }.build())
        }
        return rows
    }

    private fun generatePredictionGraph(): CharSequence? {
        val predictionData = prediction ?: return null
        val graphData = predictionData.predictionGraph?.toList() ?: return null
        val maxValue = predictionData.maxValue

        val maxWidth = if (BuildConfig.FLAVOR_automotive == "automotive") 25 else 18
        val step = maxOf(graphData.size.toFloat() / maxWidth, 1f)
        val values = graphData.map { it.second }

        val graph = buildGraph(values, step, maxValue, predictionData.isPercentage)

        val measurer = TextMeasurer(carContext)
        val width = measurer.measureText(graph)

        val startTime = timeFormat.format(graphData[0].first)
        val endTime = timeFormat.format(graphData.last().first)

        val baseWidth = measurer.measureText(startTime + endTime)
        val spaceWidth = measurer.measureText(" ")
        val numSpaces = floor((width - baseWidth) / spaceWidth).toInt()
        val legend = startTime + " ".repeat(numSpaces) + endTime

        return graph + "\n" + legend
    }

    private fun buildGraph(
        values: List<Double>,
        step: Float,
        maxValue: Double,
        isPercentage: Boolean
    ): CharSequence {
        val sparklines = "▁▂▃▄▅▆▇█"
        val graph = SpannableStringBuilder()
        var i = 0f
        while (i.roundToInt() < values.size) {
            val v = values[i.roundToInt()]
            val fraction = v / maxValue
            val sparkline = sparklines[(fraction * (sparklines.length - 1)).roundToInt()].toString()

            val color = if (isPercentage) {
                when (v) {
                    in 0.0..0.5 -> CarColor.GREEN
                    in 0.5..0.8 -> CarColor.YELLOW
                    else -> CarColor.RED
                }
            } else {
                if (v < maxValue) CarColor.GREEN else CarColor.RED
            }

            graph.append(
                sparkline,
                ForegroundCarColorSpan.create(color),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            i += step
        }
        return graph
    }

    private fun generateCostStatusText(cost: Cost): CharSequence {
        val string = SpannableString(cost.getStatusText(carContext, emoji = true))
        // replace emoji with CarIcon
        string.indexOf('⚡').takeIf { it >= 0 }?.let { index ->
            string.setSpan(
                CarIconSpan.create(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_lightning
                        )
                    ).setTint(CarColor.YELLOW).build()
                ), index, index + 1, SpannableString.SPAN_INCLUSIVE_EXCLUSIVE
            )
        }
        string.indexOf('\uD83C').takeIf { it >= 0 }?.let { index ->
            string.setSpan(
                CarIconSpan.create(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_parking
                        )
                    ).setTint(CarColor.BLUE).build()
                ), index, index + 2, SpannableString.SPAN_INCLUSIVE_EXCLUSIVE
            )
        }
        return string
    }


    private fun generateFaultReportTitle(fault: FaultReport): CharSequence {
        val string = SpannableString(
            carContext.getString(
                R.string.auto_fault_report_date,
                fault.created?.atZone(ZoneId.systemDefault())
                    ?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
            )
        )
        // replace emoji with CarIcon
        string.setSpan(
            CarIconSpan.create(
                CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext,
                        R.drawable.ic_fault_report
                    )
                ).setTint(CarColor.YELLOW).build()
            ), 0, 1, SpannableString.SPAN_INCLUSIVE_EXCLUSIVE
        )
        return string
    }

    private fun generateChargepointsText(charger: ChargeLocation): SpannableStringBuilder {
        val chargepointsText = SpannableStringBuilder()
        charger.chargepointsMerged.forEachIndexed { i, cp ->
            chargepointsText.apply {
                if (i > 0) append(" · ")
                append("${cp.count}× ")
                val plugIcon = iconForPlugType(cp.type)
                if (plugIcon != 0) {
                    append(
                        nameForPlugType(carContext.stringProvider(), cp.type),
                        CarIconSpan.create(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    plugIcon
                                )
                            ).setTint(
                                CarColor.createCustom(Color.WHITE, Color.BLACK)
                            ).build()
                        ),
                        Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                    )
                } else {
                    append(nameForPlugType(carContext.stringProvider(), cp.type))
                }
                cp.formatPower()?.let {
                    append(" ")
                    append(it)
                }
            }
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
                Uri.parse("geo:${coord.lat},${coord.lng}")
            )
        carContext.startCarApp(intent)
    }

    private fun loadCharger() {
        lifecycleScope.launch {
            favorite = db.favoritesDao().findFavorite(chargerSparse.id, chargerSparse.dataSource)

            val response = repo.getChargepointDetail(chargerSparse.id).awaitFinished()
            if (response.status == Status.SUCCESS) {
                val charger = response.data!!
                this@ChargerDetailScreen.charger = charger
                invalidate()

                val photo = charger.photos?.firstOrNull()
                photo?.let {
                    val density = carContext.resources.displayMetrics.density
                    val size =
                        (density * if (largeImageSupported) imageSizeLarge else imageSize).roundToInt()
                    val url = photo.getUrl(size = size)
                    val request = ImageRequest.Builder(carContext).data(url).build()
                    val img =
                        (carContext.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap ?: return@let

                    // draw icon on top of image
                    val icon = iconGen.getBitmap(
                        tint = getMarkerTint(charger),
                        fault = charger.faultReport != null,
                        multi = charger.isMulti()
                    )

                    val outImg = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val iconSmall = icon.scale(
                        (size * 0.4 / icon.height * icon.width).roundToInt(),
                        (size * 0.4).roundToInt()
                    )
                    val canvas = Canvas(outImg)

                    val m = Matrix()
                    m.setRectToRect(
                        RectF(0f, 0f, img.width.toFloat(), img.height.toFloat()),
                        RectF(0f, 0f, size.toFloat(), size.toFloat()),
                        Matrix.ScaleToFit.CENTER
                    )
                    canvas.drawBitmap(
                        img.copy(Bitmap.Config.ARGB_8888, false), m, null
                    )
                    canvas.drawBitmap(
                        iconSmall,
                        0f,
                        (size - iconSmall.height * 1.1).toFloat(),
                        null
                    )
                    this@ChargerDetailScreen.photo = outImg
                }
                fronyxSupported = charger.chargepoints.any {
                    FronyxApi.isChargepointSupported(
                        charger,
                        it
                    )
                } && !availabilityRepo.isSupercharger(charger)
                teslaSupported = availabilityRepo.isTeslaSupported(charger)

                invalidate()

                availability = availabilityRepo.getAvailability(charger).data

                invalidate()

                prediction = predictionRepo.getPredictionData(charger, availability)

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