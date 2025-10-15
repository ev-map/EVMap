package net.vonforst.evmap.auto

import androidx.annotation.StringRes
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.Template

class TextDialogScreen(
    ctx: CarContext,
    @StringRes val title: Int,
    @StringRes val message: Int
) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        return LongMessageTemplate.Builder(carContext.getString(message)).apply {
            setTitle(carContext.getString(title))
            setHeaderAction(Action.BACK)
        }.build()
    }
}