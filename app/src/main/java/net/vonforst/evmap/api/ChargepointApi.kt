package net.vonforst.evmap.api

import android.content.Context
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import net.vonforst.evmap.model.*
import net.vonforst.evmap.viewmodel.Resource

interface ChargepointApi<T : ReferenceData> {
    suspend fun getChargepoints(
        bounds: LatLngBounds,
        zoom: Float,
        filters: FilterValues
    ): Resource<List<ChargepointListItem>>

    suspend fun getChargepointsRadius(
        location: LatLng,
        radius: Int,
        zoom: Float,
        filters: FilterValues
    ): Resource<List<ChargepointListItem>>

    suspend fun getChargepointDetail(id: Long): Resource<ChargeLocation>

    suspend fun getReferenceData(): Resource<T>

    fun getFilters(referenceData: T, sp: StringProvider): List<Filter<FilterValue>>
}

interface StringProvider {
    fun getString(id: Int): String
}

fun Context.stringProvider() = object : StringProvider {
    override fun getString(id: Int): String {
        return this@stringProvider.getString(id)
    }
}