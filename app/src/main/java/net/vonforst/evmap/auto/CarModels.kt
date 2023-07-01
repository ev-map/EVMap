package net.vonforst.evmap.auto

/**
 * This file lists known mappings between the vehicle model provided by Android Auto's CarInfo API
 * and human-readable vehicle models as listed by Chargeprice in their vehicle database.
 */

private val brands = mapOf(
    "Saic" to "MG",  // Seen on MG 4
    "Google" to "Hyundai"  // useful for debugging on the DHU. Delete in case there's ever a Google car ;)
)

private val models = mapOf(
    "Audi" to mapOf(
        "516 (G4x)" to "e-tron"
    ),
    "Renault" to mapOf(
        "BCB" to "Megane E-Tech"
    )
)

fun getVehicleModel(manufacturer: String?, model: String?) =
    if (manufacturer != null && model != null) {
        models[manufacturer]?.get(model) ?: model
    } else {
        null
    }

fun getVehicleBrand(manufacturer: String?) =
    if (manufacturer != null) {
        brands[manufacturer] ?: manufacturer
    } else {
        null
    }