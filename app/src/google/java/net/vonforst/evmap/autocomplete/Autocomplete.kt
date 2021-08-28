package net.vonforst.evmap.autocomplete

import android.content.Context
import net.vonforst.evmap.storage.PreferenceDataSource

fun getAutocompleteProviders(context: Context) =
    if (PreferenceDataSource(context).searchProvider == "google") {
        listOf(GooglePlacesAutocompleteProvider(context), MapboxAutocompleteProvider(context))
    } else {
        listOf(MapboxAutocompleteProvider(context), GooglePlacesAutocompleteProvider(context))
    }