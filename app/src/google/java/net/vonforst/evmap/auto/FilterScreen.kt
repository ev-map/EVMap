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

    init {
        filterProfiles.observe(this) {
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val filterStatus = prefs.filterStatus
        return ListTemplate.Builder().apply {
            filterProfiles.value?.let {
                setSingleList(buildFilterProfilesList(it, filterStatus))
            } ?: setLoading(true)
            setTitle(carContext.getString(R.string.menu_filter))
            setHeaderAction(Action.BACK)
            setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().apply {
                        setIcon(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_edit
                                )
                            ).build()
                        )
                        setTitle(carContext.getString(R.string.menu_edit_filters))
                        setOnClickListener(ParkedOnlyOnClickListener.create {
                            lifecycleScope.launch {
                                db.filterValueDao()
                                    .copyFiltersToCustom(filterStatus, prefs.dataSource)
                                screenManager.push(EditFiltersScreen(carContext))
                            }
                        })
                    }.build())
                    .build()
            )
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
            }.build())
            addItem(Row.Builder().apply {
                setTitle(carContext.getString(R.string.filter_favorites))
            }.build())
            profilesToShow.forEach {
                addItem(Row.Builder().apply {
                    val name =
                        it.name.ifEmpty { carContext.getString(R.string.unnamed_filter_profile) }
                    setTitle(name)
                }.build())
            }
            if (FILTERS_CUSTOM == filterStatus) {
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.filter_custom))
                }.build())
            }
            setSelectedIndex(when (filterStatus) {
                FILTERS_DISABLED -> 0
                FILTERS_FAVORITES -> 1
                FILTERS_CUSTOM -> profilesToShow.size + 2
                else -> profilesToShow.indexOfFirst { it.id == filterStatus } + 2
            })
            setOnSelectedListener { index ->
                onItemClick(
                    when (index) {
                        0 -> FILTERS_DISABLED
                        1 -> FILTERS_FAVORITES
                        profilesToShow.size + 2 -> FILTERS_CUSTOM
                        else -> profilesToShow[index - 2].id
                    }
                )
            }
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
                            setBrowsable(true)
                            setOnClickListener {
                                screenManager.push(
                                    MultipleChoiceFilterScreen(
                                        carContext,
                                        filter,
                                        value as MultipleChoiceFilterValue
                                    )
                                )
                            }
                            addText(
                                if ((value as MultipleChoiceFilterValue).all) {
                                    carContext.getString(R.string.all_selected)
                                } else {
                                    carContext.getString(
                                        R.string.number_selected,
                                        value.values.size
                                    )
                                }
                            )
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
        super.selectAll()
    }

    override fun selectNone() {
        value.all = false
        value.values = mutableSetOf()
        super.selectNone()
    }

    override fun getLabel(it: Pair<String, String>): String = it.second

    override suspend fun loadData(): List<Pair<String, String>> {
        return filter.choices.entries.map { it.toPair() }
    }
}