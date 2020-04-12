package com.johan.evmap.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.ViewGroup
import androidx.annotation.ColorRes
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
    data class BitmapData(val tint: Int, val scale: Int, val alpha: Int)

    val cacheSize = 4 * 1024 * 1024; // 4MiB
    val cache = object : LruCache<BitmapData, Bitmap>(cacheSize) {
        override fun sizeOf(key: BitmapData, value: Bitmap): Int {
            return value.byteCount
        }
    }
    val oversize = 1.5f
    val icon = R.drawable.ic_map_marker_charging

    init {
        preloadCache()
    }

    fun preloadCache() {
        // pre-generates images for scale from 0 to 255 for all possible tint colors
        val tints = listOf(
            R.color.charger_100kw,
            R.color.charger_43kw,
            R.color.charger_20kw,
            R.color.charger_11kw,
            R.color.charger_low
        )
        for (tint in tints) {
            for (scale in 0..20) {
                val data = BitmapData(tint, scale, 255)
                cache.put(data, generateBitmap(data))
            }
        }
    }

    fun getBitmapDescriptor(
        @ColorRes tint: Int,
        scale: Int = 20,
        alpha: Int = 255
    ): BitmapDescriptor? {
        val data = BitmapData(tint, scale, alpha)
        val cachedImg = cache[data]
        return if (cachedImg != null) {
            BitmapDescriptorFactory.fromBitmap(cachedImg)
        } else {
            val bitmap = generateBitmap(data)
            cache.put(data, bitmap)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    private fun generateBitmap(data: BitmapData): Bitmap {
        val vd: Drawable = context.getDrawable(icon)!!

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

        val scale = data.scale / 20f
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