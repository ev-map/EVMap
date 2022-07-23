package net.vonforst.evmap.auto

import androidx.annotation.StringRes
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.car.app.model.signin.InputSignInMethod
import androidx.car.app.model.signin.SignInTemplate
import net.vonforst.evmap.R

class TextPromptScreen(
    ctx: CarContext,
    @StringRes val title: Int,
    @StringRes val prompt: Int,
    val initialValue: String? = null,
    val cancelable: Boolean = true
) : Screen(ctx),
    InputCallback {
    private var inputText = ""

    override fun onGetTemplate(): Template {
        val signInMethod = InputSignInMethod.Builder(this).apply {
            initialValue?.let {
                setDefaultValue(it)
                inputText = initialValue
            }
            setShowKeyboardByDefault(true)
        }.build()
        return SignInTemplate.Builder(signInMethod).apply {
            setHeaderAction(Action.BACK)
            setInstructions(carContext.getString(prompt))
            setTitle(carContext.getString(title))
            if (cancelable) {
                addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.cancel))
                        .setOnClickListener(ParkedOnlyOnClickListener.create {
                            screenManager.pop()
                        })
                        .build()
                )
            }
            addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.ok))
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener(ParkedOnlyOnClickListener.create {
                        onInputSubmitted(inputText)
                    })
                    .build()
            )
        }.build()
    }

    override fun onInputTextChanged(text: String) {
        inputText = text
    }

    override fun onInputSubmitted(text: String) {
        setResult(text)
        screenManager.pop()
    }
}