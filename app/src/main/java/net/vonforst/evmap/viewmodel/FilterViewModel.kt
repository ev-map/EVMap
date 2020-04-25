package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.Equatable
import net.vonforst.evmap.api.goingelectric.GoingElectricApi
import net.vonforst.evmap.storage.AppDatabase

class FilterViewModel(application: Application, geApiKey: String) :
    AndroidViewModel(application) {
    private var api = GoingElectricApi.create(geApiKey)
    private var db = AppDatabase.getInstance(application)

    val filters: MutableLiveData<List<Filter>> by lazy {
        MutableLiveData<List<Filter>>().apply {
            value = listOf(
                BooleanFilter(application.getString(R.string.filter_free), "freecharging")
            )
        }
    }
}

sealed class Filter : Equatable {
    abstract val name: String
    abstract val key: String
}

data class BooleanFilter(override val name: String, override val key: String) : Filter()

data class MultipleChoiceFilter(
    override val name: String,
    override val key: String,
    val choices: Map<String, String>
) : Filter()