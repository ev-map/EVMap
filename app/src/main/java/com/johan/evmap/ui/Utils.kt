package com.johan.evmap.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory


fun getBitmapDescriptor(@DrawableRes id: Int, @ColorRes tint: Int, context: Context): BitmapDescriptor? {
    val vd: Drawable = context.getDrawable(id)!!

    DrawableCompat.setTint(vd, ContextCompat.getColor(context, tint));
    DrawableCompat.setTintMode(vd, PorterDuff.Mode.MULTIPLY);

    vd.setBounds(0, 0, vd.intrinsicWidth, vd.intrinsicHeight)
    val bm = Bitmap.createBitmap(vd.intrinsicWidth, vd.intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bm)
    vd.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bm)
}