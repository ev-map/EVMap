package net.vonforst.evmap.ui

import android.content.Context
import android.graphics.*
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import net.vonforst.evmap.R
import kotlin.math.max
import kotlin.math.min

class Gauge(val size: Int, ctx: Context) {
    val arcPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = size * 0.15f
    }
    val gaugePaint = Paint()
    val activeColor = ContextCompat.getColor(ctx, R.color.gauge_active)
    val middleColor = ContextCompat.getColor(ctx, R.color.gauge_middle)
    val inactiveColor = ContextCompat.getColor(ctx, R.color.gauge_inactive)

    fun draw(valuePercent: Float?, secondValuePercent: Float? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val angle = valuePercent?.let { 180f * it / 100 } ?: 0f
        val secondAngle = secondValuePercent?.let { 180f * it / 100 }

        drawArc(angle, secondAngle, canvas)
        if (secondAngle != null) drawGauge(secondAngle, inactiveColor, canvas)
        drawGauge(angle, Color.WHITE, canvas)
        return bitmap
    }

    private fun drawGauge(angle: Float, @ColorInt color: Int, canvas: Canvas) {
        gaugePaint.color = color
        canvas.save()
        canvas.rotate(angle - 90, size / 2f, 3 * size / 4f)
        canvas.drawCircle(size / 2f, 3 * size / 4f, size * 0.1F, gaugePaint)
        canvas.drawRect(size * 0.48f, 3 * size / 4f, size * 0.53f, size * 0.325f, gaugePaint)
        canvas.restore()
    }

    private fun drawArc(angle: Float, secondAngle: Float?, canvas: Canvas) {
        val (angle1, angle2) = if (secondAngle != null) {
            min(angle, secondAngle) to max(angle, secondAngle)
        } else {
            angle to null
        }

        arcPaint.color = activeColor
        val arcBounds = RectF(
            arcPaint.strokeWidth / 2,
            size / 4f + arcPaint.strokeWidth / 2,
            size - arcPaint.strokeWidth / 2,
            5 * size / 4f - arcPaint.strokeWidth / 2
        )

        canvas.drawArc(arcBounds, 180f, angle1, false, arcPaint)
        if (angle2 != null) {
            arcPaint.color = middleColor
            canvas.drawArc(arcBounds, 180f + angle1, angle2 - angle1, false, arcPaint)
        }
        arcPaint.color = inactiveColor
        canvas.drawArc(
            arcBounds,
            180f + (angle2 ?: angle1),
            180f - (angle2 ?: angle1),
            false,
            arcPaint
        )
    }
}