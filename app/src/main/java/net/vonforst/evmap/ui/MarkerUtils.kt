package net.vonforst.evmap.ui

import android.animation.ValueAnimator
import android.view.animation.BounceInterpolator
import androidx.core.animation.addListener
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.car2go.maps.model.Marker
import net.vonforst.evmap.R
import net.vonforst.evmap.model.ChargeLocation
import kotlin.math.max

fun getMarkerTint(
    charger: ChargeLocation,
    connectors: Set<String>? = null
): Int {
    val maxPower = charger.maxPower(connectors)
    return when {
        maxPower == null -> R.color.charger_low
        maxPower >= 100 -> R.color.charger_100kw
        maxPower >= 43 -> R.color.charger_43kw
        maxPower >= 20 -> R.color.charger_20kw
        maxPower >= 11 -> R.color.charger_11kw
        else -> R.color.charger_low
    }
}

class MarkerAnimator(val gen: ChargerIconGenerator) {
    private val animatingMarkers = hashMapOf<Marker, ValueAnimator>()

    fun animateMarkerAppear(
        marker: Marker,
        tint: Int,
        highlight: Boolean,
        fault: Boolean,
        multi: Boolean,
        fav: Boolean
    ) {
        animatingMarkers[marker]?.let {
            it.cancel()
            animatingMarkers.remove(marker)
        }

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            interpolator = LinearOutSlowInInterpolator()
            addUpdateListener { animationState ->
                val scale = animationState.animatedValue as Float
                marker.setIcon(
                    gen.getBitmapDescriptor(
                        tint,
                        scale = scale,
                        highlight = highlight,
                        fault = fault,
                        multi = multi,
                        fav = fav
                    )
                )
            }
            addListener(onEnd = {
                animatingMarkers.remove(marker)
            }, onCancel = {
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
        fault: Boolean,
        multi: Boolean,
        fav: Boolean
    ) {
        animatingMarkers[marker]?.let {
            it.cancel()
            animatingMarkers.remove(marker)
        }

        val anim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 200
            interpolator = FastOutLinearInInterpolator()
            addUpdateListener { animationState ->
                val scale = animationState.animatedValue as Float
                marker.setIcon(
                    gen.getBitmapDescriptor(
                        tint,
                        scale = scale,
                        highlight = highlight,
                        fault = fault,
                        multi = multi,
                        fav = fav
                    )
                )
            }
            addListener(onEnd = {
                marker.remove()
                animatingMarkers.remove(marker)
            }, onCancel = {
                marker.remove()
                animatingMarkers.remove(marker)
            })
        }
        animatingMarkers[marker] = anim
        anim.start()
    }

    fun deleteMarker(marker: Marker) {
        animatingMarkers[marker]?.let {
            it.cancel()
            animatingMarkers.remove(marker)
        }
        marker.remove()
    }

    fun animateMarkerBounce(marker: Marker) {
        animatingMarkers[marker]?.let {
            it.cancel()
            animatingMarkers.remove(marker)
        }

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = BounceInterpolator()
            addUpdateListener { state ->
                val t = max(1f - state.animatedValue as Float, 0f) / 2
                marker.setAnchor(0.5f, 1.0f + t)
            }
            addListener(onEnd = {
                animatingMarkers.remove(marker)
            }, onCancel = {
                animatingMarkers.remove(marker)
            })
        }
        animatingMarkers[marker] = anim
        anim.start()
    }
}