package net.vonforst.evmap.ui

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import net.vonforst.evmap.R
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class BarGraphView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val dp = context.resources.displayMetrics.density
    var zeroHeight = 4 * dp
    var barWidth = 16 * dp
    var barMargin = 2 * dp
    var legendWidth = 12 * dp
    var legendLineLength = 4 * dp
    var legendLineWidth = 1 * dp
    var dashLength = 4 * dp
    var bubbleTextSize =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics)
            .roundToInt()
    var bubblePadding = (6 * dp).roundToInt()
    var selectedBar: Int = 0
    var bubbleStrokeWidth = 1 * dp

    var barDrawable =
        AppCompatResources.getDrawable(context, R.drawable.bar_graph)!!
    var colorAvailable = ContextCompat.getColor(context, R.color.available)
    var colorSomeAvailable = ContextCompat.getColor(context, R.color.some_available)
    var colorUnavailable = ContextCompat.getColor(context, R.color.unavailable)

    var data: Map<ZonedDateTime, Double>? = null
        set(value) {
            field = value
            invalidate()
        }
    var maxValue: Double? = null
        set(value) {
            field = value
            invalidate()
        }
    var isPercentage: Boolean = false

    var activeAlpha = 0.87f
    var inactiveAlpha = 0.60f

    private val legendPaint = Paint().apply {
        val ta =
            context.theme.obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.colorControlNormal))
        color = ta.getColor(0, 0)
        strokeWidth = legendLineWidth
        textSize = legendWidth - legendLineLength
    }
    private val legendDashedPaint = Paint().apply {
        set(legendPaint)
        alpha = (inactiveAlpha * 255).roundToInt()
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(dashLength, dashLength), 0f)
        strokeWidth = 1f
    }
    private val bubblePaint = Paint().apply {
        set(legendPaint)
        alpha = (inactiveAlpha * 255).roundToInt()
        style = Paint.Style.STROKE
        strokeWidth = bubbleStrokeWidth
    }
    private val bubbleTextPaint = TextPaint().apply {
        set(legendPaint)
        textSize = bubbleTextSize.toFloat()
    }

    private var graphBounds: Rect? = null
    private var bubbleBounds: Rect? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val bottom = (paddingBottom + legendWidth).roundToInt()
        val left = (paddingLeft + legendWidth).roundToInt()
        val right = (paddingRight + legendWidth).roundToInt()
        val top = (paddingTop + bubbleStrokeWidth / 2).roundToInt()

        val bubbleTextHeight = bubbleTextPaint.fontMetrics.run { descent - ascent }
        val bubbleHeight = (bubbleTextHeight + 3 * bubblePadding).roundToInt()
        val bubbleLeft = (paddingLeft + bubbleStrokeWidth / 2).roundToInt()
        val bubbleRight = (paddingRight + bubbleStrokeWidth / 2).roundToInt()

        graphBounds = Rect(left, top + bubbleHeight, w - right, h - bottom)
        bubbleBounds = Rect(bubbleLeft, top, w - bubbleRight, top + bubbleHeight)
    }

    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    override fun onDraw(canvas: Canvas) {
        if (isInEditMode && data == null) {
            // show sample data
            val now = ZonedDateTime.now().run {
                val minutesRound = ((minute / 15) + 1) * 15
                plusMinutes((minutesRound - minute).toLong())
            }
            data = (0..20).associate {
                now.plusMinutes(15L * it) to (Math.random() * 8)
            }
            maxValue = 8.0
        }
        val data = data?.toSortedMap() ?: return
        if (data.isEmpty()) return
        val maxValue = maxValue ?: data.maxOf { it.value }

        val graphWidth = graphBounds?.width() ?: return
        val n = data.size
        val barMarginFactor = 0.1f
        barWidth = graphWidth / (n + barMarginFactor * (n - 1))
        barMargin = barWidth * barMarginFactor

        drawGraph(canvas, data, maxValue)
        drawBubble(canvas, data, maxValue)
    }

    private fun drawGraph(
        canvas: Canvas,
        data: SortedMap<ZonedDateTime, Double>,
        maxValue: Double
    ) {
        val graphBounds = graphBounds ?: return

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
                val divisor = maxValue.toFloat().takeIf { it > 0f } ?: 1f
                val height =
                    zeroHeight + (graphBounds.height() - zeroHeight) * v.toFloat() / divisor
                val left = graphBounds.left + (barWidth + barMargin) * i

                if (left + barWidth > graphBounds.right) return@forEachIndexed

                barDrawable.setBounds(
                    0,
                    graphBounds.bottom - height.roundToInt(),
                    barWidth.roundToInt(),
                    graphBounds.bottom
                )

                canvas.translate(left, 0f)
                barDrawable.alpha =
                    ((if (i == selectedBar) activeAlpha else inactiveAlpha) * 255).roundToInt()
                barDrawable.setTint(getColor(v, maxValue))
                barDrawable.draw(canvas)
                canvas.translate(-left, 0f)

                val center = left + barWidth / 2
                if (shouldDrawLabel(t, data)) {
                    drawLine(
                        center, graphBounds.bottom.toFloat(),
                        center, graphBounds.bottom + legendLineLength, legendPaint
                    )
                    drawText(
                        t.withZoneSameInstant(ZoneId.systemDefault()).format(timeFormat),
                        center, graphBounds.bottom + legendWidth, legendPaint
                    )
                }

                if (i == selectedBar) {
                    drawLine(
                        center,
                        graphBounds.bottom - height,
                        center,
                        graphBounds.top.toFloat(),
                        legendDashedPaint
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
            if (!isPercentage) {
                drawText(
                    maxValue.roundToInt().toString(),
                    graphBounds.right.toFloat() + legendLineLength,
                    graphBounds.top + (legendWidth - legendLineLength) / 3,
                    legendPaint
                )
            }
        }
    }

    private fun shouldDrawLabel(t: ZonedDateTime, data: SortedMap<ZonedDateTime, Double>): Boolean {
        val ts = data.keys.toList()
        return if (Duration.between(ts[0], ts[1]) > Duration.ofMinutes(31)) {
            // label every 6 hours
            t.hour % 6 == 0
        } else {
            // label every 15 minutes
            t.minute == 0
        }
    }

    private fun getColor(v: Double, maxValue: Double) =
        if (isPercentage) {
            when (v) {
                in 0.0..0.5 -> colorAvailable
                in 0.5..0.8 -> colorSomeAvailable
                else -> colorUnavailable
            }
        } else {
            if (v < maxValue) colorAvailable else colorUnavailable
        }

    private fun drawBubble(
        canvas: Canvas,
        data: SortedMap<ZonedDateTime, Double>,
        maxValue: Double
    ) {
        val bubbleBounds = bubbleBounds ?: return
        val graphBounds = graphBounds ?: return
        val d = data.toList()

        if (d.size <= selectedBar) return
        canvas.apply {
            val center = graphBounds.left + selectedBar * (barWidth + barMargin) + barWidth * 0.5f
            val (t, v) = d[selectedBar]
            val tformat = context.getString(
                R.string.prediction_time_colon,
                t.withZoneSameInstant(ZoneId.systemDefault()).format(timeFormat)
            )
            val availableformat = if (isPercentage) {
                "%.0f %%".format(v * 100)
            } else {
                context.resources.getQuantityString(
                    R.plurals.prediction_number_available,
                    (maxValue - v).roundToInt(),
                    (maxValue - v).roundToInt(),
                    maxValue.roundToInt()
                )
            }
            val text = SpannableString("$tformat $availableformat").apply {
                setSpan(
                    ForegroundColorSpan(getColor(v, maxValue)),
                    0,
                    tformat.length + 1,
                    SpannableString.SPAN_INCLUSIVE_INCLUSIVE
                )
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    tformat.length + 1,
                    SpannableString.SPAN_INCLUSIVE_INCLUSIVE
                )
            }

            val bubbleTextWidth = StaticLayout.getDesiredWidth(text, bubbleTextPaint)
            val bubbleWidth = bubbleTextWidth + 2 * bubblePadding
            val bubbleLeft = max(
                min(center - bubbleWidth / 2, bubbleBounds.right - bubbleWidth),
                bubbleBounds.left.toFloat()
            )

            val bubblePath = generateBubblePath(
                center,
                bubbleBounds.bottom.toFloat(),
                bubbleLeft,
                bubbleBounds.top.toFloat(),
                bubbleLeft + bubbleWidth,
                (bubbleBounds.bottom - bubblePadding).toFloat(),
                bubblePadding.toFloat()
            )
            drawPath(bubblePath, bubblePaint)

            val layout = StaticLayout(
                text,
                bubbleTextPaint,
                ceil(bubbleTextWidth).toInt(),
                Layout.Alignment.ALIGN_NORMAL,
                1f,
                0f,
                false
            )
            canvas.save()
            canvas.translate(bubbleLeft + bubblePadding, bubbleBounds.top + bubblePadding.toFloat())
            layout.draw(canvas)
            canvas.restore()
            //drawText(text, 0, text.length, bubbleLeft + bubblePadding, bubbleBounds.top + 2f * bubblePadding, bubbleTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val graphBounds = graphBounds ?: return super.onTouchEvent(event)
        val x = event.x.roundToInt()
        val y = event.y.roundToInt()
        if (graphBounds.contains(x, y) && event.action == MotionEvent.ACTION_DOWN) {
            parent.requestDisallowInterceptTouchEvent(true)
            updateSelectedBar(x)
            return true
        } else if (event.action == MotionEvent.ACTION_MOVE && x > graphBounds.left && y < graphBounds.right) {
            updateSelectedBar(x)
            return true
        } else if (event.action == MotionEvent.ACTION_UP) {
            parent.requestDisallowInterceptTouchEvent(false)
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun updateSelectedBar(x: Int) {
        val graphBounds = graphBounds ?: return
        val bar = ((x - graphBounds.left) / (barWidth + barMargin)).roundToInt()
        if (bar != selectedBar) {
            selectedBar = bar
            invalidate()
        }
    }

    /**
     * Generates a path that represents a "speech bubble" with tip position at tipX, tipY,
     * bubble bounds left, top, right bottom and corner radius cornerRadius.
     */
    private fun generateBubblePath(
        tipX: Float,
        tipY: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        cornerRadius: Float
    ): Path {
        val tipWidth = tipY - bottom
        return Path().apply {
            moveTo(tipX, tipY)
            lineTo(min(tipX + tipWidth, right - cornerRadius), bottom)
            lineTo(right - cornerRadius, bottom)
            arcTo(
                right - cornerRadius * 2,
                bottom - cornerRadius * 2,
                right,
                bottom,
                90f,
                -90f,
                false
            )
            lineTo(right, top + cornerRadius)
            arcTo(right - cornerRadius * 2, top, right, top + cornerRadius * 2, 0f, -90f, false)
            lineTo(left + cornerRadius, top)
            arcTo(left, top, left + cornerRadius * 2, top + cornerRadius * 2, 270f, -90f, false)
            lineTo(left, bottom - cornerRadius)
            arcTo(
                left,
                bottom - cornerRadius * 2,
                left + cornerRadius * 2,
                bottom,
                180f,
                -90f,
                false
            )
            lineTo(max(tipX - tipWidth, left + cornerRadius), bottom)
            close()
        }
    }
}