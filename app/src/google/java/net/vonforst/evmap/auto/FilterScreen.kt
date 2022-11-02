package net.vonforst.evmap.auto

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
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
import kotlin.math.min
import kotlin.math.roundToInt

@androidx.car.app.annotations.ExperimentalCarApi
class FilterScreen(ctx: CarContext, val session: EVMapSession) : Screen(ctx) {
    private val prefs = PreferenceDataSource(ctx)
    private val db = AppDatabase.getInstance(ctx)
    private val filterProfiles: LiveData<List<FilterProfile>> by lazy {
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

            var numProfiles = 0

            filterProfiles.value?.let {
                numProfiles = it.size
            }

            setTitle(carContext.getString(R.string.menu_filter))

            setHeaderAction(Action.BACK)
            setActionStrip(
                ActionStrip.Builder().apply {
                    if (filterStatus !in listOf(
                            FILTERS_CUSTOM,
                            FILTERS_FAVORITES,
                            FILTERS_DISABLED
                        )
                    ) {
                        addAction(Action.Builder().apply {
                            setIcon(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(
                                        carContext,
                                        R.drawable.ic_delete
                                    )
                                ).build()

                            )
                            setOnClickListener {
                                val currentProfile =
                                    filterProfiles.value?.find { it.id == filterStatus }
                                        ?: return@setOnClickListener
                                lifecycleScope.launch {
                                    db.filterProfileDao().delete(currentProfile)
                                    prefs.filterStatus = FILTERS_DISABLED
                                    CarToast.makeText(
                                        carContext,
                                        carContext.getString(
                                            R.string.deleted_filterprofile,
                                            currentProfile.name
                                        ),
                                        CarToast.LENGTH_SHORT
                                    ).show()
                                    invalidate()
                                }
                            }
                        }.build())
                    }
                    addAction(Action.Builder().apply {
                        setIcon(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_edit
                                )
                            ).build()

                        )
                        setOnClickListener(ParkedOnlyOnClickListener.create {
                            lifecycleScope.launch {
                                db.filterValueDao()
                                    .copyFiltersToCustom(filterStatus, prefs.dataSource)
                                screenManager.push(EditFiltersScreen(carContext,numProfiles))
                            }
                        })
                    }.build())
                }.build()
            )
        }.build()
    }

    private fun buildFilterProfilesList(
        profiles: List<FilterProfile>,
        filterStatus: Long
    ): ItemList {
        //val extraRows = if (FILTERS_CUSTOM == filterStatus) 3 else 2
        val extraRows = 3
        val profilesToShow =
            profiles.sortedByDescending { it.id == filterStatus }.take(maxRows - extraRows)
        return ItemList.Builder().apply {
            addItem(Row.Builder().apply {
                setTitle(carContext.getString(R.string.no_filters))
                setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_close
                        )
                    ).build(),
                    Row.IMAGE_TYPE_ICON
                )
            }.build())
            addItem(Row.Builder().apply {
                setTitle(carContext.getString(R.string.filter_favorites))
                setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.ic_fav
                        )
                    ).build(),
                    Row.IMAGE_TYPE_ICON
                )
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
class EditFiltersScreen(ctx: CarContext, val numProfiles: Int) : Screen(ctx) {
    private val vm = FilterViewModel(carContext.applicationContext as Application)

    private val maxRows = if (ctx.carAppApiLevel >= 2) {
        ctx.constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    } else 6

    private var page = 0
    private val maxPages = 2

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
                val start = page * maxRows
                val end = min(start + maxRows, filtersWithValue.lastIndex + 1)
                setSingleList(buildFiltersList(filtersWithValue.subList(start, end)))
            } ?: setLoading(true)

            var title = currentProfileName?.let {
                carContext.getString(
                    R.string.edit_filter_profile,
                    it,
                )
            } ?: carContext.getString(R.string.menu_filter)
            if ((vm.filtersWithValue.value?.size ?: 0) > maxRows) {
                title += " " + carContext.getString(R.string.auto_multipage, page + 1, maxPages)
            }
            setTitle(title)

            setHeaderAction(Action.BACK)

            setActionStrip(ActionStrip.Builder().apply {
                addAction(
                    Action.Builder()
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
                                var saveSuccess = false
                                lifecycleScope.launch {
                                    saveSuccess = vm.saveAsProfile(
                                        name as String,
                                        numProfiles < maxRows - 3
                                    )
                                    if (saveSuccess) {
                                        screenManager.popTo(MapScreen.MARKER)
                                    } else {
                                        CarToast.makeText(
                                            carContext,
                                            "No more Space",
                                            CarToast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                if (!saveSuccess) return@pushForResult
                            }
                            invalidate()
                        }
                        .build()
                )
                addAction(Action.Builder().apply {
                    setTitle(
                        CarText.Builder(
                            carContext.getString(if (page == 0) R.string.auto_multipage_more else R.string.auto_multipage_back)
                        ).build()
                    )
                    setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                if (page == 0) R.drawable.ic_arrow_forward else R.drawable.ic_arrow_back
                            )
                        ).build()

                    )
                    setOnClickListener {
                        if (page == 0) page = 1 else page = 0
                        screenManager.pushForResult(DummyReturnScreen(carContext)) {
                            Handler(Looper.getMainLooper()).post {
                                invalidate()
                            }
                        }
                    }
                }
                    .build())
            }
                .build())
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
                                lifecycleScope.launch { vm.saveFilterValues() }
                            }.setChecked((value as BooleanFilterValue).value).build())
                        }
                        is MultipleChoiceFilter -> {
                            setBrowsable(true)
                            setOnClickListener {
                                screenManager.pushForResult(
                                    MultipleChoiceFilterScreen(
                                        carContext,
                                        filter,
                                        value as MultipleChoiceFilterValue
                                    )
                                ) {
                                    lifecycleScope.launch { vm.saveFilterValues() }
                                }
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
                            setBrowsable(true)
                            addText((value as SliderFilterValue).value.toString() + " " + filter.unit)
                            setOnClickListener {
                                screenManager.pushForResult(
                                    SliderFilterScreen(
                                        carContext,
                                        filter,
                                        value
                                    )
                                ) {
                                    lifecycleScope.launch { vm.saveFilterValues() }
                                }
                            }
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


class SliderFilterScreen(
    ctx: CarContext,
    val filter: SliderFilter,
    val value: SliderFilterValue
) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        return PaneTemplate.Builder(
            Pane.Builder().apply {
                addRow(Row.Builder().apply {
                    setTitle(filter.name)
                    addText(value.value.toString() + " " + filter.unit)
                    addText(generateSlider())
                }.build())
                addAction(Action.Builder().apply {
                    setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_remove
                            )
                        ).build()
                    )
                    setOnClickListener(::decrease)
                }.build())
                addAction(Action.Builder().apply {
                    setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_add
                            )
                        ).build()
                    )
                    setOnClickListener(::increase)
                }.build())
            }.build()
        ).apply {
            setHeaderAction(Action.BACK)
        }.build()
    }

    private fun generateSlider(): CharSequence {
        val bar = "━"
        val dot = "⬤"
        val length = 30

        val position =
            ((filter.inverseMapping(value.value) - filter.min) / (filter.max - filter.min).toDouble() * length).roundToInt()

        val text = SpannableStringBuilder()
        text.append(
            bar.repeat(position),
            ForegroundCarColorSpan.create(CarColor.SECONDARY),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        text.append(
            dot,
            ForegroundCarColorSpan.create(CarColor.SECONDARY),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        text.append(bar.repeat(length - position))

        return text
    }

    private fun increase() {
        var valueInternal = filter.inverseMapping(value.value)
        if (valueInternal < filter.max) valueInternal += 1
        value.value = filter.mapping(valueInternal)
        invalidate()
    }

    private fun decrease() {
        var valueInternal = filter.inverseMapping(value.value)
        if (valueInternal > filter.min) valueInternal -= 1
        value.value = filter.mapping(valueInternal)
        invalidate()
    }
}