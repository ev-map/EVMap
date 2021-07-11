package net.vonforst.evmap.api

import androidx.annotation.DrawableRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import net.vonforst.evmap.R
import net.vonforst.evmap.model.Chargepoint
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

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
    Chargepoint.TYPE_2_UNKNOWN to R.string.plug_type_2,
    Chargepoint.TYPE_2_PLUG to R.string.plug_type_2,
    Chargepoint.TYPE_2_SOCKET to R.string.plug_type_2,
    Chargepoint.TYPE_3 to R.string.plug_type_3,
    Chargepoint.CCS_UNKNOWN to R.string.plug_ccs,
    Chargepoint.CCS_TYPE_1 to R.string.plug_ccs,
    Chargepoint.CCS_TYPE_2 to R.string.plug_ccs,
    Chargepoint.SCHUKO to R.string.plug_schuko,
    Chargepoint.CHADEMO to R.string.plug_chademo,
    Chargepoint.SUPERCHARGER to R.string.plug_supercharger,
    Chargepoint.CEE_BLAU to R.string.plug_cee_blau,
    Chargepoint.CEE_ROT to R.string.plug_cee_rot,
    Chargepoint.TESLA_ROADSTER_HPC to R.string.plug_roadster_hpc
)

fun nameForPlugType(ctx: StringProvider, type: String): String =
    plugNames[type]?.let {
        ctx.getString(it)
    } ?: type

fun equivalentPlugTypes(type: String): Set<String> {
    return when (type) {
        Chargepoint.CCS_TYPE_1 -> setOf(Chargepoint.CCS_UNKNOWN, Chargepoint.CCS_TYPE_1)
        Chargepoint.CCS_TYPE_2 -> setOf(Chargepoint.CCS_UNKNOWN, Chargepoint.CCS_TYPE_2)
        Chargepoint.CCS_UNKNOWN -> setOf(
            Chargepoint.CCS_UNKNOWN,
            Chargepoint.CCS_TYPE_1,
            Chargepoint.CCS_TYPE_2
        )
        Chargepoint.TYPE_2_PLUG -> setOf(Chargepoint.TYPE_2_UNKNOWN, Chargepoint.TYPE_2_PLUG)
        Chargepoint.TYPE_2_SOCKET -> setOf(Chargepoint.TYPE_2_UNKNOWN, Chargepoint.TYPE_2_SOCKET)
        Chargepoint.TYPE_2_UNKNOWN -> setOf(
            Chargepoint.TYPE_2_UNKNOWN,
            Chargepoint.TYPE_2_PLUG,
            Chargepoint.TYPE_2_SOCKET
        )
        else -> setOf(type)
    }
}

@DrawableRes
fun iconForPlugType(type: String): Int =
    when (type) {
        Chargepoint.CCS_TYPE_2 -> R.drawable.ic_connector_ccs_typ2
        Chargepoint.CCS_UNKNOWN -> R.drawable.ic_connector_ccs_typ2
        Chargepoint.CCS_TYPE_1 -> R.drawable.ic_connector_ccs_typ1
        Chargepoint.CHADEMO -> R.drawable.ic_connector_chademo
        Chargepoint.SCHUKO -> R.drawable.ic_connector_schuko
        Chargepoint.SUPERCHARGER -> R.drawable.ic_connector_supercharger
        Chargepoint.TYPE_2_UNKNOWN -> R.drawable.ic_connector_typ2
        Chargepoint.TYPE_2_SOCKET -> R.drawable.ic_connector_typ2
        Chargepoint.TYPE_2_PLUG -> R.drawable.ic_connector_typ2
        Chargepoint.CEE_BLAU -> R.drawable.ic_connector_cee_blau
        Chargepoint.CEE_ROT -> R.drawable.ic_connector_cee_rot
        Chargepoint.TYPE_1 -> R.drawable.ic_connector_typ1
        // TODO: add other connectors
        else -> 0
    }

val powerSteps = listOf(0, 2, 3, 7, 11, 22, 43, 50, 75, 100, 150, 200, 250, 300, 350)
fun mapPower(i: Int) = powerSteps[i]
fun mapPowerInverse(power: Int) = powerSteps
    .mapIndexed { index, v -> abs(v - power) to index }
    .minByOrNull { it.first }?.second ?: 0