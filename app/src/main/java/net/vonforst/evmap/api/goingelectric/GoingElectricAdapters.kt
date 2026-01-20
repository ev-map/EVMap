package net.vonforst.evmap.api.goingelectric

import android.util.Log
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeParseException


internal class ChargepointListItemJsonAdapterFactory : JsonAdapter.Factory {
    override fun create(
        type: Type,
        annotations: MutableSet<out Annotation>,
        moshi: Moshi
    ): JsonAdapter<*>? {
        return if (Types.getRawType(type) == GEChargepointListItem::class.java) {
            ChargepointListItemJsonAdapter(
                moshi
            )
        } else {
            null
        }
    }

}


internal class ChargepointListItemJsonAdapter(val moshi: Moshi) :
    JsonAdapter<GEChargepointListItem>() {
    private val clusterAdapter =
        moshi.adapter<GEChargeLocationCluster>(
            GEChargeLocationCluster::class.java
        )

    private val locationAdapter = moshi.adapter<GEChargeLocation>(
        GEChargeLocation::class.java
    )

    @FromJson
    override fun fromJson(reader: JsonReader): GEChargepointListItem {
        var clustered = false
        reader.peekJson().use { peeked ->
            peeked.beginObject()
            while (peeked.hasNext()) {
                if (peeked.selectName(CLUSTERED) == 0) {
                    clustered = peeked.nextBoolean()
                    break
                }
                peeked.skipName()
                peeked.skipValue()
            }
        }
        return if (clustered) {
            clusterAdapter.fromJson(reader)!!
        } else {
            locationAdapter.fromJson(reader)!!
        }
    }

    companion object {
        val CLUSTERED: JsonReader.Options = JsonReader.Options.of("clustered")
    }

    override fun toJson(writer: JsonWriter, value: GEChargepointListItem?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class StringOrFalseAdapter {
    @FromJson
    @JsonObjectOrFalse
    fun fromJson(reader: JsonReader): String? = when (reader.peek()) {
        JsonReader.Token.BOOLEAN -> when (reader.nextBoolean()) {
            false -> null
            true -> throw IllegalArgumentException("found true")
        }

        JsonReader.Token.STRING -> reader.nextString()
        else -> throw IllegalArgumentException("illegal value found")
    }

    @ToJson
    fun toJson(writer: JsonWriter, @JsonObjectOrFalse value: String?) {
        writer.value(value)
    }
}

class IntOrFalseAdapter {
    @FromJson
    @JsonObjectOrFalse
    fun fromJson(reader: JsonReader): Int? = when (reader.peek()) {
        JsonReader.Token.BOOLEAN -> when (reader.nextBoolean()) {
            false -> null
            true -> throw IllegalArgumentException("found true")
        }

        JsonReader.Token.NUMBER -> reader.nextInt()
        else -> throw IllegalArgumentException("illegal value found")
    }

    @ToJson
    fun toJson(writer: JsonWriter, @JsonObjectOrFalse value: Int?) {
        writer.value(value)
    }
}

class GEFaultReportOrFalseAdapter {
    @FromJson
    @JsonObjectOrFalse
    fun fromJson(reader: JsonReader, delegate: JsonAdapter<GEFaultReport>): GEFaultReport? =
        when (reader.peek()) {
            JsonReader.Token.BOOLEAN -> when (reader.nextBoolean()) {
                false -> null
                true -> GEFaultReport(null, "")
            }

            else -> delegate.fromJson(reader)
        }

    @ToJson
    fun toJson(
        writer: JsonWriter,
        @JsonObjectOrFalse value: GEFaultReport?,
        delegate: JsonAdapter<GEFaultReport>
    ) {
        delegate.toJson(writer, value)
    }
}


class ChargeCardListOrFalseAdapter {
    @FromJson
    @JsonObjectOrFalse
    fun fromJson(
        reader: JsonReader,
        delegate: JsonAdapter<List<GEChargeCardId>>
    ): List<GEChargeCardId>? = when (reader.peek()) {
        JsonReader.Token.BOOLEAN -> when (reader.nextBoolean()) {
            false -> null
            true -> throw IllegalArgumentException("found true")
        }

        else -> delegate.fromJson(reader)
    }

    @ToJson
    fun toJson(
        writer: JsonWriter,
        @JsonObjectOrFalse value: List<GEChargeCardId>?,
        delegate: JsonAdapter<List<GEChargeCardId>>
    ) {
        delegate.toJson(writer, value)
    }
}

@JsonQualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonObjectOrFalse {

}

internal class HoursAdapter {
    private val regex = Regex("from (.*) till (.*)")

    @FromJson
    fun fromJson(str: String): GEHours {
        if (str == "closed") {
            return GEHours(null, null)
        } else if (str == "around the clock") {
            return GEHours(LocalTime.MIN, LocalTime.MAX)
        } else {
            val match = regex.find(str)
            if (match != null) {
                val start = LocalTime.parse(match.groupValues[1])
                val end = if (match.groupValues[2] == "24:00") {
                    LocalTime.MAX
                } else {
                    try {
                        LocalTime.parse(match.groupValues[2])
                    } catch (e: DateTimeParseException) {
                        // got a rare bug report where the value is 24:0000
                        LocalTime.MIN
                    }
                }
                return GEHours(start, end)
            } else {
                // I cannot reproduce this case, but it seems to occur once in a while
                Log.e("GoingElectricApi", "invalid hours value: " + str)
                return GEHours(
                    LocalTime.MIN, LocalTime.MIN
                )
            }
        }
    }

    @ToJson
    fun toJson(value: GEHours): String {
        if (value.start == null || value.end == null) {
            return "closed"
        } else {
            return "from ${value.start} till ${value.end}"
        }
    }

}

internal class InstantAdapter {
    @FromJson
    fun fromJson(value: Long?): Instant? = value?.let {
        Instant.ofEpochSecond(it)
    }

    @ToJson
    fun toJson(value: Instant?): Long? = value?.epochSecond
}