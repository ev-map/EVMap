package net.vonforst.evmap.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.memory.MemoryCache
import net.vonforst.evmap.R
import net.vonforst.evmap.model.ChargerPhoto
import net.vonforst.evmap.waitForLayout


class GalleryAdapter(context: Context, val itemClickListener: ItemClickListener? = null) :
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
        val view = inflater.inflate(R.layout.gallery_item, parent, false) as ImageView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        if (holder.view.height == 0) {
            holder.view.waitForLayout {
                loadImage(item, holder)
            }
        } else {
            loadImage(item, holder)
        }

        if (itemClickListener != null) {
            holder.view.setOnClickListener {
                itemClickListener.onItemClick(holder.view, position, memoryKeys[item.id])
            }
        }
    }

    private fun loadImage(
        item: ChargerPhoto,
        holder: ViewHolder
    ) {
        val url = item.getUrl(height = holder.view.height)

        holder.view.load(
            url
        ) {
            listener(
                onSuccess = { _, metadata ->
                    memoryKeys[item.id] = metadata.memoryCacheKey
                }
            )
            allowHardware(false)
        }
    }
}

class ChargerPhotoDiffCallback : DiffUtil.ItemCallback<ChargerPhoto>() {
    override fun areItemsTheSame(oldItem: ChargerPhoto, newItem: ChargerPhoto): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChargerPhoto, newItem: ChargerPhoto): Boolean {
        return oldItem.id == newItem.id
    }

}
