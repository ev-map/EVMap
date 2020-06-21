package net.vonforst.evmap.storage

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import net.vonforst.evmap.api.goingelectric.ChargeCardId
import net.vonforst.evmap.api.goingelectric.Chargepoint
import net.vonforst.evmap.api.goingelectric.ChargerPhoto
import java.time.Instant
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
    private val chargeCardIdListAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, ChargeCardId::class.java)
        moshi.adapter<List<ChargeCardId>>(type)
    }
    private val stringSetAdapter by lazy {
        val type = Types.newParameterizedType(Set::class.java, String::class.java)
        moshi.adapter<Set<String>>(type)
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
    fun fromChargeCardIdList(value: List<ChargeCardId>?): String {
        return chargeCardIdListAdapter.toJson(value)
    }

    @TypeConverter
    fun toChargeCardIdList(value: String): List<ChargeCardId>? {
        return chargeCardIdListAdapter.fromJson(value)
    }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? {
        return value?.let {
            LocalTime.parse(it)
        }
    }

    @TypeConverter
    fun fromInstant(value: Instant?): Long? {
        return value?.toEpochMilli()
    }

    @TypeConverter
    fun toInstant(value: Long?): Instant? {
        return value?.let {
            Instant.ofEpochMilli(it)
        }
    }

    @TypeConverter
    fun fromStringSet(value: Set<String>?): String {
        return stringSetAdapter.toJson(value)
    }

    @TypeConverter
    fun toStringSet(value: String): Set<String>? {
        return stringSetAdapter.fromJson(value)
    }
}