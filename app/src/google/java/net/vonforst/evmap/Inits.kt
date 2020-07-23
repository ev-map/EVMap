package net.vonforst.evmap

import android.content.Context

fun init(context: Context) {
    Places.initialize(context, context.getString(R.string.google_maps_key));
}

fun checkPlayServices(): Boolean {
    val request = 9000
    val apiAvailability = GoogleApiAvailability.getInstance()
    val resultCode = apiAvailability.isGooglePlayServicesAvailable(this)
    if (resultCode != ConnectionResult.SUCCESS) {
        if (apiAvailability.isUserResolvableError(resultCode)) {
            apiAvailability.getErrorDialog(this, resultCode, request).show()
        } else {
            Log.d("EVMap", "This device is not supported.")
        }
        return false
    }
    return true
}