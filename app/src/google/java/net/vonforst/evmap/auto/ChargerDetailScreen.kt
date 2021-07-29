package net.vonforst.evmap.auto

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.vonforst.evmap.*
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.getAvailability
import net.vonforst.evmap.api.createApi
import net.vonforst.evmap.api.nameForPlugType
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.model.ChargeLocation
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

    private val iconGen =
        ChargerIconGenerator(carContext, null, oversize = 1.4f, height = imageSize)

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
                        }.ifEmpty {
                            carContext.getString(R.string.unknown_operator)
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
        val referenceData = referenceData.value ?: return
        lifecycleScope.launch {
            val response = api.getChargepointDetail(referenceData, chargerSparse.id)
            if (response.status == Status.SUCCESS) {
                charger = response.data!!

                val photo = charger?.photos?.firstOrNull()
                photo?.let {
                    val size = (carContext.resources.displayMetrics.density * 64).roundToInt()
                    val url = photo.getUrl(size = size)
                    val request = ImageRequest.Builder(carContext).data(url).build()
                    this@ChargerDetailScreen.photo =
                        (carContext.imageLoader.execute(request).drawable as BitmapDrawable).bitmap
                }

                availability = charger?.let { getAvailability(it).data }

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