package net.vonforst.evmap.autocomplete

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.CharacterStyle
import android.text.style.StyleSpan
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import com.car2go.maps.util.SphericalUtil
import com.mapbox.api.geocoding.v5.GeocodingCriteria
import com.mapbox.api.geocoding.v5.MapboxGeocoding
import com.mapbox.api.geocoding.v5.models.CarmenFeature
import com.mapbox.geojson.BoundingBox
import com.mapbox.geojson.Point
import net.vonforst.evmap.R
import java.io.IOException
import kotlin.math.roundToInt

class MapboxAutocompleteProvider(val context: Context) : AutocompleteProvider {
    private val bold: CharacterStyle = StyleSpan(Typeface.BOLD)
    private val results = HashMap<String, CarmenFeature>()

    override fun autocomplete(query: String, location: LatLng?): List<AutocompletePlace> {
        val result = MapboxGeocoding.builder().apply {
            location?.let {
                proximity(Point.fromLngLat(location.longitude, location.latitude))
            }
            accessToken(context.getString(R.string.mapbox_key))
            autocomplete(true)
            this.query(query)
        }.build().executeCall()
        if (!result.isSuccessful) {
            throw IOException(result.message())
        }
        return result.body()!!.features().map { feature ->
            results[feature.id()!!] = feature
            val primaryText =
                (feature.matchingText() ?: feature.text())!! + (feature.address()?.let { " $it" }
                    ?: "")
            val secondaryText =
                (feature.matchingPlaceName() ?: feature.placeName())!!.replace("$primaryText, ", "")
            AutocompletePlace(
                highlightMatch(primaryText, query),
                secondaryText,
                feature.id()!!,
                location?.let { location ->
                    SphericalUtil.computeDistanceBetween(
                        feature.center()!!.toLatLng(), location
                    ).roundToInt()
                },
                getPlaceTypes(feature)
            )
        }
    }

    private fun getPlaceTypes(feature: CarmenFeature): List<AutocompletePlaceType> {
        val types = feature.placeType()?.mapNotNull {
            when (it) {
                GeocodingCriteria.TYPE_COUNTRY -> AutocompletePlaceType.COUNTRY
                GeocodingCriteria.TYPE_REGION -> AutocompletePlaceType.ADMINISTRATIVE_AREA_LEVEL_1
                GeocodingCriteria.TYPE_POSTCODE -> AutocompletePlaceType.POSTAL_CODE
                GeocodingCriteria.TYPE_DISTRICT -> AutocompletePlaceType.ADMINISTRATIVE_AREA_LEVEL_2
                GeocodingCriteria.TYPE_PLACE -> AutocompletePlaceType.ADMINISTRATIVE_AREA_LEVEL_3
                GeocodingCriteria.TYPE_LOCALITY -> AutocompletePlaceType.LOCALITY
                GeocodingCriteria.TYPE_NEIGHBORHOOD -> AutocompletePlaceType.NEIGHBORHOOD
                GeocodingCriteria.TYPE_ADDRESS -> AutocompletePlaceType.STREET_ADDRESS
                GeocodingCriteria.TYPE_POI -> AutocompletePlaceType.POINT_OF_INTEREST
                GeocodingCriteria.TYPE_POI_LANDMARK -> AutocompletePlaceType.POINT_OF_INTEREST
                else -> null
            }
        } ?: emptyList()
        val categories = feature.properties()?.get("category")?.asString?.split(", ")?.mapNotNull {
            // Place categories are defined at https://docs.mapbox.com/api/search/geocoding/#point-of-interest-category-coverage
            // We try to find a matching entry in the enum.
            // TODO: map categories that are not named the same
            AutocompletePlaceType.valueOfOrNull(it.uppercase().replace(" ", "_"))
        } ?: emptyList()
        return types + categories
    }

    private fun highlightMatch(text: String, query: String): CharSequence {
        val result = SpannableString(text)

        val startPos = text.lowercase().indexOf(query.lowercase())
        if (startPos > -1) {
            val endPos = startPos + query.length
            result.setSpan(bold, startPos, endPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return result
    }

    override suspend fun getDetails(id: String): PlaceWithBounds {
        val place = results[id]!!
        results.clear()
        return PlaceWithBounds(
            place.center()!!.toLatLng(),
            place.geometry()?.bbox()?.toLatLngBounds()
        )
    }

    override fun getAttributionString(): Int = R.string.powered_by_mapbox

    override fun getAttributionImage(dark: Boolean): Int = R.drawable.mapbox_logo_icon
}

private fun BoundingBox.toLatLngBounds(): LatLngBounds {
    return LatLngBounds(
        southwest().toLatLng(),
        northeast().toLatLng()
    )
}

private fun Point.toLatLng(): LatLng = LatLng(this.latitude(), this.longitude())