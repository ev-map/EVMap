package net.vonforst.evmap.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

abstract class MultiSelectSearchScreen<T>(ctx: CarContext) : Screen(ctx),
    SearchTemplate.SearchCallback {
    protected var fullList: List<T>? = null
    private var currentList: List<T> = emptyList()
    private var query: String = ""
    private val maxRows = if (ctx.carAppApiLevel >= 2) {
        ctx.constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    } else 6
    protected abstract val isMultiSelect: Boolean

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

    private fun buildItemList(): ItemList {
        return ItemList.Builder().apply {
            currentList.forEach { item ->
                addItem(
                    Row.Builder()
                        .setTitle(
                            if (isSelected(item)) {
                                "☑  " + getLabel(item)
                            } else {
                                "☐  " + getLabel(item)
                            }
                        )
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

    abstract fun isSelected(it: T): Boolean

    abstract fun getLabel(it: T): String

    abstract suspend fun loadData(): List<T>
}