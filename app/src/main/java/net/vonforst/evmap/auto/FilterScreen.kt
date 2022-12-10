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
import androidx.lifecycle.map
import kotlinx.coroutines.launch
import net.vonforst.evmap.R
import net.vonforst.evmap.model.*
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.FilterProfile
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.viewmodel.FilterViewModel
import kotlin.math.roundToInt

@androidx.car.app.annotations.ExperimentalCarApi
class FilterScreen(ctx: CarContext, val session: EVMapSession) : Screen(ctx) {
    private val prefs = PreferenceDataSource(ctx)
    private val db = AppDatabase.getInstance(ctx)
    private val filterProfiles: LiveData<List<FilterProfile>> by lazy {
        db.filterProfileDao().getProfiles(prefs.dataSource)
    }

    private val maxRows = ctx.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    private val supportsRefresh = ctx.isAppDrivenRefreshSupported
    private var page = 0

    init {
        filterProfiles.observe(this) {
            val filterStatus = prefs.filterStatus
            if (filterStatus in listOf(FILTERS_DISABLED, FILTERS_FAVORITES, FILTERS_CUSTOM)) {
                page = 0
            } else {
                page = paginateProfiles(it).indexOfFirst { it.any { it.id == filterStatus } }
            }
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val filterStatus = prefs.filterStatus
        return ListTemplate.Builder().apply {
            var title = carContext.getString(R.string.menu_filter)

            filterProfiles.value?.let {
                val paginatedProfiles = paginateProfiles(it)
                setSingleList(buildFilterProfilesList(paginatedProfiles, filterStatus))

                val numPages = paginatedProfiles.size
                if (numPages > 1) {
                    title += " " + carContext.getString(
                        R.string.auto_multipage,
                        page + 1,
                        numPages
                    )
                }
            } ?: setLoading(true)

            setTitle(title)

            setHeaderAction(Action.BACK)
            setActionStrip(
                ActionStrip.Builder().apply {
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
                                screenManager.push(EditFiltersScreen(carContext))
                            }
                        })
                    }.build())
                }.build()
            )
        }.build()
    }

    private fun paginateProfiles(filterProfiles: List<FilterProfile>): List<List<FilterProfile>> {
        val filterStatus = prefs.filterStatus
        val extraRows = if (FILTERS_CUSTOM == filterStatus) 3 else 2
        return filterProfiles.paginate(
            maxRows - extraRows,
            maxRows - extraRows - 1,
            maxRows - 2,
            maxRows - 1
        )
    }

    private fun buildFilterProfilesList(
        paginatedProfiles: List<List<FilterProfile>>,
        filterStatus: Long
    ): ItemList {
        return ItemList.Builder().apply {
            if (page > 0) {
                addItem(Row.Builder().apply {
                    setTitle(
                        CarText.Builder(
                            carContext.getString(R.string.auto_multipage_goto, page)
                        ).build()
                    )
                    setImage(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_arrow_back
                            )
                        ).build(),
                        Row.IMAGE_TYPE_ICON
                    )
                    setOnClickListener {
                        page -= 1
                        if (!supportsRefresh) {
                            screenManager.pushForResult(DummyReturnScreen(carContext)) {
                                Handler(Looper.getMainLooper()).post {
                                    invalidate()
                                }
                            }
                        } else {
                            invalidate()
                        }
                    }
                }.build())
            }

            if (page == 0) {
                addItem(Row.Builder().apply {
                    val active = filterStatus == FILTERS_DISABLED
                    setTitle(carContext.getString(R.string.no_filters))
                    setImage(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_close
                            )
                        ).setTint(if (active) CarColor.SECONDARY else CarColor.DEFAULT)
                            .build(),
                        Row.IMAGE_TYPE_ICON
                    )
                    setOnClickListener { onItemClick(FILTERS_DISABLED) }
                }.build())
                addItem(Row.Builder().apply {
                    val active = filterStatus == FILTERS_FAVORITES
                    setTitle(carContext.getString(R.string.filter_favorites))
                    setImage(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_fav
                            )
                        ).setTint(if (active) CarColor.SECONDARY else CarColor.DEFAULT)
                            .build(),
                        Row.IMAGE_TYPE_ICON
                    )
                    setOnClickListener { onItemClick(FILTERS_FAVORITES) }
                }.build())
                if (FILTERS_CUSTOM == filterStatus) {
                    addItem(Row.Builder().apply {
                        setTitle(carContext.getString(R.string.filter_custom))
                        setImage(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_checkbox_checked
                                )
                            ).setTint(CarColor.PRIMARY).build(),
                            Row.IMAGE_TYPE_ICON
                        )
                        setOnClickListener { onItemClick(FILTERS_CUSTOM) }
                    }.build())
                }
            }
            paginatedProfiles[page].forEach {
                addItem(Row.Builder().apply {
                    val name =
                        it.name.ifEmpty { carContext.getString(R.string.unnamed_filter_profile) }
                    val active = filterStatus == it.id
                    setTitle(name)
                    setImage(
                        if (active)
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_check
                                )
                            ).setTint(CarColor.SECONDARY).build() else emptyCarIcon,
                        Row.IMAGE_TYPE_ICON
                    )
                    setOnClickListener { onItemClick(it.id) }
                    if (carContext.carAppApiLevel >= 6) {
                        // Delete action
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
                                lifecycleScope.launch {
                                    db.filterProfileDao().delete(it)
                                    if (prefs.filterStatus == it.id) {
                                        prefs.filterStatus = FILTERS_DISABLED
                                    }
                                    CarToast.makeText(
                                        carContext,
                                        carContext.getString(
                                            R.string.deleted_filterprofile,
                                            it.name
                                        ),
                                        CarToast.LENGTH_SHORT
                                    ).show()
                                    invalidate()
                                }
                            }
                        }.build())
                    }
                }.build())
            }
            if (page < paginatedProfiles.size - 1) {
                addItem(Row.Builder().apply {
                    setTitle(
                        CarText.Builder(
                            carContext.getString(R.string.auto_multipage_goto, page + 2)
                        ).build()
                    )
                    setImage(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_arrow_forward
                            )
                        ).build(),
                        Row.IMAGE_TYPE_ICON
                    )
                    setOnClickListener {
                        page += 1
                        if (!supportsRefresh) {
                            screenManager.pushForResult(DummyReturnScreen(carContext)) {
                                Handler(Looper.getMainLooper()).post {
                                    invalidate()
                                }
                            }
                        } else {
                            invalidate()
                        }
                    }
                }.build())
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

    private val maxRows = ctx.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    private val supportsRefresh = ctx.isAppDrivenRefreshSupported

    private var page = 0
    private var paginatedFilters = vm.filtersWithValue.map {
        it?.paginate(maxRows, maxRows - 1, maxRows - 2, maxRows - 1)
    }

    init {
        paginatedFilters.observe(this) {
            vm.filterProfile.observe(this) {
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val currentProfileName = vm.filterProfile.value?.name

        return ListTemplate.Builder().apply {
            paginatedFilters.value?.let { paginatedFilters ->
                setSingleList(buildFiltersList(paginatedFilters))
            } ?: setLoading(true)

            var title = currentProfileName?.let {
                carContext.getString(
                    R.string.edit_filter_profile,
                    it,
                )
            } ?: carContext.getString(R.string.menu_filter)
            val numPages = paginatedFilters.value?.size ?: 0
            if (numPages > 1) {
                title += " " + carContext.getString(
                    R.string.auto_multipage,
                    page + 1,
                    numPages
                )
            }
            setTitle(title)

            setHeaderAction(Action.BACK)

            setActionStrip(ActionStrip.Builder().apply {
                val currentProfile = vm.filterProfile.value
                if (currentProfile != null && carContext.carAppApiLevel < 6) {
                    // Delete action (when row actions are not available)
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
                            lifecycleScope.launch {
                                vm.deleteCurrentProfile()
                                CarToast.makeText(
                                    carContext,
                                    carContext.getString(
                                        R.string.deleted_filterprofile,
                                        currentProfile.name
                                    ),
                                    CarToast.LENGTH_SHORT
                                ).show()
                                invalidate()
                                screenManager.pop()
                            }
                        }
                    }.build())
                }
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
                                    saveSuccess = vm.saveAsProfile(name as String)
                                    screenManager.popTo(MapScreen.MARKER)
                                }
                                if (!saveSuccess) return@pushForResult
                            }
                            invalidate()
                        }
                        .build()
                )
            }
                .build())
        }.build()
    }

    private fun buildFiltersList(paginatedFilters: List<FilterValues>): ItemList {

        return ItemList.Builder().apply {
            if (page > 0) {
                addItem(Row.Builder().apply {
                    setTitle(
                        CarText.Builder(
                            carContext.getString(R.string.auto_multipage_goto, page)
                        ).build()
                    )
                    setImage(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_arrow_back
                            )
                        ).build(),
                        Row.IMAGE_TYPE_ICON
                    )
                    setOnClickListener {
                        page -= 1
                        if (!supportsRefresh) {
                            screenManager.pushForResult(DummyReturnScreen(carContext)) {
                                Handler(Looper.getMainLooper()).post {
                                    invalidate()
                                }
                            }
                        } else {
                            invalidate()
                        }
                    }
                }.build())
            }

            paginatedFilters[page].forEach {
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

            if (page < paginatedFilters.size - 1) {
                addItem(Row.Builder().apply {
                    setTitle(
                        CarText.Builder(
                            carContext.getString(R.string.auto_multipage_goto, page + 2)
                        ).build()
                    )
                    setImage(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_arrow_forward
                            )
                        ).build(),
                        Row.IMAGE_TYPE_ICON
                    )
                    setOnClickListener {
                        page += 1
                        if (!supportsRefresh) {
                            screenManager.pushForResult(DummyReturnScreen(carContext)) {
                                Handler(Looper.getMainLooper()).post {
                                    invalidate()
                                }
                            }
                        } else {
                            invalidate()
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