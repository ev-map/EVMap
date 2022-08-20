package net.vonforst.evmap.ui

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.roundToInt

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

/**
 * DialogFragment that uses Material styling.
 * This needs a bit of a workaround, see also
 * https://github.com/material-components/material-components-android/issues/540 and
 * https://dev.to/bhullnatik/how-to-use-material-dialogs-with-dialogfragment-28i1
 */
open class MaterialDialogFragment : AppCompatDialogFragment() {

    private lateinit var dialogView: View
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = MaterialAlertDialogBuilder(requireContext(), theme).apply {
            dialogView =
                onCreateView(LayoutInflater.from(requireContext()), null, savedInstanceState)!!

            setView(dialogView)
        }.create()
        onViewCreated(dialogView, savedInstanceState)
        return dialog
    }

    override fun getView(): View {
        return dialogView
    }

    override fun onStart() {
        super.onStart()
        // make sure that custom view fills whole dialog height
        (view.parent as View).layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        (view.parent.parent as View).layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        (view.parent.parent.parent as View).layoutParams.height =
            ViewGroup.LayoutParams.MATCH_PARENT
    }

    /**
     * Makes the dialog fill the whole width & height of the screen, with an optional maximum
     * width in dp. Call this during onStart.
     */
    fun setFullSize(maxWidthDp: Int? = null) {
        val width = resources.displayMetrics.widthPixels
        val maxWidth = if (maxWidthDp != null) {
            val density = resources.displayMetrics.density
            (maxWidthDp * density).roundToInt()
        } else null

        dialog?.window?.setLayout(
            if (maxWidth == null || width < maxWidth) WindowManager.LayoutParams.MATCH_PARENT else maxWidth,
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }
}