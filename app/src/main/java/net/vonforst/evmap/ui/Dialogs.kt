package net.vonforst.evmap.ui

import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
    customize: (MaterialAlertDialogBuilder, EditText) -> Unit
): AlertDialog {
    val (container, input) = dialogEditText(ctx)
    val dialogBuilder = MaterialAlertDialogBuilder(ctx)
        .setView(container)

    customize(dialogBuilder, input)

    val dialog = dialogBuilder.show()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

    // focus and show keyboard
    input.requestFocus()
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