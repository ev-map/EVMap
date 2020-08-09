package net.vonforst.evmap.autocomplete

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import com.car2go.maps.google.adapter.AnyMapAdapter
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import net.vonforst.evmap.fragment.REQUEST_AUTOCOMPLETE
import net.vonforst.evmap.viewmodel.PlaceWithBounds

fun launchAutocomplete(fragment: Fragment) {
    val fields = listOf(Place.Field.LAT_LNG, Place.Field.VIEWPORT)
    val intent: Intent = Autocomplete.IntentBuilder(
        AutocompleteActivityMode.OVERLAY, fields
    )
        .build(fragment.requireActivity())
        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    fragment.startActivityForResult(intent, REQUEST_AUTOCOMPLETE)

    // show keyboard
    val imm = fragment.requireContext()
        .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.toggleSoftInput(0, 0)
}

fun handleAutocompleteResult(intent: Intent): PlaceWithBounds? {
    val place = Autocomplete.getPlaceFromIntent(intent)
    return PlaceWithBounds(AnyMapAdapter.adapt(place.latLng), AnyMapAdapter.adapt(place.viewport))
}