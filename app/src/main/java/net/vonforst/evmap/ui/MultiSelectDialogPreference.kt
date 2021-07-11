package net.vonforst.evmap.ui

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.MultiSelectListPreference
import net.vonforst.evmap.R
import net.vonforst.evmap.fragment.MultiSelectDialog

class MultiSelectDialogPreference(ctx: Context, attrs: AttributeSet) :
    MultiSelectListPreference(ctx, attrs) {
    val showAllButton: Boolean
    val defaultToAll: Boolean

    init {
        val a = ctx.obtainStyledAttributes(attrs, R.styleable.MultiSelectDialogPreference)
        showAllButton = a.getBoolean(R.styleable.MultiSelectDialogPreference_showAllButton, true)
        defaultToAll = a.getBoolean(R.styleable.MultiSelectDialogPreference_defaultToAll, true)
        a.recycle()
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        try {
            super.onSetInitialValue(defaultValue)
        } catch (e: ClassCastException) {
            // backwards compatibility when changing a ListPreference into a MultiSelectListPreference
            val value =
                getPersistedString(null)?.let { setOf(it) } ?: (defaultValue as Set<String>?)
            sharedPreferences.edit()
                .remove(key)
                .putStringSet(key, value)
                .apply()
            super.onSetInitialValue(defaultValue)
        }
    }

    override fun onClick() {
        val dialog =
            MultiSelectDialog.getInstance(
                title.toString(),
                entryValues.map { it.toString() }.zip(entries.map { it.toString() }).toMap(),
                if (all) entryValues.map { it.toString() }.toSet() else values,
                emptySet(),
                showAllButton
            )
        dialog.okListener = { selected ->
            all = selected == entryValues.toSet()
            values = selected
        }
        dialog.show((context as AppCompatActivity).supportFragmentManager, null)
    }

    var all: Boolean
        get() = sharedPreferences.getBoolean(key + "_all", defaultToAll)
        set(value) {
            sharedPreferences.edit().putBoolean(key + "_all", value).apply()
        }
}