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
import kotlin.math.roundToInt

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


class ChargerIconGenerator(
    val context: Context,
    val factory: BitmapDescriptorFactory?,
    val scaleResolution: Int = 20,
    val oversize: Float = 1f, // increase to add padding for scale > 1
    val height: Int = 48
) {
    private data class BitmapData(
        val tint: Int,
        val scale: Int,
        val alpha: Int,
        val highlight: Boolean,
        val fault: Boolean,
        val multi: Boolean,
        val fav: Boolean
    )

    // 230 items: (21 sizes, 5 colors, multi on/off) + highlight + fault (only with scale = 1)
    private val cacheSize = (scaleResolution + 3) * 5 * 2;
    private val cache = LruCache<BitmapData, BitmapDescriptor>(cacheSize)
    private val icon = R.drawable.ic_map_marker_charging
    private val multiIcon = R.drawable.ic_map_marker_charging_multiple
    private val highlightIcon = R.drawable.ic_map_marker_highlight
    private val highlightIconMulti = R.drawable.ic_map_marker_charging_highlight_multiple
    private val faultIcon = R.drawable.ic_map_marker_fault
    private val favIcon = R.drawable.ic_map_marker_fav

    fun preloadCache() {
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
                for (multi in listOf(false, true)) {
                    for (fav in listOf(false, true)) {
                        for (tint in tints) {
                            for (scale in 0..scaleResolution) {
                                getBitmapDescriptor(
                                    tint, scale.toFloat() / scaleResolution,
                                    255, highlight, fault, multi, fav
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun getBitmapDescriptor(
        @ColorRes tint: Int,
        scale: Float = 1f,
        alpha: Int = 255,
        highlight: Boolean = false,
        fault: Boolean = false,
        multi: Boolean = false,
        fav: Boolean = false
    ): BitmapDescriptor? {
        val data = BitmapData(
            tint, (scale * scaleResolution).roundToInt(),
            alpha,
            if (scale == 1f) highlight else false,
            if (scale == 1f) fault else false,
            multi,
            if (scale == 1f) fav else false
        )
        val cachedImg = cache[data]
        return if (cachedImg != null) {
            cachedImg
        } else {
            val bitmap = generateBitmap(data)
            val bmd = factory!!.fromBitmap(bitmap)
            cache.put(data, bmd)
            bmd
        }
    }

    fun getBitmap(
        @ColorRes tint: Int,
        scale: Float = 1f,
        alpha: Int = 255,
        highlight: Boolean = false,
        fault: Boolean = false,
        multi: Boolean = false,
        fav: Boolean = false
    ): Bitmap {
        val data = BitmapData(
            tint, (scale * scaleResolution).roundToInt(),
            alpha,
            if (scale == 1f) highlight else false,
            if (scale == 1f) fault else false,
            multi,
            if (scale == 1f) fav else false,
        )
        return generateBitmap(data)
    }

    private fun generateBitmap(data: BitmapData): Bitmap {
        val icon = if (data.multi) multiIcon else icon
        val vd: Drawable = ContextCompat.getDrawable(context, icon)!!

        DrawableCompat.setTint(vd, ContextCompat.getColor(context, data.tint));
        DrawableCompat.setTintMode(vd, PorterDuff.Mode.MULTIPLY);

        val density = context.resources.displayMetrics.density
        val markerWidth =
            (height.toFloat() * density / vd.intrinsicHeight * vd.intrinsicWidth).roundToInt()
        val markerHeight = (height * density).roundToInt()
        val extraIconSize = (0.75 * markerWidth).roundToInt()
        val extraIconShift = (0.25 * markerWidth).roundToInt()

        val totalWidth = markerWidth + 2 * extraIconShift
        val totalHeight = markerHeight + extraIconShift

        val leftPadding = ((totalWidth) * (oversize - 1) / 2).roundToInt() + extraIconShift
        val topPadding = ((totalHeight) * (oversize - 1)).roundToInt() + extraIconShift
        vd.setBounds(
            leftPadding, topPadding,
            leftPadding + markerWidth,
            topPadding + markerHeight
        )
        vd.alpha = data.alpha

        val bm = Bitmap.createBitmap(
            (totalWidth * oversize).roundToInt(), (totalHeight * oversize).roundToInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bm)

        val scale = data.scale.toFloat() / scaleResolution
        canvas.scale(
            scale,
            scale,
            canvas.width / 2f,
            canvas.height.toFloat()
        )

        vd.draw(canvas)

        if (data.highlight) {
            val hIcon = if (data.multi) highlightIconMulti else highlightIcon
            val highlightDrawable = ContextCompat.getDrawable(context, hIcon)!!
            highlightDrawable.setBounds(
                leftPadding, topPadding,
                leftPadding + markerWidth,
                topPadding + markerHeight
            )
            highlightDrawable.alpha = data.alpha
            highlightDrawable.draw(canvas)
        }

        if (data.fault) {
            val faultDrawable = ContextCompat.getDrawable(context, faultIcon)!!
            faultDrawable.setBounds(
                leftPadding + markerWidth + extraIconShift - extraIconSize,
                topPadding - extraIconShift,
                leftPadding + markerWidth + extraIconShift,
                topPadding + extraIconSize - extraIconShift
            )
            faultDrawable.alpha = data.alpha
            faultDrawable.draw(canvas)
        }

        if (data.fav) {
            val favDrawable = ContextCompat.getDrawable(context, favIcon)!!
            val favShiftY = extraIconShift
            val favShiftX = if (data.fault) extraIconShift - extraIconSize else extraIconShift
            favDrawable.setBounds(
                leftPadding + markerWidth - extraIconSize + favShiftX,
                topPadding - favShiftY,
                leftPadding + markerWidth + favShiftX,
                topPadding + extraIconSize - favShiftY
            )
            favDrawable.alpha = data.alpha
            favDrawable.draw(canvas)
        }

        return bm
    }
}