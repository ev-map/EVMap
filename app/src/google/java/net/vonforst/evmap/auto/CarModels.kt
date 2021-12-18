package net.vonforst.evmap.auto

/**
 * This file lists known mappings between the vehicle model provided by Android Auto's CarInfo API
 * and human-readable vehicle models as listed by Chargeprice in their vehicle database.
 */

private val models = mapOf(
    "Audi" to mapOf(
        "516 (G4x)" to "e-tron"
    )
)

fun getVehicleModel(manufacturer: String?, model: String?) =
    if (manufacturer != null && model != null) {
        models[manufacturer]?.get(model) ?: model
    } else {
        null
    }