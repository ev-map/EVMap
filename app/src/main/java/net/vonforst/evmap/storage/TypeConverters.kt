package net.vonforst.evmap.storage

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import net.vonforst.evmap.api.goingelectric.Chargepoint
import net.vonforst.evmap.api.goingelectric.ChargerPhoto
import java.time.LocalTime

class Converters {
    val moshi = Moshi.Builder().build()
    private val chargepointListAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, Chargepoint::class.java)
        moshi.adapter<List<Chargepoint>>(type)
    }
    private val chargerPhotoListAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, ChargerPhoto::class.java)
        moshi.adapter<List<ChargerPhoto>>(type)
    }

    @TypeConverter
    fun fromChargepointList(value: List<Chargepoint>?): String {
        return chargepointListAdapter.toJson(value)
    }

    @TypeConverter
    fun toChargepointList(value: String): List<Chargepoint>? {
        return chargepointListAdapter.fromJson(value)
    }

    @TypeConverter
    fun fromChargerPhotoList(value: List<ChargerPhoto>?): String {
        return chargerPhotoListAdapter.toJson(value)
    }

    @TypeConverter
    fun toChargerPhotoList(value: String): List<ChargerPhoto>? {
        return chargerPhotoListAdapter.fromJson(value)
    }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? {
        return value.let {
            LocalTime.parse(it)
        }
    }
}