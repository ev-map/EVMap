package com.johan.evmap.api

import com.squareup.moshi.*
import java.lang.reflect.Type


internal class ChargepointListItemJsonAdapterFactory : JsonAdapter.Factory {
    override fun create(
        type: Type,
        annotations: MutableSet<out Annotation>,
        moshi: Moshi
    ): JsonAdapter<*>? {
        if (Types.getRawType(type) == ChargepointListItem::class.java) {
            return ChargepointListItemJsonAdapter(moshi)
        } else {
            return null
        }
    }

}


internal class ChargepointListItemJsonAdapter(val moshi: Moshi) :
    JsonAdapter<ChargepointListItem>() {
    private val clusterAdapter =
        moshi.adapter<ChargeLocationCluster>(ChargeLocationCluster::class.java)

    private val locationAdapter = moshi.adapter<ChargeLocation>(ChargeLocation::class.java)

    @FromJson
    override fun fromJson(reader: JsonReader): ChargepointListItem {
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

    override fun toJson(writer: JsonWriter, value: ChargepointListItem?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

internal class JsonObjectOrFalseAdapter<T> private constructor(
    private val objectDelegate: JsonAdapter<T>?
) : JsonAdapter<T>() {

    class Factory() : JsonAdapter.Factory {
        override fun create(
            type: Type,
            annotations: Set<Annotation>?,
            moshi: Moshi
        ): JsonAdapter<*>? {
            val clazz = Types.getRawType(type)
            return when (hasJsonObjectOrFalseAnnotation(annotations)) {
                false -> null
                true -> JsonObjectOrFalseAdapter(moshi.adapter(clazz))
            }
        }
    }

    override fun fromJson(reader: JsonReader) = when (reader.peek()) {
        JsonReader.Token.BOOLEAN -> when (reader.nextBoolean()) {
            false -> null // Response was false
            else ->
                throw IllegalStateException("Non-false boolean for @JsonObjectOrFalse field")
        }
        JsonReader.Token.BEGIN_OBJECT -> objectDelegate?.fromJson(reader)
        JsonReader.Token.STRING -> objectDelegate?.fromJson(reader)
        JsonReader.Token.NUMBER -> objectDelegate?.fromJson(reader)
        else ->
            throw IllegalStateException("Non-object-non-boolean value for @JsonObjectOrFalse field")
    }

    override fun toJson(writer: JsonWriter, value: T?) =
        objectDelegate?.toJson(writer, value) ?: Unit
}

private fun hasJsonObjectOrFalseAnnotation(annotations: Set<Annotation>?) =
    annotations?.firstOrNull { it.annotationClass == JsonObjectOrFalse::class } != null

@JsonQualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class JsonObjectOrFalse {

}
