package net.vonforst.evmap.api.openstreetmap

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.rawType
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type
import java.time.Instant
import kotlin.math.floor

internal class InstantAdapter {
    @FromJson
    fun fromJson(value: Double?): Instant? = value?.let {
        val seconds = floor(it).toLong()
        val nanos = ((value - seconds) * 1e9).toLong()
        Instant.ofEpochSecond(seconds, nanos)
    }

    @ToJson
    fun toJson(value: Instant?): Double? = value?.let {
        it.epochSecond.toDouble() + it.nano / 1e9
    }
}

internal class OSMConverterFactory(val moshi: Moshi) : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        if (type.rawType != OSMDocument::class.java) return null

        val instantAdapter = moshi.adapter(Instant::class.java)
        val osmChargingStationAdapter = moshi.adapter(OSMChargingStation::class.java)
        return Converter<ResponseBody, OSMDocument> { body ->
            val reader = JsonReader.of(body.source())
            reader.beginObject()

            var timestamp: Instant? = null
            var doc: Sequence<OSMChargingStation>? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "timestamp" -> timestamp = instantAdapter.fromJson(reader)!!
                    "elements" -> {
                        doc = sequence {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                yield(osmChargingStationAdapter.fromJson(reader)!!)
                            }
                            reader.endArray()
                            reader.close()
                        }
                        break
                    }
                }
            }
            OSMDocument(timestamp!!, doc!!)
        }
    }
}