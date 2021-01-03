package net.vonforst.evmap.ui

import android.content.Context
import android.content.DialogInterface
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog

private fun dialogEditText(ctx: Context): Pair<View, EditText> {
    val container = FrameLayout(ctx)
    container.setPadding(
        (16 * ctx.resources.displayMetrics.density).toInt(), 0,
        (16 * ctx.resources.displayMetrics.density).toInt(), 0
    )
    val input = EditText(ctx)
    input.isSingleLine = true
    container.addView(input)
    return container to input
}

fun showEditTextDialog(
    ctx: Context,
    customize: (AlertDialog.Builder, EditText) -> Unit
): AlertDialog {
    val (container, input) = dialogEditText(ctx)
    val dialogBuilder = AlertDialog.Builder(ctx)
        .setView(container)

    customize(dialogBuilder, input)

    val dialog = dialogBuilder.show()


    // move dialog to top
    val attrs = dialog.window?.attributes?.apply {
        gravity = Gravity.TOP
    }
    dialog.window?.attributes = attrs

    // focus and show keyboard
    input.requestFocus()
    input.postDelayed({
        val imm =
            ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }, 100)
    input.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            val text = input.text
            val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            if (text != null && button != null) {
                button.performClick()
                return@setOnEditorActionListener true
            }
        }
        false
    }
    return dialog
}