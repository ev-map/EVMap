package net.vonforst.evmap.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import net.vonforst.evmap.R
import java.time.ZonedDateTime
import kotlin.math.roundToInt

class BarGraphView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    var zeroHeight = 4 * context.resources.displayMetrics.density
    var barWidth = 16 * context.resources.displayMetrics.density
    var barMargin = 2 * context.resources.displayMetrics.density

    var barDrawableUnavailable =
        AppCompatResources.getDrawable(context, R.drawable.bar_graph_unavailable)!!
    var barDrawableAvailable =
        AppCompatResources.getDrawable(context, R.drawable.bar_graph_available)!!

    var data: Map<ZonedDateTime, Int>? = null
        set(value) {
            field = value
            invalidate()
        }

    var activeAlpha = 0.87f
    var inactiveAlpha = 0.60f

    private lateinit var graphBounds: Rect

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        graphBounds = Rect(paddingLeft, paddingTop, w - paddingRight, h - paddingBottom)
    }

    override fun onDraw(canvas: Canvas) {
        val data = data ?: return
        val maxValue = data.maxOf { it.value }

        canvas.apply {
            data.toSortedMap().entries.forEachIndexed { i, (t, v) ->
                val drawable = if (v > 0) barDrawableAvailable else barDrawableUnavailable

                val height =
                    zeroHeight + (graphBounds.height() - zeroHeight) * v.toFloat() / maxValue
                drawable.setBounds(
                    graphBounds.left + ((barWidth + barMargin) * i).roundToInt(),
                    graphBounds.bottom - height.roundToInt(),
                    graphBounds.left + ((barWidth + barMargin) * i + barWidth).roundToInt(),
                    graphBounds.bottom
                )
                drawable.alpha = (inactiveAlpha * 255).roundToInt()
                drawable.draw(canvas)
            }
        }
    }
}