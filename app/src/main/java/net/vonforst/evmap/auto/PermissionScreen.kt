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
    @StringRes val message: Int,
    val permissions: List<String>,
    val finishApp: Boolean = true
) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(carContext.getString(message))
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.grant_on_phone))
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setFlags(Action.FLAG_PRIMARY)
                    .setOnClickListener(ParkedOnlyOnClickListener.create {
                        requestPermissions()
                    })
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.cancel))
                    .setOnClickListener {
                        if (finishApp) {
                            carContext.finishCarApp()
                        } else {
                            // pop twice to get away from the screen that requires the permission
                            screenManager.pop()
                            screenManager.pop()
                        }
                    }
                    .build(),
            )
            .build()
    }

    private fun requestPermissions() {
        carContext.requestPermissions(permissions) { granted, _ ->
            if (granted.containsAll(permissions)) {
                screenManager.pop()
            } else {
                requestPermissions()
            }
        }
    }
}