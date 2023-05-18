package net.vonforst.evmap.autocomplete

import android.content.Context
import android.graphics.Typeface
import android.text.style.CharacterStyle
import android.text.style.StyleSpan
import com.car2go.maps.google.adapter.AnyMapAdapter
import com.car2go.maps.util.SphericalUtil
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.Tasks.await
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesStatusCodes
import kotlinx.coroutines.tasks.await
import net.vonforst.evmap.R
import java.io.IOException
import java.util.concurrent.ExecutionException
import kotlin.math.sqrt


class GooglePlacesAutocompleteProvider(val context: Context) : AutocompleteProvider {
    private var token = AutocompleteSessionToken.newInstance()
    private val client = Places.createClient(context)
    private val bold: CharacterStyle = StyleSpan(Typeface.BOLD)

    override val id = "google"

    override fun autocomplete(
        query: String,
        location: com.car2go.maps.model.LatLng?
    ): List<AutocompletePlace> {
        val request = FindAutocompletePredictionsRequest.builder().apply {
            if (location != null) {
                locationBias = calcLocationBias(location)
                origin = LatLng(location.latitude, location.longitude)
            }
            sessionToken = token
            setQuery(query)
        }.build()
        try {
            val result =
                await(client.findAutocompletePredictions(request)).autocompletePredictions
            return result.map {
                AutocompletePlace(
                    it.getPrimaryText(bold),
                    it.getSecondaryText(bold),
                    it.placeId,
                    it.distanceMeters?.toDouble(),
                    it.placeTypes.map { AutocompletePlaceType.valueOf(it.name) })
            }
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause is ApiException) {
                if (cause.statusCode == PlacesStatusCodes.OVER_QUERY_LIMIT) {
                    throw ApiUnavailableException()
                } else if (cause.statusCode in listOf(
                        CommonStatusCodes.NETWORK_ERROR,
                        CommonStatusCodes.TIMEOUT, CommonStatusCodes.RECONNECTION_TIMED_OUT,
                        CommonStatusCodes.RECONNECTION_TIMED_OUT_DURING_UPDATE
                    )
                ) {
                    throw IOException(cause)
                }
            }
            throw e
        }
    }

    override suspend fun getDetails(id: String): PlaceWithBounds {
        val request =
            FetchPlaceRequest.builder(id, listOf(Place.Field.LAT_LNG, Place.Field.VIEWPORT)).build()
        try {
            val place = client.fetchPlace(request).await().place
            token = AutocompleteSessionToken.newInstance()
            return PlaceWithBounds(
                AnyMapAdapter.adapt(place.latLng),
                AnyMapAdapter.adapt(place.viewport)
            )
        } catch (e: ApiException) {
            if (e.statusCode == PlacesStatusCodes.OVER_QUERY_LIMIT) {
                throw ApiUnavailableException()
            } else {
                throw e
            }
        }
    }

    override fun getAttributionString(): Int = R.string.places_powered_by_google

    override fun getAttributionImage(dark: Boolean): Int =
        if (dark) R.drawable.places_powered_by_google_dark else R.drawable.places_powered_by_google_light

    private fun calcLocationBias(location: com.car2go.maps.model.LatLng): RectangularBounds {
        val radius = 100e3 // meters
        val northEast =
            SphericalUtil.computeOffset(
                location,
                radius * sqrt(2.0),
                45.0
            )
        val southWest =
            SphericalUtil.computeOffset(
                location,
                radius * sqrt(2.0),
                225.0
            )
        return RectangularBounds.newInstance(
            LatLngBounds(
                AnyMapAdapter.adapt(southWest),
                AnyMapAdapter.adapt(northEast)
            )
        )
    }
}