package net.vonforst.evmap.auto

import android.Manifest
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import net.vonforst.evmap.R

/**
 * Screen to grant location permission
 */
class PermissionScreen(ctx: CarContext, val session: EVMapSession) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(carContext.getString(R.string.auto_location_permission_needed))
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.grant_on_phone))
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener(ParkedOnlyOnClickListener.create {
                        requestPermissions()
                    })
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.cancel))
                    .setOnClickListener {
                        carContext.finishCarApp()
                    }
                    .build(),
            )
            .build()
    }

    private fun requestPermissions() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        carContext.requestPermissions(listOf(permission)) { granted, rejected ->
            if (granted.contains(permission)) {
                session.bindLocationService()
                screenManager.push(
                    WelcomeScreen(
                        carContext,
                        session
                    )
                )
            } else {
                requestPermissions()
            }
        }
    }
}