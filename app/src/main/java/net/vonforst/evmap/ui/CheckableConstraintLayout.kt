package net.vonforst.evmap.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Checkable
import androidx.constraintlayout.widget.ConstraintLayout


class CheckableConstraintLayout(ctx: Context, attrs: AttributeSet) : ConstraintLayout(ctx, attrs),
    Checkable {
    private var onCheckedChangeListener: ((View, Boolean) -> Unit)? = null
    private var checked = false
    private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)

    override fun setChecked(b: Boolean) {
        if (b != checked) {
            checked = b;
            refreshDrawableState();
            onCheckedChangeListener?.invoke(this, checked);
        }
    }

    override fun isChecked(): Boolean {
        return checked
    }

    override fun toggle() {
        checked = !checked
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray? {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (isChecked) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET)
        }
        return drawableState
    }

    /**
     * Register a callback to be invoked when the checked state of this view changes.
     *
     * @param listener the callback to call on checked state change
     */
    fun setOnCheckedChangeListener(listener: (View, Boolean) -> Unit) {
        onCheckedChangeListener = listener
    }
}