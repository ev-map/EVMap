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
import com.car2go.maps.OnMapReadyCallback
import com.car2go.maps.maplibre.MapView
import com.car2go.maps.maplibre.MapsConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.vonforst.evmap.R
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.roundToLong


class MapSurfaceCallback(val ctx: CarContext, val lifecycleScope: LifecycleCoroutineScope) :
    SurfaceCallback, OnMapReadyCallback {
    private val VIRTUAL_DISPLAY_NAME = "evmap_map"
    private val VELOCITY_THRESHOLD_IGNORE_FLING = 1000

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
                (surfaceContainer.dpi * 1.5).roundToInt(),
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

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = visibleArea
        updateVisibleArea()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
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
        map.moveCamera(map.cameraUpdateFactory.scrollBy(-distanceX, -distanceY))
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
                val diff = current - last
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
        if (scaleFactor == 2f) return

        val focus = Point(focusX.roundToInt(), focusY.roundToInt())
        // TODO: using focal point does not work correctly (at least not with mapbox)
        map.moveCamera(map.cameraUpdateFactory.zoomBy(scaleFactor - 1))
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

        val downEvent = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0
        )
        mapView.dispatchTouchEvent(downEvent)
        downEvent.recycle()
        val upEvent = MotionEvent.obtain(
            downTime,
            eventTime,
            MotionEvent.ACTION_UP,
            x,
            y,
            0
        )
        mapView.dispatchTouchEvent(upEvent)
        upEvent.recycle()
    }

    private fun createMap(ctx: Context): MapContainerView {
        MapsConfiguration.getInstance().initialize(ctx)
        return MapView(ctx)
    }

    override fun onMapReady(anyMap: AnyMap) {
        this.map = anyMap
        updateVisibleArea()
        mapCallbacks.forEach { it.onMapReady(anyMap) }
        mapCallbacks.clear()
    }

    private fun updateVisibleArea() {
        visibleArea?.let {
            map?.setPadding(0, it.top, width - it.right, height - it.bottom)
        }
    }

    fun getMapAsync(callback: OnMapReadyCallback) {
        mapCallbacks.add(callback)
    }
}