package net.vonforst.evmap.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.core.content.ContextCompat
import net.vonforst.evmap.R

class CompassNeedle(val size: Int, ctx: Context) {
    val image = ContextCompat.getDrawable(ctx, R.drawable.ic_navigation)!!

    init {
        image.setTint(Color.WHITE)
        image.setBounds(0, 0, size, size)
    }

    fun draw(angle: Float?): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        if (angle != null) {
            canvas.save()
            canvas.rotate(-angle, size / 2f, size / 2f)
            image.draw(canvas)
            canvas.restore()
        }
        return bitmap
    }
}