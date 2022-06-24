package net.vonforst.evmap.auto

import androidx.annotation.StringRes
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.InputCallback
import androidx.car.app.model.Template
import androidx.car.app.model.signin.InputSignInMethod
import androidx.car.app.model.signin.SignInTemplate

class TextPromptScreen(
    ctx: CarContext,
    @StringRes val title: Int,
    @StringRes val prompt: Int,
    val initialValue: String? = null
) : Screen(ctx),
    InputCallback {
    override fun onGetTemplate(): Template {
        val signInMethod = InputSignInMethod.Builder(this).apply {
            initialValue?.let { setDefaultValue(it) }
            setShowKeyboardByDefault(true)
        }.build()
        return SignInTemplate.Builder(signInMethod).apply {
            setHeaderAction(Action.BACK)
            setInstructions(carContext.getString(prompt))
            setTitle(carContext.getString(title))
        }.build()
    }

    override fun onInputSubmitted(text: String) {
        setResult(text)
        screenManager.pop()
    }
}