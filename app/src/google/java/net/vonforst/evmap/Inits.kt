package net.vonforst.evmap

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.places.api.Places

fun init(context: Context) {
    Places.initialize(context, context.getString(R.string.google_maps_key));
}

fun checkPlayServices(activity: Activity): Boolean {
    val request = 9000
    val apiAvailability = GoogleApiAvailability.getInstance()
    val resultCode = apiAvailability.isGooglePlayServicesAvailable(activity)
    if (resultCode != ConnectionResult.SUCCESS) {
        if (apiAvailability.isUserResolvableError(resultCode)) {
            apiAvailability.getErrorDialog(activity, resultCode, request)?.show()
        } else {
            Log.d("EVMap", "This device is not supported.")
        }
        return false
    }
    return true
}