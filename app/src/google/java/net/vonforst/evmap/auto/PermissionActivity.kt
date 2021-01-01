package net.vonforst.evmap.auto

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ResultReceiver
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class PermissionActivity : Activity() {
    companion object {
        const val EXTRA_RESULT_RECEIVER = "result_receiver";
        const val RESULT_GRANTED = "granted"
    }

    private lateinit var resultReceiver: ResultReceiver
    private val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private val requestCode = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent != null) {
            resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)!!
            if (!hasPermissions(permissions)) {
                ActivityCompat.requestPermissions(this, permissions, requestCode)
            } else {
                onComplete(
                    requestCode,
                    permissions,
                    intArrayOf(PackageManager.PERMISSION_GRANTED)
                )
            }
        } else {
            finish()
        }
    }

    private fun onComplete(requestCode: Int, permissions: Array<String>?, grantResults: IntArray) {
        val bundle = Bundle()
        bundle.putBoolean(
            RESULT_GRANTED,
            grantResults.all { it == PackageManager.PERMISSION_GRANTED })
        resultReceiver.send(requestCode, bundle)
        finish()
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        var result = true
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                result = false
                break
            }
        }
        return result
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onComplete(requestCode, permissions, grantResults)
    }
}