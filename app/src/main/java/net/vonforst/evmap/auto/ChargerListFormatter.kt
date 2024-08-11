package net.vonforst.evmap.auto

import android.location.Location
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarIconSpan
import androidx.car.app.model.CarLocation
import androidx.car.app.model.CarText
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Pane
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.FILTERS_FAVORITES
import net.vonforst.evmap.ui.ChargerIconGenerator
import net.vonforst.evmap.ui.availabilityText
import net.vonforst.evmap.ui.getMarkerTint
import net.vonforst.evmap.utils.distanceBetween
import java.time.ZonedDateTime
import kotlin.math.roundToInt

interface ChargerListDelegate : ItemList.OnItemVisibilityChangedListener {
    val locationError: Boolean
    val loadingError: Boolean
    val maxRows: Int
    val filterStatus: Long
    val location: Location?
    val energyLevel: EnergyLevel?
    fun onChargerClick(charger: ChargeLocation)
}

class ChargerListFormatter(val carContext: CarContext, val screen: ChargerListDelegate) {
    private val iconGen = ChargerIconGenerator(carContext, null, height = 96)
    var favorites: Set<Long> = emptySet()

    fun buildChargerList(
        chargers: List<ChargeLocation>?,
        availabilities: Map<Long, Pair<ZonedDateTime, ChargeLocationStatus?>>
    ): ItemList? {
        return if (chargers != null) {
            val chargerList = chargers.take(screen.maxRows)
            val builder = ItemList.Builder()
            // only show the city if not all chargers are in the same city
            val showCity = chargerList.map { it.address?.city }.distinct().size > 1
            chargerList.forEach { charger ->
                builder.addItem(
                    formatCharger(
                        charger,
                        availabilities,
                        showCity,
                        charger.id in favorites
                    )
                )
            }
            builder.setNoItemsMessage(
                carContext.getString(
                    if (screen.filterStatus == FILTERS_FAVORITES) {
                        R.string.auto_no_favorites_found
                    } else {
                        R.string.auto_no_chargers_found
                    }
                )
            )
            builder.setOnItemsVisibilityChangedListener(screen)
            builder.build()
        } else {
            if (screen.loadingError) {
                val builder = ItemList.Builder()
                builder.setNoItemsMessage(
                    carContext.getString(R.string.connection_error)
                )
                builder.build()
            } else if (screen.locationError) {
                val builder = ItemList.Builder()
                builder.setNoItemsMessage(
                    carContext.getString(R.string.location_error)
                )
                builder.build()
            } else {
                null
            }
        }
    }

    private fun formatCharger(
        charger: ChargeLocation,
        availabilities: Map<Long, Pair<ZonedDateTime, ChargeLocationStatus?>>,
        showCity: Boolean,
        isFavorite: Boolean
    ): Row {
        val markerTint = getMarkerTint(charger)
        val backgroundTint = if ((charger.maxPower ?: 0.0) > 100) {
            R.color.charger_100kw_dark  // slightly darker color for better contrast
        } else {
            markerTint
        }
        val color = ContextCompat.getColor(carContext, backgroundTint)
        val place =
            Place.Builder(CarLocation.create(charger.coordinates.lat, charger.coordinates.lng))
                .setMarker(
                    PlaceMarker.Builder()
                        .setColor(CarColor.createCustom(color, color))
                        .build()
                )
                .build()

        val icon = iconGen.getBitmap(
            markerTint,
            fault = charger.faultReport != null,
            multi = charger.isMulti(),
            fav = isFavorite
        )
        val iconSpan =
            CarIconSpan.create(CarIcon.Builder(IconCompat.createWithBitmap(icon)).build())

        return Row.Builder().apply {
            // only show the city if not all chargers are in the same city (-> showCity == true)
            // and the city is not already contained in the charger name
            val title = SpannableStringBuilder().apply {
                append(" ", iconSpan, SpannableString.SPAN_INCLUSIVE_EXCLUSIVE)
                append(" ")
                append(charger.name)
            }
            if (showCity && charger.address?.city != null && charger.address.city !in charger.name) {
                val titleWithCity = SpannableStringBuilder().apply {
                    append("", iconSpan, SpannableString.SPAN_INCLUSIVE_EXCLUSIVE)
                    append(" ")
                    append("${charger.name} · ${charger.address.city}")
                }
                setTitle(CarText.Builder(titleWithCity).addVariant(title).build())
            } else {
                setTitle(title)
            }

            val text = SpannableStringBuilder()

            // distance
            screen.location?.let {
                val distanceMeters = distanceBetween(
                    it.latitude, it.longitude,
                    charger.coordinates.lat, charger.coordinates.lng
                )
                text.append(
                    "distance",
                    DistanceSpan.create(
                        roundValueToDistance(
                            distanceMeters,
                            screen.energyLevel?.distanceDisplayUnit?.value,
                            carContext
                        )
                    ),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // power
            val power = charger.maxPower
            if (power != null) {
                if (text.isNotEmpty()) text.append(" · ")
                text.append("${power.roundToInt()} kW")
            }

            // availability
            availabilities[charger.id]?.second?.let { av ->
                val status = av.status.values.flatten()
                val available = availabilityText(status)
                val total = charger.chargepoints.sumOf { it.count }

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
                screen.onChargerClick(charger)
            }
        }.build()
    }

    fun buildSingleCharger(
        charger: ChargeLocation,
        availability: ChargeLocationStatus?,
        onClick: () -> Unit
    ) = Pane.Builder().apply {
        val icon = iconGen.getBitmap(
            getMarkerTint(charger),
            fault = charger.faultReport != null,
            multi = charger.isMulti(),
            fav = charger.id in favorites
        )


        addRow(Row.Builder().apply {
            setImage(CarIcon.Builder(IconCompat.createWithBitmap(icon)).build())
            setTitle(charger.address.toString())
            addText(generateChargepointsText(charger, availability, carContext))
        }.build())
        addAction(Action.Builder().apply {
            setTitle(carContext.getString(R.string.show_more))
            setOnClickListener(onClick)
        }.build())
        addAction(Action.Builder().apply {
            setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext,
                        R.drawable.ic_navigation
                    )
                ).build()
            )
            setTitle(carContext.getString(R.string.navigate))
            setBackgroundColor(CarColor.PRIMARY)
            setOnClickListener {
                navigateToCharger(carContext, charger)
            }
        }.build())
    }.build()
}