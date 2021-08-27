package net.vonforst.evmap.autocomplete

import android.content.Context

fun getAutocompleteProviders(context: Context) =
    listOf(GooglePlacesAutocompleteProvider(context), MapboxAutocompleteProvider(context))