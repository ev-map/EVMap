package net.vonforst.evmap.ui

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
import androidx.core.widget.TextViewCompat
import com.car2go.maps.BitmapDescriptorFactory
import com.car2go.maps.model.BitmapDescriptor
import com.google.maps.android.ui.IconGenerator
import com.google.maps.android.ui.SquareTextView
import net.vonforst.evmap.R

class ClusterIconGenerator(context: Context) : IconGenerator(context) {
    init {
        setBackground(context.getDrawable(R.drawable.marker_cluster_bg))
        setContentView(makeSquareTextView(context))
    }

    private fun makeSquareTextView(context: Context): SquareTextView? {
        val density = context.resources.displayMetrics.density
        val twelveDpi = (12.0f * density).toInt()

        return SquareTextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            id = R.id.amu_text
            setPadding(twelveDpi, twelveDpi, twelveDpi, twelveDpi)
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_AppCompat)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }
    }
}


class ChargerIconGenerator(val context: Context, val factory: BitmapDescriptorFactory) {
    data class BitmapData(
        val tint: Int,
        val scale: Int,
        val alpha: Int,
        val highlight: Boolean,
        val fault: Boolean
    )

    val cacheSize = 420; // 420 items: 21 sizes, 5 colors, highlight on/off, fault on/off
    val cache = LruCache<BitmapData, BitmapDescriptor>(cacheSize)
    val oversize = 1.4f  // increase to add padding for fault icon or scale > 1
    val icon = R.drawable.ic_map_marker_charging
    val highlightIcon = R.drawable.ic_map_marker_highlight
    val faultIcon = R.drawable.ic_map_marker_fault

    init {
        preloadCache()
    }

    private fun preloadCache() {
        // pre-generates images for scale from 0 to 255 for all possible tint colors
        val tints = listOf(
            R.color.charger_100kw,
            R.color.charger_43kw,
            R.color.charger_20kw,
            R.color.charger_11kw,
            R.color.charger_low
        )
        for (fault in listOf(false, true)) {
            for (highlight in listOf(false, true)) {
                for (tint in tints) {
                    for (scale in 0..20) {
                        getBitmapDescriptor(tint, scale, 255, highlight, fault)
                    }
                }
            }
        }
    }

    fun getBitmapDescriptor(
        @ColorRes tint: Int,
        scale: Int = 20,
        alpha: Int = 255,
        highlight: Boolean = false,
        fault: Boolean = false
    ): BitmapDescriptor? {
        val data = BitmapData(tint, scale, alpha, highlight, fault)
        val cachedImg = cache[data]
        return if (cachedImg != null) {
            cachedImg
        } else {
            val bitmap = generateBitmap(data)
            val bmd = factory.fromBitmap(bitmap)
            cache.put(data, bmd)
            bmd
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

        if (data.highlight) {
            val highlightDrawable = context.getDrawable(highlightIcon)!!
            highlightDrawable.setBounds(
                leftPadding.toInt(), topPadding.toInt(),
                leftPadding.toInt() + vd.intrinsicWidth,
                topPadding.toInt() + vd.intrinsicHeight
            )
            highlightDrawable.alpha = data.alpha
            highlightDrawable.draw(canvas)
        }

        if (data.fault) {
            val faultDrawable = context.getDrawable(faultIcon)!!
            val faultSize = 0.75
            val faultShift = 0.25
            val base = vd.intrinsicWidth
            faultDrawable.setBounds(
                (leftPadding.toInt() + base * (1 - faultSize + faultShift)).toInt(),
                (topPadding.toInt() - base * faultShift).toInt(),
                (leftPadding.toInt() + base * (1 + faultShift)).toInt(),
                (topPadding.toInt() + base * (faultSize - faultShift)).toInt()
            )
            faultDrawable.alpha = data.alpha
            faultDrawable.draw(canvas)
        }

        return bm
    }
}