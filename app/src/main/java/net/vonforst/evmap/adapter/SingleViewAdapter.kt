package net.vonforst.evmap.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter

class SingleViewAdapter(val view: View) : Adapter<SingleViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(view)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount() = 1

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    }
}