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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import net.vonforst.evmap.R
import kotlin.math.roundToInt

private fun dialogEditText(ctx: Context): Pair<TextInputLayout, EditText> {
    val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_textinput, null)
    return view as TextInputLayout to view.findViewById(R.id.input)
}

fun showEditTextDialog(
    ctx: Context,
    customize: (MaterialAlertDialogBuilder, EditText) -> Unit,
    okAction: (String) -> Unit
): AlertDialog {
    val (container, input) = dialogEditText(ctx)
    val dialogBuilder = MaterialAlertDialogBuilder(ctx)
        .setView(container)
        .setPositiveButton(R.string.ok) { _, _ -> }
        .setNegativeButton(R.string.cancel) { _, _ -> }

    customize(dialogBuilder, input)

    val dialog = dialogBuilder.show()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

    val okButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

    // focus and show keyboard
    input.requestFocus()
    input.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            val text = input.text
            if (text != null && okButton != null) {
                okButton.performClick()
                return@setOnEditorActionListener true
            }
        }
        false
    }

    okButton?.setOnClickListener {
        if (input.text.isBlank()) {
            container.isErrorEnabled = true
            container.error = ctx.getString(R.string.required)
        } else {
            container.isErrorEnabled = false
            okAction(input.text.toString())
            dialog.dismiss()
        }
    }

    return dialog
}

/**
 * DialogFragment that uses Material styling.
 * This needs a bit of a workaround, see also
 * https://github.com/material-components/material-components-android/issues/540 and
 * https://dev.to/bhullnatik/how-to-use-material-dialogs-with-dialogfragment-28i1
 */
abstract class MaterialDialogFragment : AppCompatDialogFragment() {

    private lateinit var dialogView: View
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = MaterialAlertDialogBuilder(requireContext(), theme).apply {
            dialogView =
                createView(LayoutInflater.from(requireContext()), null, savedInstanceState)

            setView(dialogView)
        }.create()
        initView(dialogView, savedInstanceState)
        return dialog
    }

    abstract fun createView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View

    abstract fun initView(view: View, savedInstanceState: Bundle?)

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