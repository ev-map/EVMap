package net.vonforst.evmap.storage

import androidx.room.TypeConverter
import co.anbora.labs.spatia.geometry.Point
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import net.vonforst.evmap.api.goingelectric.GEChargerPhotoAdapter
import net.vonforst.evmap.api.openchargemap.OCMChargerPhotoAdapter
import net.vonforst.evmap.api.openstreetmap.ImgurChargerPhoto
import net.vonforst.evmap.autocomplete.AutocompletePlaceType
import net.vonforst.evmap.model.ChargeCardId
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.model.ChargerPhoto
import net.vonforst.evmap.model.Coordinate
import java.time.Instant
import java.time.LocalTime

class Converters {
    val moshi = Moshi.Builder()
        .add(
            PolymorphicJsonAdapterFactory.of(ChargerPhoto::class.java, "type")
                .withSubtype(GEChargerPhotoAdapter::class.java, "goingelectric")
                .withSubtype(OCMChargerPhotoAdapter::class.java, "openchargemap")
                .withSubtype(ImgurChargerPhoto::class.java, "imgur")
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
    private val latLngAdapter by lazy {
        moshi.adapter<LatLng>(LatLng::class.java)
    }
    private val latLngBoundsAdapter by lazy {
        moshi.adapter<LatLngBounds>(LatLngBounds::class.java)
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

    @TypeConverter
    fun fromLatLng(value: LatLng?): String {
        return latLngAdapter.toJson(value)
    }

    @TypeConverter
    fun toLatLng(value: String): LatLng? {
        return latLngAdapter.fromJson(value)
    }

    @TypeConverter
    fun fromLatLngBounds(value: LatLngBounds?): String {
        return latLngBoundsAdapter.toJson(value)
    }

    @TypeConverter
    fun toLatLngBounds(value: String): LatLngBounds? {
        return latLngBoundsAdapter.fromJson(value)
    }

    @TypeConverter
    fun fromAutocompletePlaceTypeList(value: List<AutocompletePlaceType>): String {
        return value.joinToString(",") { it.name }
    }

    @TypeConverter
    fun toAutocompletePlaceTypeList(value: String): List<AutocompletePlaceType> {
        return value.split(",").map { AutocompletePlaceType.valueOf(it) }
    }

    @TypeConverter
    fun toCoordinate(value: Point): Coordinate {
        if (value.srid != 4326) throw IllegalArgumentException("expected WGS-84")
        return Coordinate(value.y, value.x)
    }

    @TypeConverter
    fun fromCoordinate(value: Coordinate): Point {
        return Point(value.lng, value.lat)
    }
}