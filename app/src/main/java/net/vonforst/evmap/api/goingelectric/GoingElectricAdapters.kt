package net.vonforst.evmap.api.goingelectric

import android.util.Log
import com.squareup.moshi.*
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalTime


internal class ChargepointListItemJsonAdapterFactory : JsonAdapter.Factory {
    override fun create(
        type: Type,
        annotations: MutableSet<out Annotation>,
        moshi: Moshi
    ): JsonAdapter<*>? {
        if (Types.getRawType(type) == GEChargepointListItem::class.java) {
            return ChargepointListItemJsonAdapter(
                moshi
            )
        } else {
            return null
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

internal class JsonObjectOrFalseAdapter<T> private constructor(
    private val objectDelegate: JsonAdapter<T>,
    private val clazz: Class<*>
) : JsonAdapter<T>() {

    class Factory() : JsonAdapter.Factory {
        override fun create(
            type: Type,
            annotations: Set<Annotation>?,
            moshi: Moshi
        ): JsonAdapter<Any>? {
            val clazz = Types.getRawType(type)
            return when (hasJsonObjectOrFalseAnnotation(
                annotations
            )) {
                false -> null
                true -> JsonObjectOrFalseAdapter(
                    moshi.adapter(type), clazz
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun fromJson(reader: JsonReader): T? = when (reader.peek()) {
        JsonReader.Token.BOOLEAN -> when (reader.nextBoolean()) {
            false -> null // Response was false
            else -> {
                if (this.clazz == GEFaultReport::class.java) {
                    GEFaultReport(null, null) as T
                } else {
                    throw IllegalStateException("Non-false boolean for @JsonObjectOrFalse field")
                }
            }
        }
        JsonReader.Token.BEGIN_OBJECT -> objectDelegate.fromJson(reader)
        JsonReader.Token.BEGIN_ARRAY -> objectDelegate.fromJson(reader)
        JsonReader.Token.STRING -> objectDelegate.fromJson(reader)
        JsonReader.Token.NUMBER -> objectDelegate.fromJson(reader)
        else ->
            throw IllegalStateException("Non-object-non-boolean value for @JsonObjectOrFalse field")
    }

    override fun toJson(writer: JsonWriter, value: T?) = objectDelegate.toJson(writer, value)
}

private fun hasJsonObjectOrFalseAnnotation(annotations: Set<Annotation>?) =
    annotations?.firstOrNull { it.annotationClass == JsonObjectOrFalse::class } != null

@JsonQualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
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
                    LocalTime.parse(match.groupValues[2])
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