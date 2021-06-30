package net.vonforst.evmap.api

import android.app.Application
import android.content.Context
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import net.vonforst.evmap.R
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
import net.vonforst.evmap.api.openchargemap.OpenChargeMapApiWrapper
import net.vonforst.evmap.model.*
import net.vonforst.evmap.viewmodel.Resource

interface ChargepointApi<out T : ReferenceData> {
    suspend fun getChargepoints(
        referenceData: ReferenceData,
        bounds: LatLngBounds,
        zoom: Float,
        filters: FilterValues
    ): Resource<List<ChargepointListItem>>

    suspend fun getChargepointsRadius(
        referenceData: ReferenceData,
        location: LatLng,
        radius: Int,
        zoom: Float,
        filters: FilterValues
    ): Resource<List<ChargepointListItem>>

    suspend fun getChargepointDetail(
        referenceData: ReferenceData,
        id: Long
    ): Resource<ChargeLocation>

    suspend fun getReferenceData(): Resource<T>

    fun getFilters(referenceData: ReferenceData, sp: StringProvider): List<Filter<FilterValue>>

    fun getName(): String
}

interface StringProvider {
    fun getString(id: Int): String
}

fun Context.stringProvider() = object : StringProvider {
    override fun getString(id: Int): String {
        return this@stringProvider.getString(id)
    }
}

fun createApi(type: String, application: Application): ChargepointApi<ReferenceData> {
    return when (type) {
        "openchargemap" -> {
            OpenChargeMapApiWrapper(
                application.getString(
                    R.string.openchargemap_key
                )
            )
        }
        "goingelectric" -> {
            GoingElectricApiWrapper(
                application.getString(
                    R.string.goingelectric_key
                )
            )
        }
        else -> throw IllegalArgumentException()
    }
}