package net.vonforst.evmap.ui

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.MultiSelectListPreference
import net.vonforst.evmap.fragment.MultiSelectDialog

class MultiSelectDialogPreference(ctx: Context, attrs: AttributeSet) :
    MultiSelectListPreference(ctx, attrs) {
    override fun onClick() {
        val dialog =
            MultiSelectDialog.getInstance(
                title.toString(),
                entryValues.map { it.toString() }.zip(entries.map { it.toString() }).toMap(),
                if (all) entryValues.map { it.toString() }.toSet() else values,
                emptySet()
            )
        dialog.okListener = { selected ->
            all = selected == entryValues.toSet()
            values = selected
        }
        dialog.show((context as AppCompatActivity).supportFragmentManager, null)
    }

    var all: Boolean
        get() = sharedPreferences.getBoolean(key + "_all", true)
        set(value) {
            sharedPreferences.edit().putBoolean(key + "_all", value).apply()
        }
}