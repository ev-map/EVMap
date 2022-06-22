package net.vonforst.evmap.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.vonforst.evmap.R

abstract class MultiSelectSearchScreen<T>(ctx: CarContext) : Screen(ctx),
    SearchTemplate.SearchCallback {
    protected var fullList: List<T>? = null
    private var currentList: List<T> = emptyList()
    private var query: String = ""
    private val maxRows = if (ctx.carAppApiLevel >= 2) {
        ctx.constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    } else 6
    protected abstract val isMultiSelect: Boolean
    protected abstract val shouldShowSelectAll: Boolean

    override fun onGetTemplate(): Template {
        if (fullList == null) {
            lifecycleScope.launch {
                fullList = loadData()
                filterList()
                invalidate()
            }
        }

        return SearchTemplate.Builder(this).apply {
            setHeaderAction(Action.BACK)
            fullList?.let {
                setItemList(buildItemList())
            } ?: run {
                setLoading(true)
            }
            if (isMultiSelect) {
                setActionStrip(ActionStrip.Builder().apply {
                    addAction(
                        Action.Builder().setIcon(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_select_all
                                )
                            ).build()
                        ).setOnClickListener(::selectAll).build()
                    )
                    addAction(
                        Action.Builder().setIcon(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_select_none
                                )
                            ).build()
                        ).setOnClickListener(::selectNone).build()
                    )
                }.build())
            }
        }.build()
    }

    private fun filterList() {
        currentList = fullList?.let {
            it.sortedBy { getLabel(it).lowercase() }
                .sortedBy { !isSelected(it) }
                .filter { getLabel(it).lowercase().contains(query.lowercase()) }
                .take(maxRows)
        } ?: emptyList()
    }

    private val checkedIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_checkbox_checked))
            .setTint(CarColor.PRIMARY)
            .build()
    private val uncheckedIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_checkbox_unchecked))
            .setTint(CarColor.PRIMARY)
            .build()

    private fun buildItemList(): ItemList {
        return ItemList.Builder().apply {
            currentList.forEach { item ->
                addItem(
                    Row.Builder()
                        .setTitle(getLabel(item))
                        .setImage(if (isSelected(item)) checkedIcon else uncheckedIcon)
                        .setOnClickListener {
                            toggleSelected(item)
                            if (isMultiSelect) {
                                invalidate()
                            } else {
                                setResult(item)
                                screenManager.pop()
                            }
                        }
                        .build()
                )
            }
        }.build()
    }

    override fun onSearchTextChanged(searchText: String) {
        query = searchText
        filterList()
        invalidate()
    }

    override fun onSearchSubmitted(searchText: String) {
        query = searchText
        filterList()
        invalidate()
    }

    abstract fun toggleSelected(item: T)

    open fun selectAll() {
        CarToast.makeText(carContext, R.string.selecting_all, CarToast.LENGTH_SHORT).show()
        invalidate()
    }

    open fun selectNone() {
        CarToast.makeText(carContext, R.string.selecting_none, CarToast.LENGTH_SHORT).show()
        invalidate()
    }

    abstract fun isSelected(it: T): Boolean

    abstract fun getLabel(it: T): String

    abstract suspend fun loadData(): List<T>
}