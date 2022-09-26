package net.vonforst.evmap.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import net.vonforst.evmap.R
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

class BarGraphView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    var zeroHeight = 4 * context.resources.displayMetrics.density
    var barWidth = (16 * context.resources.displayMetrics.density).roundToInt()
    var barMargin = (2 * context.resources.displayMetrics.density).roundToInt()
    var legendWidth = 12 * context.resources.displayMetrics.density
    var legendLineLength = 4 * context.resources.displayMetrics.density
    var legendLineWidth = 1 * context.resources.displayMetrics.density
    var dashLength = 4 * context.resources.displayMetrics.density

    var barDrawableUnavailable =
        AppCompatResources.getDrawable(context, R.drawable.bar_graph_unavailable)!!
    var barDrawableAvailable =
        AppCompatResources.getDrawable(context, R.drawable.bar_graph_available)!!

    var data: Map<ZonedDateTime, Int>? = null
        set(value) {
            field = value
            invalidate()
        }
    var maxValue: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    var activeAlpha = 0.87f
    var inactiveAlpha = 0.60f

    private val legendPaint = Paint().apply {
        val ta = context.theme.obtainStyledAttributes(intArrayOf(R.attr.colorControlNormal))
        color = ta.getColor(0, 0)
        strokeWidth = legendLineWidth
        textSize = legendWidth - legendLineLength
    }
    private val legendDashedPaint = Paint().apply {
        set(legendPaint)
        alpha = (inactiveAlpha * 255).roundToInt()
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(dashLength, dashLength), 0f)
    }

    private lateinit var graphBounds: Rect

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val bottom = (paddingBottom + legendWidth).roundToInt()
        val left = (paddingLeft + legendWidth).roundToInt()
        val right = (paddingRight + legendWidth).roundToInt()
        val top = (paddingTop + (legendWidth - legendLineLength) / 3 * 2).roundToInt()
        graphBounds = Rect(left, top, w - right, h - bottom)
    }

    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    override fun onDraw(canvas: Canvas) {
        val data = data?.toSortedMap() ?: return
        val maxValue = maxValue ?: data.maxOf { it.value }

        canvas.apply {
            drawLine(
                graphBounds.left.toFloat(),
                graphBounds.top.toFloat(),
                graphBounds.right.toFloat(),
                graphBounds.top.toFloat(),
                legendDashedPaint
            )

            legendPaint.textAlign = Paint.Align.CENTER
            data.entries.forEachIndexed { i, (t, v) ->
                val drawable = if (v < maxValue) barDrawableAvailable else barDrawableUnavailable

                val height =
                    zeroHeight + (graphBounds.height() - zeroHeight) * v.toFloat() / maxValue
                val left = graphBounds.left + (barWidth + barMargin) * i

                if (left + barWidth > graphBounds.right) return@forEachIndexed

                drawable.setBounds(
                    left,
                    graphBounds.bottom - height.roundToInt(),
                    left + barWidth,
                    graphBounds.bottom
                )
                drawable.alpha = (inactiveAlpha * 255).roundToInt()
                drawable.draw(canvas)

                if (t.minute == 0) {
                    val center = left.toFloat() + barWidth / 2
                    drawLine(
                        center, graphBounds.bottom.toFloat(),
                        center, graphBounds.bottom + legendLineLength, legendPaint
                    )
                    drawText(
                        t.withZoneSameInstant(ZoneId.systemDefault()).format(timeFormat),
                        center, graphBounds.bottom + legendWidth, legendPaint
                    )
                }
            }

            drawLine(
                graphBounds.left.toFloat(),
                graphBounds.bottom.toFloat(),
                graphBounds.right.toFloat(),
                graphBounds.bottom.toFloat(),
                legendPaint
            )
            drawLine(
                graphBounds.left.toFloat(),
                graphBounds.bottom.toFloat(),
                graphBounds.right.toFloat(),
                graphBounds.bottom.toFloat(),
                legendPaint
            )

            legendPaint.textAlign = Paint.Align.LEFT
            drawText(
                maxValue.toString(),
                graphBounds.right.toFloat() + legendLineLength,
                graphBounds.top + (legendWidth - legendLineLength) / 3,
                legendPaint
            )
        }
    }
}