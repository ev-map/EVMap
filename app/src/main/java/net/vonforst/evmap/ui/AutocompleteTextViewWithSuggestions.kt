package net.vonforst.evmap.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet

class AutocompleteTextViewWithSuggestions(ctx: Context, args: AttributeSet) :
    androidx.appcompat.widget.AppCompatAutoCompleteTextView(ctx, args) {
    override fun enoughToFilter(): Boolean = true

    override fun onFocusChanged(
        focused: Boolean, direction: Int,
        previouslyFocusedRect: Rect?
    ) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused && adapter != null) {
            performFiltering(text, 0)
        }
    }
}