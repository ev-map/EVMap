package net.vonforst.evmap.ui

import android.annotation.SuppressLint
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.MenuPopupWindow
import androidx.appcompat.widget.PopupMenu

/**
 * Reflection workaround to make setTouchModal accessible for
 */
@SuppressLint("RestrictedApi")
fun PopupMenu.setTouchModal(modal: Boolean) {
    try {
        val mPopup = javaClass.getDeclaredField("mPopup").let { field ->
            field.isAccessible = true
            field.get(this)
        } as MenuPopupHelper
        val mPopup2 = mPopup.javaClass.getDeclaredMethod("getPopup").let { method ->
            method.isAccessible = true
            method.invoke(mPopup)
        }
        val mPopup3 = mPopup2.javaClass.getDeclaredField("mPopup").let { field ->
            field.isAccessible = true
            field.get(mPopup2)
        } as MenuPopupWindow
        mPopup3.setTouchModal(modal)
    } catch (e: NoSuchFieldException) {
        e.printStackTrace()
    } catch (e: NoSuchMethodException) {
        e.printStackTrace()
    }
}