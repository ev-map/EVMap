package net.vonforst.evmap.autocomplete

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import com.mapbox.geojson.BoundingBox
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions
import net.vonforst.evmap.R
import net.vonforst.evmap.fragment.REQUEST_AUTOCOMPLETE
import net.vonforst.evmap.viewmodel.PlaceWithBounds


fun launchAutocomplete(fragment: Fragment) {
    val placeOptions = PlaceOptions.builder()
        .build(PlaceOptions.MODE_CARDS)

    val intent = PlaceAutocomplete.IntentBuilder()
        .accessToken(fragment.getString(R.string.mapbox_key))
        .placeOptions(placeOptions)
        .build(fragment.requireActivity())
        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    fragment.startActivityForResult(intent, REQUEST_AUTOCOMPLETE)

    // show keyboard
    val imm = fragment.requireContext()
        .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.toggleSoftInput(0, 0)
}

fun handleAutocompleteResult(intent: Intent): PlaceWithBounds? {
    val place = PlaceAutocomplete.getPlace(intent) ?: return null
    val bbox = place.bbox()?.toLatLngBounds()
    val center = place.center()!!.toLatLng()
    return PlaceWithBounds(center, bbox)
}

private fun BoundingBox.toLatLngBounds(): LatLngBounds {
    return LatLngBounds(
        southwest().toLatLng(),
        northeast().toLatLng()
    )
}

private fun Point.toLatLng(): LatLng = LatLng(this.latitude(), this.longitude())