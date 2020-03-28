package com.johan.evmap.ui

import android.content.Context
import android.view.ViewGroup
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
