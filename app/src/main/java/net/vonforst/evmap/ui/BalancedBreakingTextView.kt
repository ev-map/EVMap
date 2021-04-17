package net.vonforst.evmap.ui

import android.content.Context
import android.text.Layout
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.ceil

class BalancedBreakingTextView(context: Context, attrs: AttributeSet) :
    AppCompatTextView(context, attrs) {

    @Override
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (layout != null) {
            val width =
                ceil(getMaxLineWidth(layout)).toInt() + compoundPaddingLeft + compoundPaddingRight
            val height = measuredHeight
            setMeasuredDimension(width, height)
        }
    }

    private fun getMaxLineWidth(layout: Layout): Float {
        var maxWidth = 0.0f
        for (i in 0 until layout.lineCount) {
            if (layout.getLineWidth(i) > maxWidth) {
                maxWidth = layout.getLineWidth(i)
            }
        }
        return maxWidth
    }
}