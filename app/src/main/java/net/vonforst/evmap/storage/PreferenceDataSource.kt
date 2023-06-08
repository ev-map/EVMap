package net.vonforst.evmap.storage

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import androidx.preference.PreferenceManager
import com.car2go.maps.AnyMap
import com.car2go.maps.model.LatLng
import net.vonforst.evmap.R
import net.vonforst.evmap.model.FILTERS_CUSTOM
import net.vonforst.evmap.model.FILTERS_DISABLED
import java.time.Instant

class PreferenceDataSource(val context: Context) {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)

    var dataSource: String
        get() = sp.getString("data_source", "goingelectric")!!
        set(value) {
            sp.edit().putString("data_source", value).apply()
        }

    var dataSourceSet: Boolean
        get() = sp.getBoolean("data_source_set", false)
        set(value) {
            sp.edit().putBoolean("data_source_set", value).apply()
        }

    var navigateUseMaps: Boolean
        get() = sp.getBoolean("navigate_use_maps", true)
        set(value) {
            sp.edit().putBoolean("navigate_use_maps", value).apply()
        }

    var mapRotateGesturesEnabled: Boolean
        get() = sp.getBoolean("map_rotate_gestures_enabled", true)
        set(value) {
            sp.edit().putBoolean("map_rotate_gestures_enabled", value).apply()
        }

    var lastGeReferenceDataUpdate: Instant
        get() = Instant.ofEpochMilli(sp.getLong("last_ge_reference_data_update", 0L))
        set(value) {
            sp.edit().putLong("last_ge_reference_data_update", value.toEpochMilli()).apply()
        }

    var lastOcmReferenceDataUpdate: Instant
        get() = Instant.ofEpochMilli(sp.getLong("last_ocm_reference_data_update", 0L))
        set(value) {
            sp.edit().putLong("last_ocm_reference_data_update", value.toEpochMilli()).apply()
        }

    /**
     * Stores the current filtering status, which is either the ID of a filter profile or
     * one of FILTERS_DISABLED, FILTERS_CUSTOM
     */
    var filterStatus: Long
        get() =
            sp.getLong(
                "filter_status",
                // migration from versions before filter profiles were implemented
                if (sp.getBoolean("filters_active", true))
                    FILTERS_CUSTOM else FILTERS_DISABLED
            )
        set(value) {
            sp.edit().putLong("filter_status", value).apply()
        }

    /**
     * Stores the last filter profile which was selected
     * (excluding FILTERS_DISABLED, but including FILTERS_CUSTOM)
     */
    var lastFilterProfile: Long
        get() = sp.getLong("last_filter_profile", FILTERS_CUSTOM)
        set(value) {
            sp.edit().putLong("last_filter_profile", value).apply()
        }


    /**
     * Sets app language. Will be removed and set to null with the next update because storage is
     * handled by AppCompat.
     */
    var language: String?
        get() = sp.getString("language", null)
        set(lang) {
            sp.edit().putString("language", lang).apply()
        }

    val darkmode: String
        get() = sp.getString("darkmode", "default")!!

    val mapProvider: String
        get() = sp.getString(
            "map_provider",
            context.getString(R.string.pref_map_provider_default)
        )!!

    var searchProvider: String
        get() = sp.getString(
            "search_provider",
            context.getString(R.string.pref_search_provider_default)
        )!!
        set(value) {
            sp.edit().putString("search_provider", value).apply()
        }

    var mapType: AnyMap.Type
        get() = AnyMap.Type.valueOf(sp.getString("map_type", null) ?: AnyMap.Type.NORMAL.toString())
        set(type) {
            sp.edit().putString("map_type", type.toString()).apply()
        }

    var mapTrafficEnabled: Boolean
        get() = sp.getBoolean("map_traffic_enabled", false)
        set(value) {
            sp.edit().putBoolean("map_traffic_enabled", value).apply()
        }

    var welcomeDialogShown: Boolean
        get() = sp.getBoolean("welcome_dialog_shown", false)
        set(value) {
            sp.edit().putBoolean("welcome_dialog_shown", value).apply()
        }

    var update060AndroidAutoDialogShown: Boolean
        get() = sp.getBoolean("update_0.6.0_androidauto_dialog_shown", false)
        set(value) {
            sp.edit().putBoolean("update_0.6.0_androidauto_dialog_shown", value).apply()
        }

    var chargepriceMyVehicles: Set<String>
        get() = try {
            sp.getStringSet("chargeprice_my_vehicle", emptySet())!!
        } catch (e: ClassCastException) {
            // backwards compatibility
            sp.getString("chargeprice_my_vehicle", null)?.let { setOf(it) } ?: emptySet()
        }
        set(value) {
            sp.edit().putStringSet("chargeprice_my_vehicle", value).apply()
        }

    var chargepriceLastSelectedVehicle: String?
        get() = sp.getString("chargeprice_last_vehicle", null)
        set(value) {
            sp.edit().putString("chargeprice_last_vehicle", value).apply()
        }

    var chargepriceMyTariffs: Set<String>?
        get() = sp.getStringSet("chargeprice_my_tariffs", null)
        set(value) {
            sp.edit().putStringSet("chargeprice_my_tariffs", value).apply()
        }

    var chargepriceMyTariffsAll: Boolean
        get() = sp.getBoolean("chargeprice_my_tariffs_all", true)
        set(value) {
            sp.edit().putBoolean("chargeprice_my_tariffs_all", value).apply()
        }

    var chargepriceNoBaseFee: Boolean
        get() = sp.getBoolean("chargeprice_no_base_fee", false)
        set(value) {
            sp.edit().putBoolean("chargeprice_no_base_fee", value).apply()
        }

    var chargepriceShowProviderCustomerTariffs: Boolean
        get() = sp.getBoolean("chargeprice_show_provider_customer_tariffs", false)
        set(value) {
            sp.edit().putBoolean("chargeprice_show_provider_customer_tariffs", value).apply()
        }

    var chargepriceAllowUnbalancedLoad: Boolean
        get() = sp.getBoolean("chargeprice_allow_unbalanced_load", false)
        set(value) {
            sp.edit().putBoolean("chargeprice_allow_unbalanced_load", value).apply()
        }

    var chargepriceCurrency: String
        get() = sp.getString("chargeprice_currency", null) ?: "EUR"
        set(value) {
            sp.edit().putString("chargeprice_currency", value).apply()
        }

    var chargepriceBatteryRange: List<Float>
        get() = listOf(
            sp.getFloat("chargeprice_battery_range_min", 20f),
            sp.getFloat("chargeprice_battery_range_max", 80f),
        )
        set(value) {
            sp.edit().putFloat("chargeprice_battery_range_min", value[0])
                .putFloat("chargeprice_battery_range_max", value[1])
                .apply()
        }

    var chargepriceBatteryRangeAndroidAuto: List<Float>
        get() = listOf(
            sp.getFloat("chargeprice_battery_range_android_auto_min", 20f),
            sp.getFloat("chargeprice_battery_range_android_auto_max", 80f),
        )
        set(value) {
            sp.edit().putFloat("chargeprice_battery_range_android_auto_min", value[0])
                .putFloat("chargeprice_battery_range_android_auto_max", value[1])
                .apply()
        }

    /** App start counter, introduced with Version 1.0.0 */
    var appStartCounter: Long
        get() = sp.getLong("app_start_counter", 0)
        set(value) {
            sp.edit().putLong("app_start_counter", value).apply()
        }

    /** Counter for how many times the price comparison page was opened,
     * introduced with Version 1.3.4 **/
    var chargepriceCounter: Long
        get() = sp.getLong("chargeprice_counter", 0)
        set(value) {
            sp.edit().putLong("chargeprice_counter", value).apply()
        }

    var opensourceDonationsDialogShown: Boolean
        get() = sp.getBoolean("opensource_donations_dialog_shown", false)
        set(value) {
            sp.edit().putBoolean("opensource_donations_dialog_shown", value).apply()
        }

    var placeSearchResultAndroidAuto: LatLng?
        get() = sp.getLatLng("place_search_result_android_auto")
        set(value) {
            sp.edit().putLatLng("place_search_result_android_auto", value).apply()
        }

    var placeSearchResultAndroidAutoName: String?
        get() = sp.getString("place_search_result_android_auto_name", null)
        set(value) {
            sp.edit().putString("place_search_result_android_auto_name", value).apply()
        }

    var showChargersAheadAndroidAuto: Boolean
        get() = sp.getBoolean("show_chargers_ahead_android_auto", false)
        set(value) {
            sp.edit().putBoolean("show_chargers_ahead_android_auto", value).apply()
        }

    val predictionEnabled: Boolean
        get() = sp.getBoolean("prediction_enabled", true)

    var developerModeEnabled: Boolean
        get() = sp.getBoolean("dev_mode_enabled", false)
        set(value) {
            sp.edit().putBoolean("dev_mode_enabled", value).apply()
        }

    val mapScale: String
        get() = sp.getString("map_scale", null) ?: "both"

    var currentMapLocation: LatLng
        get() = sp.getLatLng("current_map_location") ?: LatLng(50.113388, 9.252536)
        set(value) {
            sp.edit().putLatLng("current_map_location", value).apply()
        }

    var currentMapZoom: Float
        get() = sp.getFloat("current_map_zoom", 3.5f)
        set(value) {
            sp.edit().putFloat("current_map_zoom", value).apply()
        }
}

fun SharedPreferences.getLatLng(key: String): LatLng? =
    if (contains("${key}_lat") && contains("${key}_lng")) {
        LatLng(
            Double.fromBits(getLong("${key}_lat", 0L)),
            Double.fromBits(getLong("${key}_lng", 0L))
        )
    } else null

fun Editor.putLatLng(key: String, value: LatLng?): Editor {
    if (value == null) {
        remove("${key}_lat")
        remove("${key}_lng")
    } else {
        putLong("${key}_lat", value.latitude.toBits())
        putLong("${key}_lng", value.longitude.toBits())
    }
    return this
}
