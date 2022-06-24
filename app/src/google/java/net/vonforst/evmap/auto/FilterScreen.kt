package net.vonforst.evmap.auto

import android.app.Application
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.vonforst.evmap.R
import net.vonforst.evmap.model.*
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.FilterProfile
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.viewmodel.FilterViewModel

@androidx.car.app.annotations.ExperimentalCarApi
class FilterScreen(ctx: CarContext) : Screen(ctx) {
    private val prefs = PreferenceDataSource(ctx)
    private val db = AppDatabase.getInstance(ctx)
    val filterProfiles: LiveData<List<FilterProfile>> by lazy {
        db.filterProfileDao().getProfiles(prefs.dataSource)
    }
    private val maxRows = if (ctx.carAppApiLevel >= 2) {
        ctx.constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    } else 6
    private val checkedIcon =
        CarIcon.Builder(
            IconCompat.createWithResource(
                carContext,
                R.drawable.ic_radio_button_checked
            )
        )
            .setTint(CarColor.PRIMARY)
            .build()
    private val uncheckedIcon =
        CarIcon.Builder(
            IconCompat.createWithResource(
                carContext,
                R.drawable.ic_radio_button_unchecked
            )
        )
            .setTint(CarColor.PRIMARY)
            .build()

    init {
        filterProfiles.observe(this) {
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val filterStatus =
            prefs.filterStatus.takeUnless { it == FILTERS_FAVORITES } ?: FILTERS_DISABLED
        return ListTemplate.Builder().apply {
            filterProfiles.value?.let {
                setSingleList(buildFilterProfilesList(it, filterStatus))
            } ?: setLoading(true)
            setTitle(carContext.getString(R.string.menu_filter))
            setHeaderAction(Action.BACK)
        }.build()
    }

    private fun buildFilterProfilesList(
        profiles: List<FilterProfile>,
        filterStatus: Long
    ): ItemList {
        val extraRows = if (FILTERS_CUSTOM == filterStatus) 3 else 2
        val profilesToShow = profiles.take(maxRows - extraRows)
        return ItemList.Builder().apply {
            addItem(Row.Builder().apply {
                setTitle(carContext.getString(R.string.no_filters))
                if (FILTERS_DISABLED == filterStatus) {
                    setImage(checkedIcon)
                } else {
                    setImage(uncheckedIcon)
                }
                setOnClickListener {
                    onItemClick(FILTERS_DISABLED)
                }
            }.build())
            profilesToShow.forEach {
                addItem(Row.Builder().apply {
                    val name =
                        it.name.ifEmpty { carContext.getString(R.string.unnamed_filter_profile) }
                    setTitle(name)
                    if (it.id == filterStatus) {
                        setImage(checkedIcon)
                    } else {
                        setImage(uncheckedIcon)
                    }
                    setOnClickListener {
                        onItemClick(it.id)
                    }
                }.build())
            }
            if (FILTERS_CUSTOM == filterStatus) {
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.filter_custom))
                    setImage(checkedIcon)
                    setOnClickListener {
                        onItemClick(FILTERS_CUSTOM)
                    }
                }.build())
            }

            addItem(Row.Builder().apply {
                setTitle(carContext.getString(R.string.menu_edit_filters))
                setOnClickListener(ParkedOnlyOnClickListener.create {
                    lifecycleScope.launch {
                        db.filterValueDao().copyFiltersToCustom(filterStatus, prefs.dataSource)
                        screenManager.push(EditFiltersScreen(carContext))
                    }
                })
            }.build())
        }.build()
    }

    private fun onItemClick(id: Long) {
        prefs.filterStatus = id
        screenManager.pop()
    }
}

@androidx.car.app.annotations.ExperimentalCarApi
class EditFiltersScreen(ctx: CarContext) : Screen(ctx) {
    private val vm = FilterViewModel(carContext.applicationContext as Application)

    private val maxRows = if (ctx.carAppApiLevel >= 2) {
        ctx.constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    } else 6

    init {
        vm.filtersWithValue.observe(this) {
            vm.filterProfile.observe(this) {
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val currentProfileName = vm.filterProfile.value?.name

        return ListTemplate.Builder().apply {
            vm.filtersWithValue.value?.let { filtersWithValue ->
                setSingleList(buildFiltersList(filtersWithValue.take(maxRows)))
            } ?: setLoading(true)

            setTitle(currentProfileName?.let {
                carContext.getString(
                    R.string.edit_filter_profile,
                    it
                )
            } ?: carContext.getString(R.string.menu_filter))

            setHeaderAction(Action.BACK)
            setActionStrip(ActionStrip.Builder().apply {
                addAction(Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_check
                            )
                        ).build()
                    )
                    .setOnClickListener {
                        lifecycleScope.launch {
                            vm.saveFilterValues()
                            screenManager.popTo(MapScreen.MARKER)
                        }
                    }
                    .build()
                )
                addAction(Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_save
                            )
                        ).build()
                    )
                    .setOnClickListener {
                        val textPromptScreen = TextPromptScreen(
                            carContext,
                            R.string.save_as_profile,
                            R.string.save_profile_enter_name,
                            currentProfileName
                        )
                        screenManager.pushForResult(textPromptScreen) { name ->
                            if (name == null) return@pushForResult
                            lifecycleScope.launch {
                                vm.saveAsProfile(name as String)
                                screenManager.popTo(MapScreen.MARKER)
                            }
                        }
                    }
                    .build()
                )
            }.build())
        }.build()
    }

    private fun buildFiltersList(filters: List<FilterWithValue<out FilterValue>>): ItemList {
        return ItemList.Builder().apply {
            filters.forEach {
                val filter = it.filter
                val value = it.value
                addItem(Row.Builder().apply {
                    setTitle(filter.name)
                    when (filter) {
                        is BooleanFilter -> {
                            setToggle(Toggle.Builder {
                                (value as BooleanFilterValue).value = it
                            }.setChecked((value as BooleanFilterValue).value).build())
                        }
                        is MultipleChoiceFilter -> {
                            setOnClickListener {
                                screenManager.push(
                                    MultipleChoiceFilterScreen(
                                        carContext,
                                        filter,
                                        value as MultipleChoiceFilterValue
                                    )
                                )
                            }
                        }
                        is SliderFilter -> {
                            // TODO: toggle through possible options on click?
                        }
                    }
                }.build())
            }
        }.build()
    }
}

class MultipleChoiceFilterScreen(
    ctx: CarContext,
    val filter: MultipleChoiceFilter,
    val value: MultipleChoiceFilterValue
) : MultiSelectSearchScreen<Pair<String, String>>(ctx) {
    override val isMultiSelect = true
    override val shouldShowSelectAll = true

    override fun isSelected(it: Pair<String, String>): Boolean =
        value.all || value.values.contains(it.first)

    override fun toggleSelected(item: Pair<String, String>) {
        if (isSelected(item)) {
            val values = if (value.all) filter.choices.keys else value.values
            value.values = values.minus(item.first).toMutableSet()
            value.all = false
        } else {
            value.values.add(item.first)
            if (value.values == filter.choices.keys) {
                value.all = true
            }
        }
    }

    override fun selectAll() {
        value.all = true
    }

    override fun selectNone() {
        value.all = false
        value.values = mutableSetOf()
    }

    override fun getLabel(it: Pair<String, String>): String = it.second

    override suspend fun loadData(): List<Pair<String, String>> {
        return filter.choices.entries.map { it.toPair() }
    }
}