package net.vonforst.evmap.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.load
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.ortiz.touchview.TouchImageView
import net.vonforst.evmap.R
import net.vonforst.evmap.api.goingelectric.ChargerPhoto


class GalleryAdapter(
    context: Context,
    val itemClickListener: ItemClickListener? = null,
    val detailView: Boolean = false,
    val pageToLoad: Int? = null,
    val imageCacheKey: MemoryCache.Key? = null,
    val loadedListener: (() -> Unit)? = null
) :
    ListAdapter<ChargerPhoto, GalleryAdapter.ViewHolder>(ChargerPhotoDiffCallback()) {
    class ViewHolder(val view: ImageView) : RecyclerView.ViewHolder(view)

    interface ItemClickListener {
        fun onItemClick(view: View, position: Int, imageCacheKey: MemoryCache.Key?)
    }

    val apikey = context.getString(R.string.goingelectric_key)
    var loaded = false
    val memoryKeys = HashMap<String, MemoryCache.Key?>()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view: ImageView
        if (detailView) {
            view = inflater.inflate(R.layout.gallery_item_fullscreen, parent, false) as ImageView
            view.setOnTouchListener { v, event ->
                var result = true
                //can scroll horizontally checks if there's still a part of the image
                //that can be scrolled until you reach the edge
                if (event.pointerCount >= 2 || v.canScrollHorizontally(1) && v.canScrollHorizontally(
                        -1
                    )
                ) {
                    //multi-touch event
                    result = when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            // Disallow RecyclerView to intercept touch events.
                            parent.requestDisallowInterceptTouchEvent(true)
                            // Disable touch on view
                            false
                        }
                        MotionEvent.ACTION_UP -> {
                            // Allow RecyclerView to intercept touch events.
                            parent.requestDisallowInterceptTouchEvent(false)
                            true
                        }
                        else -> true
                    }
                }
                result
            }
        } else {
            view = inflater.inflate(R.layout.gallery_item, parent, false) as ImageView
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (detailView) {
            (holder.view as TouchImageView).resetZoom()
        }
        val id = getItem(position).id
        val url = "https://api.goingelectric.de/chargepoints/photo/?key=${apikey}" +
                "&id=$id" +
                if (detailView) {
                    "&size=1000"
                } else {
                    "&height=${holder.view.height}"
                }

        holder.view.load(
            url,
            imageLoader = ImageLoader.Builder(holder.view.context).logger(DebugLogger()).build()
        ) {
            if (pageToLoad == position && imageCacheKey != null) {
                placeholderMemoryCacheKey(imageCacheKey)
            } else {
                placeholder(ColorDrawable(0))
            }
            allowHardware(false)
            listener(
                onSuccess = { _, metadata ->
                    memoryKeys[id] = metadata.memoryCacheKey
                    if (pageToLoad == position) invokeLoadedListener(holder.view)
                },
                onError = { _, _ ->
                    if (!loaded && loadedListener != null && pageToLoad == position) {
                        loadedListener.invoke()
                        loaded = true
                    }
                },
                onStart = {

                },
                onCancel = {
                }
            )
        }
        if (pageToLoad == position && imageCacheKey != null) {
            // start transition immediately
            if (pageToLoad == position) invokeLoadedListener(holder.view)
        }
        holder.view.transitionName = galleryTransitionName(position)
        if (itemClickListener != null) {
            holder.view.setOnClickListener {
                itemClickListener.onItemClick(holder.view, position, memoryKeys[id])
            }
        }
    }

    private fun invokeLoadedListener(
        view: ImageView
    ) {
        if (!loaded && loadedListener != null) {
            view.viewTreeObserver.addOnPreDrawListener(object :
                ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    view.viewTreeObserver.removeOnPreDrawListener(this)
                    loadedListener.invoke()
                    return true
                }
            })
            loaded = true
        }
    }
}

fun galleryTransitionName(position: Int) = "gallery_$position"

class ChargerPhotoDiffCallback : DiffUtil.ItemCallback<ChargerPhoto>() {
    override fun areItemsTheSame(oldItem: ChargerPhoto, newItem: ChargerPhoto): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChargerPhoto, newItem: ChargerPhoto): Boolean {
        return oldItem.id == newItem.id
    }

}
