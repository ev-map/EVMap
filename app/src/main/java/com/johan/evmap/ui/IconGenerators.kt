package com.johan.evmap.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.ui.IconGenerator
import com.google.maps.android.ui.SquareTextView
import com.johan.evmap.R

class ClusterIconGenerator(context: Context) : IconGenerator(context) {
    init {
        setBackground(context.getDrawable(R.drawable.marker_cluster_bg))
        setContentView(makeSquareTextView(context))
        setTextAppearance(R.style.TextAppearance_AppCompat_Inverse)
    }

    private fun makeSquareTextView(context: Context): SquareTextView? {
        val density = context.resources.displayMetrics.density
        val twelveDpi = (12.0f * density).toInt()

        return SquareTextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            id = com.google.maps.android.R.id.amu_text
            setPadding(twelveDpi, twelveDpi, twelveDpi, twelveDpi)
        }
    }
}


class ChargerIconGenerator(val context: Context) {
    data class BitmapData(val id: Int, val tint: Int, val scale: Int, val alpha: Int)

    val cache = hashMapOf<BitmapData, Bitmap>()
    val oversize = 1.5f

    fun getBitmapDescriptor(
        @DrawableRes id: Int,
        @ColorRes tint: Int,
        scale: Int = 255,
        alpha: Int = 255
    ): BitmapDescriptor? {
        val data = BitmapData(id, tint, scale, alpha)
        if (cache.containsKey(data)) {
            return BitmapDescriptorFactory.fromBitmap(cache[data])
        } else {
            val bitmap = generateBitmap(data)
            cache[data] = bitmap
            return BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    private fun generateBitmap(data: BitmapData): Bitmap {
        val vd: Drawable = context.getDrawable(data.id)!!

        DrawableCompat.setTint(vd, ContextCompat.getColor(context, data.tint));
        DrawableCompat.setTintMode(vd, PorterDuff.Mode.MULTIPLY);

        val leftPadding = vd.intrinsicWidth * (oversize - 1) / 2
        val topPadding = vd.intrinsicWidth * (oversize - 1)
        vd.setBounds(
            leftPadding.toInt(), topPadding.toInt(),
            leftPadding.toInt() + vd.intrinsicWidth,
            topPadding.toInt() + vd.intrinsicHeight
        )
        vd.alpha = data.alpha

        val bm = Bitmap.createBitmap(
            (vd.intrinsicWidth * oversize).toInt(), (vd.intrinsicHeight * oversize).toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bm)

        val scale = data.scale / 255f
        canvas.scale(
            scale,
            scale,
            leftPadding + vd.intrinsicWidth / 2f,
            topPadding + vd.intrinsicHeight.toFloat()
        )

        vd.draw(canvas)
        return bm
    }
}