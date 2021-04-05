package net.vonforst.evmap.api

import android.content.Context
import androidx.annotation.DrawableRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import net.vonforst.evmap.R
import net.vonforst.evmap.api.goingelectric.Chargepoint
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import kotlin.coroutines.resumeWithException

operator fun <T> JSONArray.iterator(): Iterator<T> =
    (0 until length()).asSequence().map {
        @Suppress("UNCHECKED_CAST")
        get(it) as T
    }.iterator()

@ExperimentalCoroutinesApi
suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) {}
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        })

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Throwable) {
                //Ignore cancel exception
            }
        }
    }
}

private val plugNames = mapOf(
    Chargepoint.TYPE_1 to R.string.plug_type_1,
    Chargepoint.TYPE_2 to R.string.plug_type_2,
    Chargepoint.TYPE_3 to R.string.plug_type_3,
    Chargepoint.CCS to R.string.plug_ccs,
    Chargepoint.SCHUKO to R.string.plug_schuko,
    Chargepoint.CHADEMO to R.string.plug_chademo,
    Chargepoint.SUPERCHARGER to R.string.plug_supercharger,
    Chargepoint.CEE_BLAU to R.string.plug_cee_blau,
    Chargepoint.CEE_ROT to R.string.plug_cee_rot,
    Chargepoint.TESLA_ROADSTER_HPC to R.string.plug_roadster_hpc
)

fun nameForPlugType(ctx: Context, type: String): String =
    plugNames[type]?.let {
        ctx.getString(it)
    } ?: type

@DrawableRes
fun iconForPlugType(type: String): Int =
    when (type) {
        Chargepoint.CCS -> R.drawable.ic_connector_ccs
        Chargepoint.CHADEMO -> R.drawable.ic_connector_chademo
        Chargepoint.SCHUKO -> R.drawable.ic_connector_schuko
        Chargepoint.SUPERCHARGER -> R.drawable.ic_connector_supercharger
        Chargepoint.TYPE_2 -> R.drawable.ic_connector_typ2
        Chargepoint.CEE_BLAU -> R.drawable.ic_connector_cee_blau
        Chargepoint.CEE_ROT -> R.drawable.ic_connector_cee_rot
        Chargepoint.TYPE_1 -> R.drawable.ic_connector_typ1
        // TODO: add other connectors
        else -> 0
    }