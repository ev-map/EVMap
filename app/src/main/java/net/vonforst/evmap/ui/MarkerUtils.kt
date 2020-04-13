package net.vonforst.evmap.ui

import android.animation.ValueAnimator
import androidx.core.animation.addListener
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.google.android.gms.maps.model.Marker
import net.vonforst.evmap.R
import net.vonforst.evmap.api.goingelectric.ChargeLocation

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
    ValueAnimator.ofInt(0, 20).apply {
        duration = 250
        interpolator = LinearOutSlowInInterpolator()
        addUpdateListener { animationState ->
            if (!marker.isVisible) {
                cancel()
                return@addUpdateListener
            }
            val scale = animationState.animatedValue as Int
            marker.setIcon(
                gen.getBitmapDescriptor(tint, scale = scale)
            )
        }
    }.start()
}

fun animateMarkerDisappear(
    marker: Marker,
    tint: Int,
    gen: ChargerIconGenerator
) {
    ValueAnimator.ofInt(20, 0).apply {
        duration = 200
        interpolator = FastOutLinearInInterpolator()
        addUpdateListener { animationState ->
            if (!marker.isVisible) {
                cancel()
                return@addUpdateListener
            }
            val scale = animationState.animatedValue as Int
            marker.setIcon(
                gen.getBitmapDescriptor(tint, scale = scale)
            )
        }
        addListener(onEnd = {
            marker.remove()
        })
    }.start()
}