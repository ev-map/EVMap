package net.vonforst.evmap.auto

import android.animation.ValueAnimator
import android.app.Presentation
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.annotations.RequiresCarApi
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.lifecycle.LifecycleCoroutineScope
import com.car2go.maps.AnyMap
import com.car2go.maps.AnyMap.CancelableCallback
import com.car2go.maps.CameraUpdate
import com.car2go.maps.MapContainerView
import com.car2go.maps.MapFactory
import com.car2go.maps.OnMapReadyCallback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.R
import net.vonforst.evmap.storage.PreferenceDataSource
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.roundToLong


class MapSurfaceCallback(val ctx: CarContext, val lifecycleScope: LifecycleCoroutineScope) :
    SurfaceCallback, OnMapReadyCallback {
    private val VIRTUAL_DISPLAY_NAME = "evmap_map"
    private val VELOCITY_THRESHOLD_IGNORE_FLING = 1000

    private val prefs = PreferenceDataSource(ctx)

    private lateinit var virtualDisplay: VirtualDisplay
    lateinit var presentation: Presentation
    private lateinit var mapView: MapContainerView
    private var width: Int = 0
    private var height: Int = 0
    private var visibleArea: Rect? = null
    private var map: AnyMap? = null
    private val mapCallbacks = mutableListOf<OnMapReadyCallback>()

    private var flingAnimator: ValueAnimator? = null
    private var idle = true
    private var idleDelay: Job? = null
    var cameraMoveStartedListener: (() -> Unit)? = null
    var cameraIdleListener: (() -> Unit)? = null


    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        if (surfaceContainer.surface == null || surfaceContainer.dpi == 0 || surfaceContainer.height == 0 || surfaceContainer.width == 0) {
            return
        }

        if (Build.FINGERPRINT.contains("emulator") || Build.FINGERPRINT.contains("sdk_gcar")) {
            // fix for MapLibre in Android Automotive Emulators
            System.setProperty("ro.kernel.qemu", "1")
        }

        width = surfaceContainer.width
        height = surfaceContainer.height
        virtualDisplay = ContextCompat
            .getSystemService(ctx, DisplayManager::class.java)!!
            .createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                width,
                height,
                (surfaceContainer.dpi * when (getMapProvider()) {
                    "mapbox" -> 1.6
                    "google" -> 1.0
                    else -> 1.0
                }).roundToInt(),
                surfaceContainer.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            )
        presentation = Presentation(ctx, virtualDisplay.display, R.style.AppTheme)

        mapView = createMap(presentation.context)
        mapView.onCreate(null)
        mapView.onResume()

        presentation.setContentView(mapView)
        presentation.show()

        mapView.getMapAsync(this)
    }

    private fun getMapProvider(): String = if (BuildConfig.FLAVOR_automotive == "automotive") {
        // Google Maps SDK is not available on AAOS (not even AAOS with GAS, so far)
        "mapbox"
    } else prefs.mapProvider

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        Log.d("MapSurfaceCallback", "visible area: $visibleArea")
        this.visibleArea = visibleArea
        updateVisibleArea()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        Log.d("MapSurfaceCallback", "stable area: $stableArea")
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        mapView.onPause()
        mapView.onStop()
        mapView.onDestroy()
        map = null

        presentation.dismiss()
        virtualDisplay.release()
    }

    @RequiresCarApi(2)
    override fun onScroll(distanceX: Float, distanceY: Float) {
        flingAnimator?.cancel()
        val map = map ?: return
        map.moveCamera(map.cameraUpdateFactory.scrollBy(distanceX, distanceY))
        dispatchCameraMoveStarted()
    }

    @RequiresCarApi(2)
    override fun onFling(velocityX: Float, velocityY: Float) {
        val map = map ?: return
        val screenDensity: Float = presentation.resources.displayMetrics.density

        // calculate velocity vector for xy dimensions, independent from screen size
        val velocityXY =
            hypot((velocityX / screenDensity).toDouble(), (velocityY / screenDensity).toDouble())
        if (velocityXY < VELOCITY_THRESHOLD_IGNORE_FLING) {
            // ignore short flings, these can occur when other gestures just have finished executing
            return
        }

        idleDelay?.cancel()

        val offsetX = velocityX / 10
        val offsetY = velocityY / 10
        val animationTime = (velocityXY / 10).roundToLong()

        flingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationTime
            interpolator = LinearOutSlowInInterpolator()

            var last = 0f
            addUpdateListener {
                val current = it.animatedFraction
                val diff = last - current
                map.moveCamera(map.cameraUpdateFactory.scrollBy(diff * offsetX, diff * offsetY))
                last = current
            }
            start()

            doOnEnd { dispatchCameraIdle() }
        }
    }

    @RequiresCarApi(2)
    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        flingAnimator?.cancel()
        val map = map ?: return

        val offsetX = (focusX - mapView.width / 2) * (scaleFactor - 1f)
        val offsetY = (offsetY(focusY) - mapView.height / 2) * (scaleFactor - 1f)

        Log.i("MapSurfaceCallback", "focus: $focusX, $focusY, scaleFactor: $scaleFactor")
        if (scaleFactor == 2f) {
            map.animateCamera(
                map.cameraUpdateFactory.zoomBy(
                    scaleFactor - 1,
                    Point(focusX.roundToInt(), focusY.roundToInt())
                )
            )
        } else {
            map.moveCamera(map.cameraUpdateFactory.zoomBy(scaleFactor - 1))
            map.moveCamera(map.cameraUpdateFactory.scrollBy(offsetX, offsetY))
        }
        dispatchCameraMoveStarted()
    }

    fun animateCamera(update: CameraUpdate) {
        val map = map ?: return
        map.animateCamera(update, object : CancelableCallback {
            override fun onFinish() {
                dispatchCameraIdle()
            }

            override fun onCancel() {
            }
        })
    }

    private fun dispatchCameraMoveStarted() {
        if (idle) {
            idle = false
            cameraMoveStartedListener?.invoke()
        }
        idleDelay?.cancel()
        idleDelay = lifecycleScope.launch {
            delay(500)
            dispatchCameraIdle()
        }
    }

    private fun dispatchCameraIdle() {
        idle = true
        cameraIdleListener?.invoke()
    }

    @RequiresCarApi(5)
    override fun onClick(x: Float, y: Float) {
        flingAnimator?.cancel()
        val downTime: Long = SystemClock.uptimeMillis()
        val eventTime: Long = downTime + 100
        val yOffset = offsetY(y)

        val downEvent = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            yOffset,
            0
        )
        mapView.dispatchTouchEvent(downEvent)
        downEvent.recycle()
        val upEvent = MotionEvent.obtain(
            downTime,
            eventTime,
            MotionEvent.ACTION_UP,
            x,
            yOffset,
            0
        )
        mapView.dispatchTouchEvent(upEvent)
        upEvent.recycle()
    }

    private fun offsetY(y: Float): Float {
        if (BuildConfig.FLAVOR_automotive != "automotive") {
            return y
        }

        // On AAOS, touch locations seem to be offset by the status bar height
        // related: https://issuetracker.google.com/issues/256905247
        val resId = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        val offset = resId.takeIf { it > 0 }?.let { ctx.resources.getDimensionPixelSize(it) } ?: 0
        return y + offset
    }

    private fun createMap(ctx: Context): MapContainerView {
        val priority = arrayOf(
            when (getMapProvider()) {
                "mapbox" -> MapFactory.MAPLIBRE
                "google" -> MapFactory.GOOGLE
                else -> null
            },
            MapFactory.GOOGLE,
            MapFactory.MAPLIBRE
        )
        return MapFactory.createMap(ctx, priority).view
    }

    override fun onMapReady(anyMap: AnyMap) {
        this.map = anyMap
        updateVisibleArea()
        mapCallbacks.forEach { it.onMapReady(anyMap) }
        mapCallbacks.clear()
    }

    private fun updateVisibleArea() {
        visibleArea?.let {
            map?.setPadding(it.left, it.top, width - it.right, height - it.bottom)
        }
    }

    fun getMapAsync(callback: OnMapReadyCallback) {
        mapCallbacks.add(callback)
    }
}