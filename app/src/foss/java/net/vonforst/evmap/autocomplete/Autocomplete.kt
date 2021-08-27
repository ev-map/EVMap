package net.vonforst.evmap.autocomplete

import android.content.Context

fun getAutocompleteProviders(context: Context) = listOf(MapboxAutocompleteProvider(context))