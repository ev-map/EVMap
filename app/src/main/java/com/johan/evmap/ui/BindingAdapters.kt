package com.johan.evmap.ui

import android.R
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import androidx.databinding.BindingAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton


@BindingAdapter("goneUnless")
fun goneUnless(view: View, visible: Boolean) {
    view.visibility = if (visible) View.VISIBLE else View.GONE
}

@BindingAdapter("invisibleUnless")
fun invisibleUnless(view: View, visible: Boolean) {
    view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
}

@BindingAdapter("isFabActive")
fun isFabActive(view: FloatingActionButton, isColored: Boolean) {
    val color = TypedValue()
    if (isColored) {
        view.context.theme.resolveAttribute(R.attr.colorAccent, color, true)
    } else {
        view.context.theme.resolveAttribute(R.attr.colorControlNormal, color, true)
    }
    view.imageTintList = ColorStateList.valueOf(color.data)
}