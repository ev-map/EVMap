package com.johan.evmap.ui

import android.animation.ValueAnimator
import androidx.core.animation.addListener
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.google.android.gms.maps.model.Marker
import com.johan.evmap.R
import com.johan.evmap.api.ChargeLocation

fun getMarkerTint(charger: ChargeLocation): Int = when {
    charger.maxPower >= 100 -> R.color.charger_100kw
    charger.maxPower >= 43 -> R.color.charger_43kw
    charger.maxPower >= 20 -> R.color.charger_20kw
    charger.maxPower >= 11 -> R.color.charger_11kw
    else -> R.color.charger_low
}

fun animateMarkerAppear(
    marker: Marker,
    tint: Int,
    gen: ChargerIconGenerator
) {
    ValueAnimator.ofInt(0, 255).apply {
        duration = 250
        interpolator = LinearOutSlowInInterpolator()
        addUpdateListener { animationState ->
            if (!marker.isVisible) {
                cancel()
                return@addUpdateListener
            }
            val scale = animationState.animatedValue as Int
            marker.setIcon(
                gen.getBitmapDescriptor(
                    R.drawable.ic_map_marker_charging,
                    tint,
                    scale = scale
                )
            )
        }
    }.start()
}

fun animateMarkerDisappear(
    marker: Marker,
    tint: Int,
    gen: ChargerIconGenerator
) {
    ValueAnimator.ofInt(255, 0).apply {
        duration = 200
        interpolator = FastOutLinearInInterpolator()
        addUpdateListener { animationState ->
            if (!marker.isVisible) {
                cancel()
                return@addUpdateListener
            }
            val scale = animationState.animatedValue as Int
            marker.setIcon(
                gen.getBitmapDescriptor(
                    R.drawable.ic_map_marker_charging,
                    tint,
                    scale = scale
                )
            )
        }
        addListener(onEnd = {
            marker.remove()
        })
    }.start()
}