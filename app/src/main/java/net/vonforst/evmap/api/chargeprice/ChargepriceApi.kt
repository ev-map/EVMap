package net.vonforst.evmap.api.chargeprice

import net.vonforst.evmap.model.ChargeLocation
import java.util.Locale

interface ChargepriceApi {

    companion object {
        val supportedLanguages = setOf("de", "en", "fr", "nl")

        private val DATA_SOURCE_GOINGELECTRIC = "going_electric"
        private val DATA_SOURCE_OPENCHARGEMAP = "open_charge_map"

        fun getChargepriceLanguage(): String {
            val locale = Locale.getDefault().language
            return if (supportedLanguages.contains(locale)) {
                locale
            } else {
                "en"
            }
        }

        fun getPoiUrl(charger: ChargeLocation) =
            "https://www.chargeprice.app/?poi_id=${charger.id}&poi_source=${getDataAdapter(charger)}"

        fun getDataAdapter(charger: ChargeLocation) = when (charger.dataSource) {
            "goingelectric" -> DATA_SOURCE_GOINGELECTRIC
            "openchargemap" -> DATA_SOURCE_OPENCHARGEMAP
            else -> throw IllegalArgumentException()
        }

        /**
         * Checks if a charger is supported by Chargeprice.
         *
         * This function just applies some heuristics on the charger's data without making API
         * calls. If it returns true, that is not a guarantee that Chargeprice will have information
         * on this charger. But if it is false, it is pretty unlikely that Chargeprice will have
         * useful data, so we do not show the price comparison button in this case.
         */
        @JvmStatic
        fun isChargerSupported(charger: ChargeLocation): Boolean {
            val dataSourceSupported = charger.dataSource in listOf("goingelectric", "openchargemap")
            val countrySupported =
                charger.chargepriceData?.country?.let { isCountrySupported(it, charger.dataSource) }
                    ?: false
            val networkSupported = charger.chargepriceData?.network?.let {
                when (charger.dataSource) {
                    "openchargemap" -> it !in listOf(
                        "1", // unknown operator
                        "44", // private residence/individual
                        "45",  // business owner at location
                        "23", "3534" // Tesla
                    )

                    "goingelectric" -> it != "Tesla Supercharger"
                    else -> true
                }
            } ?: false
            val powerAvailable = charger.chargepoints.all { it.hasKnownPower() }
            return dataSourceSupported && countrySupported && networkSupported && powerAvailable
        }

        private fun isCountrySupported(country: String, dataSource: String): Boolean =
            when (dataSource) {
                "goingelectric" -> country in listOf(
                    // list of countries according to Chargeprice.app, 2021/08/24
                    "Deutschland",
                    "Österreich",
                    "Schweiz",
                    "Frankreich",
                    "Belgien",
                    "Niederlande",
                    "Luxemburg",
                    "Dänemark",
                "Norwegen",
                "Schweden",
                "Slowenien",
                "Kroatien",
                "Ungarn",
                "Tschechien",
                "Italien",
                "Spanien",
                "Großbritannien",
                "Irland",
                // additional countries found 2022/09/17, https://github.com/ev-map/EVMap/issues/234
                "Finnland",
                "Lettland",
                "Litauen",
                "Estland",
                "Liechtenstein",
                "Rumänien",
                "Slowakei",
                "Slowenien",
                "Polen",
                "Serbien",
                "Bulgarien",
                "Kosovo",
                "Montenegro",
                "Albanien",
                "Griechenland",
                "Portugal",
                "Island"
            )
            "openchargemap" -> country in listOf(
                // list of countries according to Chargeprice.app, 2021/08/24
                "DE",
                "AT",
                "CH",
                "FR",
                "BE",
                "NE",
                "LU",
                "DK",
                "NO",
                "SE",
                "SI",
                "HR",
                "HU",
                "CZ",
                "IT",
                "ES",
                "GB",
                "IE",
                // additional countries found 2022/09/17, https://github.com/ev-map/EVMap/issues/234
                "FI",
                "LV",
                "LT",
                "EE",
                "LI",
                "RO",
                "SK",
                "SI",
                "PL",
                "RS",
                "BG",
                "XK",
                "ME",
                "AL",
                "GR",
                "PT",
                "IS"
            )
            else -> false
        }
    }
}
