package net.vonforst.evmap.auto

import androidx.annotation.StringRes
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import net.vonforst.evmap.R

/**
 * Screen to grant permission
 */
class PermissionScreen(
    ctx: CarContext,
    val session: EVMapSession,
    @StringRes val message: Int,
    val permissions: List<String>
) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(carContext.getString(message))
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
        carContext.requestPermissions(permissions) { granted, rejected ->
            if (granted.containsAll(permissions)) {
                screenManager.pop()
            } else {
                requestPermissions()
            }
        }
    }
}