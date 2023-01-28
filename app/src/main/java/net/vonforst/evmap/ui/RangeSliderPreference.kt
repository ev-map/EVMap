package net.vonforst.evmap.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.RangeSlider
import net.vonforst.evmap.R

class RangeSliderPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    var valueFrom: Float = 0f
        set(value) {
            val v = if (value > valueTo) valueTo else value
            if (v != valueFrom) {
                field = v
                notifyChanged()
            }
        }
    var valueTo: Float = 100f
        set(value) {
            val v = if (value < valueFrom) valueFrom else value
            if (v != valueTo) {
                field = v
                notifyChanged()
            }
        }
    var stepSize: Float? = null
        set(value) {
            if (value != stepSize) {
                field = value
                notifyChanged()
            }
        }
    var updatesContinuously: Boolean
    var defaultValue: List<Float>
    var labelFormatter: ((Float) -> String)? = null
        set(value) {
            if (value != labelFormatter) {
                field = value
                notifyChanged()
            }
        }

    private lateinit var slider: RangeSlider
    private var dragging = false

    var values: List<Float>
        get() = if ((sharedPreferences!!.contains(key + "_min") && sharedPreferences!!.contains(key + "_max"))) {
            listOf(
                sharedPreferences!!.getFloat(key + "_min", 0f),
                sharedPreferences!!.getFloat(key + "_max", 0f)
            )
        } else defaultValue
        set(value) {
            sharedPreferences!!.edit()
                .putFloat(key + "_min", value[0])
                .putFloat(key + "_max", value[1])
                .apply()
        }

    init {
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.RangeSliderPreference
        )

        // The ordering of these two statements are important. If we want to set max first, we need
        // to perform the same steps by changing min/max to max/min as following:
        // mMax = a.getInt(...) and setMin(...).
        valueFrom = a.getFloat(R.styleable.RangeSliderPreference_android_valueFrom, 0f)
        valueTo = a.getFloat(R.styleable.RangeSliderPreference_android_valueTo, 100f)
        stepSize =
            a.getFloat(R.styleable.RangeSliderPreference_android_stepSize, -1f).takeIf { it != -1f }
        updatesContinuously = a.getBoolean(
            R.styleable.RangeSliderPreference_updatesContinuously,
            false
        )
        defaultValue =
            a.getString(R.styleable.RangeSliderPreference_android_defaultValue)?.split(",")
                ?.map { it.toFloat() } ?: listOf(valueFrom, valueTo)

        a.recycle()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        slider = holder.findViewById(R.id.rangeSlider) as RangeSlider
        slider.valueFrom = valueFrom
        slider.valueTo = valueTo
        stepSize?.let { slider.stepSize = it }

        slider.addOnChangeListener { slider, _, fromUser ->
            if (fromUser && (updatesContinuously || !dragging)) {
                syncValueInternal(slider)
            }
        }
        slider.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> dragging = true
                MotionEvent.ACTION_UP -> dragging = false
            }
            false
        }
        slider.values = values
        slider.isEnabled = isEnabled
        slider.setLabelFormatter(labelFormatter)
    }

    private fun syncValueInternal(slider: RangeSlider) {
        val newValues = slider.values
        if (callChangeListener(newValues)) {
            values = newValues
        } else {
            slider.values = values
        }
    }
}