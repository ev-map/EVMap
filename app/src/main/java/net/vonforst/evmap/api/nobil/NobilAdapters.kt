package net.vonforst.evmap.api.nobil

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.rawType
import net.vonforst.evmap.model.Coordinate
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class CoordinateAdapter {
    @FromJson
    fun fromJson(position: String): Coordinate {
        val pattern = """\((\d+(\.\d+)?), *(-?\d+(\.\d+)?)\)"""
        val match = Regex(pattern).matchEntire(position)
            ?: throw JsonDataException("Unexpected coordinate format: '$position'")

        val latitude : String = match.groups[1]?.value ?: "0.0"
        val longitude : String = match.groups[3]?.value ?: "0.0"
        return Coordinate(latitude.toDouble(), longitude.toDouble())
    }

    @ToJson
    fun toJson(value: Coordinate): String = "(" + value.lat + ", " + value.lng + ")"
}

internal class LocalDateTimeAdapter {
    @FromJson
    fun fromJson(value: String?): LocalDateTime? = value?.let {
        LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }

    @ToJson
    fun toJson(value: LocalDateTime?): String? = value?.toString()
}

internal class NobilConverterFactory(val moshi: Moshi) : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val stringAdapter = moshi.adapter(String::class.java)

        if (type.rawType == NobilNumChargepointsResponseData::class.java) {
            // {"Provider":"NOBIL.no",
            //  "Rights":"Creative Commons Attribution 4.0 International License",
            //  "apiver":"3",
            //  "chargerstations": [{"count":8748}]
            //  }
            return Converter<ResponseBody, NobilNumChargepointsResponseData> { body ->
                val reader = JsonReader.of(body.source())
                reader.beginObject()

                var error: String? = null
                var provider: String? = null
                var rights: String? = null
                var apiver: String? = null
                var count: Int? = null
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "error" -> error = stringAdapter.fromJson(reader)!!
                        "Provider" -> provider = stringAdapter.fromJson(reader)!!
                        "Rights" -> rights = stringAdapter.fromJson(reader)!!
                        "apiver" -> apiver = stringAdapter.fromJson(reader)!!
                        "chargerstations" -> {
                            reader.beginArray()
                            val intAdapter = moshi.adapter(Int::class.java)
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "count" -> count = intAdapter.fromJson(reader)!!
                                }
                            }
                            reader.endObject()
                            reader.endArray()
                            reader.close()
                            break
                        }
                    }
                }
                NobilNumChargepointsResponseData(error, provider, rights, apiver, count)
            }
        }

        if (type.rawType == NobilDynamicResponseData::class.java) {
            val nobilChargerStationAdapter = moshi.adapter(NobilChargerStation::class.java)
            return Converter<ResponseBody, NobilDynamicResponseData> { body ->
                val reader = JsonReader.of(body.source())
                reader.beginObject()

                var error: String? = null
                var provider: String? = null
                var rights: String? = null
                var apiver: String? = null
                var doc: Sequence<NobilChargerStation>? = null
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "error" -> error = stringAdapter.fromJson(reader)!!
                        "Provider" -> provider = stringAdapter.fromJson(reader)!!
                        "Rights" -> rights = stringAdapter.fromJson(reader)!!
                        "apiver" -> apiver = stringAdapter.fromJson(reader)!!
                        "chargerstations" -> {
                            doc = sequence {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    yield(nobilChargerStationAdapter.fromJson(reader)!!)
                                }
                                reader.endArray()
                                reader.close()
                            }
                            break
                        }
                    }
                }
                NobilDynamicResponseData(error, provider, rights, apiver, doc)
            }
        }

        return null
    }
}