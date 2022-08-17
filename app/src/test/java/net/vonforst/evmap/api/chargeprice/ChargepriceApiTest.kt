package net.vonforst.evmap.api.chargeprice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import net.vonforst.evmap.api.goingelectric.GoingElectricApi
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.okResponse
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.HttpURLConnection

class ChargepriceApiTest {
    val ge: GoingElectricApi
    val webServer = MockWebServer()
    val chargeprice: ChargepriceApi

    init {
        webServer.start()

        val apikey = ""
        val baseurl = webServer.url("/ge/").toString()
        ge = GoingElectricApi.create(apikey, baseurl)
        chargeprice = ChargepriceApi.create(
            apikey,
            webServer.url("/cp/").toString()
        )

        webServer.dispatcher = object : Dispatcher() {
            val notFoundResponse = MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)

            override fun dispatch(request: RecordedRequest): MockResponse {
                val segments = request.requestUrl!!.pathSegments
                val urlHead = segments.subList(0, 2).joinToString("/")
                when (urlHead) {
                    "ge/chargepoints" -> {
                        val id = request.requestUrl!!.queryParameter("ge_id")
                        return okResponse("/chargers/$id.json")
                    }
                    "cp/charge_prices" -> {
                        val body = request.body.readUtf8()
                        return okResponse("/chargeprice/2105.json")
                    }
                    else -> return notFoundResponse
                }
            }
        }
    }

    private fun readResource(s: String) =
        ChargepriceApiTest::class.java.getResource(s)?.readText()

    @ExperimentalCoroutinesApi
    @Test
    fun apiTest() {
        for (chargepoint in listOf(2105L, 18284L)) {
            val charger = runBlocking { ge.getChargepointDetail(chargepoint).body()!! }
                .chargelocations[0].convert("", true) as ChargeLocation
            println(charger)

            runBlocking {
                val result = chargeprice.getChargePrices(
                    ChargepriceRequest(
                        dataAdapter = "going_electric",
                        station =
                        ChargepriceStation.fromEvmap(charger, listOf("Typ2", "Schuko")),
                        options = ChargepriceOptions(energy = 22.0, duration = 60)
                    ), "en"
                )
                assertEquals(25, result.data!!.size)
            }
        }
    }
}
