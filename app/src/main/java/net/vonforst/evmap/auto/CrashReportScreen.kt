package net.vonforst.evmap.auto

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import net.vonforst.evmap.R
import org.acra.dialog.CrashReportDialogHelper

/**
 * ACRA-compatible crash reporting screen for the Car App Library
 *
 * only used on Android Automotive OS
 */
class CrashReportScreen(ctx: CarContext, intent: Intent) : Screen(ctx) {
    val helper = CrashReportDialogHelper(ctx, intent)
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(carContext.getString(R.string.crash_report_text)).apply {
            setHeaderAction(Action.APP_ICON)
            setTitle(carContext.getString(R.string.app_name))
            addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.ok))
                    .setFlags(Action.FLAG_PRIMARY)
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener {
                        helper.sendCrash(null, null)
                        screenManager.pop()
                    }.build()
            )
            addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.cancel))
                    .setOnClickListener {
                        helper.cancelReports()
                        screenManager.pop()
                    }.build()
            )
        }.build()
    }
}
