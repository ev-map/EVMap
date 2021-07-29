package net.vonforst.evmap.auto

import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*
import net.vonforst.evmap.R

/**
 * Screen to grant location permission
 */
@androidx.car.app.annotations.ExperimentalCarApi
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
                        val intent = Intent(carContext, PermissionActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(
                                PermissionActivity.EXTRA_RESULT_RECEIVER,
                                object : ResultReceiver(null) {
                                    override fun onReceiveResult(
                                        resultCode: Int,
                                        resultData: Bundle?
                                    ) {
                                        if (resultData!!.getBoolean(PermissionActivity.RESULT_GRANTED)) {
                                            session.bindLocationService()
                                            screenManager.push(
                                                WelcomeScreen(
                                                    carContext,
                                                    session
                                                )
                                            )
                                        }
                                    }
                                })
                        carContext.startActivity(intent)
                        CarToast.makeText(
                            carContext,
                            R.string.opened_on_phone,
                            CarToast.LENGTH_LONG
                        ).show()
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
}