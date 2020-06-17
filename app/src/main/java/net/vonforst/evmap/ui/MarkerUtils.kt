package net.vonforst.evmap.ui

import android.animation.ValueAnimator
import android.view.animation.BounceInterpolator
import androidx.core.animation.addListener
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.google.android.libraries.maps.model.Marker
import net.vonforst.evmap.R
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import kotlin.math.max

fun getMarkerTint(charger: ChargeLocation): Int = when {
    charger.maxPower >= 100 -> R.color.charger_100kw
    charger.maxPower >= 43 -> R.color.charger_43kw
    charger.maxPower >= 20 -> R.color.charger_20kw
    charger.maxPower >= 11 -> R.color.charger_11kw
    else -> R.color.charger_low
}

class MarkerAnimator(val gen: ChargerIconGenerator) {
    val animatingMarkers = hashMapOf<Marker, ValueAnimator>()

    fun animateMarkerAppear(
        marker: Marker,
        tint: Int,
        highlight: Boolean,
        fault: Boolean
    ) {
        animatingMarkers[marker]?.cancel()
        animatingMarkers.remove(marker)

        val anim = ValueAnimator.ofInt(0, 20).apply {
            duration = 250
            interpolator = LinearOutSlowInInterpolator()
            addUpdateListener { animationState ->
                if (!marker.isVisible) {
                    cancel()
                    animatingMarkers.remove(marker)
                    return@addUpdateListener
                }
                val scale = animationState.animatedValue as Int
                marker.setIcon(
                    gen.getBitmapDescriptor(
                        tint,
                        scale = scale,
                        highlight = highlight,
                        fault = fault
                    )
                )
            }
            addListener(onEnd = {
                animatingMarkers.remove(marker)
            })
        }
        animatingMarkers[marker] = anim
        anim.start()
    }

    fun animateMarkerDisappear(
        marker: Marker,
        tint: Int,
        highlight: Boolean,
        fault: Boolean
    ) {
        animatingMarkers[marker]?.cancel()
        animatingMarkers.remove(marker)

        val anim = ValueAnimator.ofInt(20, 0).apply {
            duration = 200
            interpolator = FastOutLinearInInterpolator()
            addUpdateListener { animationState ->
                if (!marker.isVisible) {
                    cancel()
                    animatingMarkers.remove(marker)
                    return@addUpdateListener
                }
                val scale = animationState.animatedValue as Int
                marker.setIcon(
                    gen.getBitmapDescriptor(
                        tint,
                        scale = scale,
                        highlight = highlight,
                        fault = fault
                    )
                )
            }
            addListener(onEnd = {
                animatingMarkers.remove(marker)
                marker.remove()
            })
        }
        animatingMarkers[marker] = anim
        anim.start()
    }

    fun animateMarkerBounce(marker: Marker) {
        animatingMarkers[marker]?.cancel()
        animatingMarkers.remove(marker)

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = BounceInterpolator()
            addUpdateListener { state ->
                if (!marker.isVisible) {
                    cancel()
                    animatingMarkers.remove(marker)
                    return@addUpdateListener
                }
                val t = max(1f - state.animatedValue as Float, 0f) / 2
                marker.setAnchor(0.5f, 1.0f + t)
            }
        }
        animatingMarkers[marker] = anim
        anim.start()
    }
}