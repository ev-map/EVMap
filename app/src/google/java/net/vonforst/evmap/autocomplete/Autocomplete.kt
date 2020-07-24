package net.vonforst.evmap.autocomplete

import android.content.Context
import net.vonforst.evmap.viewmodel.PlaceWithBounds

fun launchAutocomplete(context: Context) {
    val fields = listOf(Place.Field.LAT_LNG, Place.Field.VIEWPORT)
    val intent: Intent = Autocomplete.IntentBuilder(
        AutocompleteActivityMode.OVERLAY, fields
    )
        .build(requireContext())
        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    startActivityForResult(intent, REQUEST_AUTOCOMPLETE)

    // show keyboard
    val imm =
        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.toggleSoftInput(0, 0)
}

fun handleAutocompleteResult(intent: Intent): PlaceWithBounds? =
    Autocomplete.getPlaceFromIntent(data!!)