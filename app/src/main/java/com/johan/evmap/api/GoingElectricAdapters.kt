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