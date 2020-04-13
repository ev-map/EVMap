package net.vonforst.evmap.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.*
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ortiz.touchview.TouchImageView
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import net.vonforst.evmap.R
import net.vonforst.evmap.api.goingelectric.ChargerPhoto


class GalleryAdapter(
    context: Context,
    val itemClickListener: ItemClickListener? = null,
    val detailView: Boolean = false,
    val pageToLoad: Int? = null,
    val loadedListener: (() -> Unit)? = null
) :
    ListAdapter<ChargerPhoto, GalleryAdapter.ViewHolder>(ChargerPhotoDiffCallback()) {
    class ViewHolder(val view: ImageView) : RecyclerView.ViewHolder(view)

    interface ItemClickListener {
        fun onItemClick(view: View, position: Int)
    }

    val apikey = context.getString(R.string.goingelectric_key)
    var loaded = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view: ImageView
        if (detailView) {
            view = inflater.inflate(R.layout.gallery_item_fullscreen, parent, false) as ImageView
            view.setOnTouchListener { view, event ->
                var result = true
                //can scroll horizontally checks if there's still a part of the image
                //that can be scrolled until you reach the edge
                if (event.pointerCount >= 2 || view.canScrollHorizontally(1) && view.canScrollHorizontally(
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
        Picasso.get()
            .load(
                "https://api.goingelectric.de/chargepoints/photo/?key=${apikey}" +
                        "&id=${getItem(position).id}" +
                        if (detailView) {
                            "&size=1000"
                        } else {
                            "&height=${holder.view.height}"
                        }
            )
            .into(holder.view, object : Callback {
                override fun onSuccess() {
                    if (!loaded && loadedListener != null && pageToLoad == position) {
                        holder.view.viewTreeObserver.addOnPreDrawListener(object :
                            ViewTreeObserver.OnPreDrawListener {
                            override fun onPreDraw(): Boolean {
                                holder.view.viewTreeObserver.removeOnPreDrawListener(this)
                                loadedListener.invoke()
                                return true
                            }
                        })
                        loaded = true
                    }
                }

                override fun onError(e: Exception?) {
                    if (!loaded && loadedListener != null && pageToLoad == position) {
                        loadedListener.invoke()
                        loaded = true
                    }
                }

            })
        holder.view.transitionName = galleryTransitionName(position)
        if (itemClickListener != null) {
            holder.view.setOnClickListener {
                itemClickListener.onItemClick(holder.view, position)
            }
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
