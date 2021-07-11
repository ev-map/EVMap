package net.vonforst.evmap.storage

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import net.vonforst.evmap.api.goingelectric.GEChargerPhotoAdapter
import net.vonforst.evmap.api.openchargemap.OCMChargerPhotoAdapter
import net.vonforst.evmap.model.ChargeCardId
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.model.ChargerPhoto
import java.time.Instant
import java.time.LocalTime

class Converters {
    val moshi = Moshi.Builder()
        .add(
            PolymorphicJsonAdapterFactory.of(ChargerPhoto::class.java, "type")
                .withSubtype(GEChargerPhotoAdapter::class.java, "goingelectric")
                .withSubtype(OCMChargerPhotoAdapter::class.java, "openchargemap")
                .withDefaultValue(null)
        )
        .build()
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
    private val stringListAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        moshi.adapter<List<String>>(type)
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
        return chargerPhotoListAdapter.fromJson(value)?.filterNotNull()
    }

    @TypeConverter
    fun fromChargeCardIdList(value: List<ChargeCardId>?): String {
        return chargeCardIdListAdapter.toJson(value)
    }

    @TypeConverter
    fun toChargeCardIdList(value: String?): List<ChargeCardId>? {
        return value?.let { chargeCardIdListAdapter.fromJson(it) }
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

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return stringListAdapter.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String>? {
        return stringListAdapter.fromJson(value)
    }
}