package net.vonforst.evmap.api.goingelectric

import kotlinx.coroutines.runBlocking
import net.vonforst.evmap.notFoundResponse
import net.vonforst.evmap.okResponse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class GoingElectricApiTest {
    val api: GoingElectricApi
    val webServer = MockWebServer()

    init {
        webServer.start()

        val apikey = ""
        val baseurl = webServer.url("/ge/").toString()
        api = GoingElectricApi.create(apikey, baseurl)

        webServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val segments = request.requestUrl!!.pathSegments
                val urlHead = segments.subList(0, 2).joinToString("/")
                when (urlHead) {
                    "ge/chargepoints" -> {
                        val id = request.requestUrl!!.queryParameter("ge_id")
                        if (id != null) {
                            return okResponse("/chargers/$id.json")
                        } else {
                            val body = request.body.readUtf8()
                            val bodyQuery = "http://host?$body".toHttpUrl()

                            val freeparking =
                                bodyQuery.queryParameter("freeparking")!!.toBoolean()
                            val freecharging =
                                bodyQuery.queryParameter("freecharging")!!.toBoolean()
                            return if (freeparking && freecharging) {
                                okResponse("/chargers/list-empty.json")
                            } else if (freecharging) {
                                okResponse("/chargers/list.json")
                            } else {
                                okResponse("/chargers/list-startkey.json")
                            }
                        }
                    }
                    else -> return notFoundResponse
                }
            }
        }
    }

    @Test
    fun testLoadChargepointDetail() {
        val response = runBlocking { api.getChargepointDetail(2105) }
        assertTrue(response.isSuccessful)
        val body = response.body()!!
        assertEquals("ok", body.status)
        assertEquals(null, body.startkey)
        assertEquals(1, body.chargelocations.size)
        val charger = body.chargelocations[0] as GEChargeLocation
        assertEquals(2105, charger.id)
    }

    @Test
    fun testLoadChargepointDetail2() {
        val response = runBlocking { api.getChargepointDetail(34210) }
        assertTrue(response.isSuccessful)
        val body = response.body()!!
        assertEquals("ok", body.status)
        assertEquals(null, body.startkey)
        assertEquals(1, body.chargelocations.size)
        val charger = body.chargelocations[0] as GEChargeLocation
        assertEquals(34210, charger.id)
        assertEquals(LocalTime.MIN, charger.openinghours!!.days!!.monday.start)
        assertEquals(LocalTime.MAX, charger.openinghours!!.days!!.monday.end)
        assertEquals(LocalTime.MAX, charger.openinghours!!.days!!.tuesday.end)
    }

    @Test
    fun testLoadChargepointList() {
        val response = runBlocking {
            api.getChargepoints(1.0, 1.0, 2.0, 2.0, 11f, freecharging = true)
        }
        assertTrue(response.isSuccessful)
        val body = response.body()!!
        assertEquals("ok", body.status)
        assertEquals(null, body.startkey)
        assertEquals(2, body.chargelocations.size)
        val charger = body.chargelocations[0] as GEChargeLocation
        assertEquals(41161, charger.id)
    }

    @Test
    fun testLoadChargepointListEmpty() {
        val response = runBlocking {
            api.getChargepoints(1.0, 1.0, 2.0, 2.0, 11f, freeparking = true, freecharging = true)
        }
        assertTrue(response.isSuccessful)
        val body = response.body()!!
        assertEquals("ok", body.status)
        assertEquals(null, body.startkey)
        assertEquals(0, body.chargelocations.size)
    }

    @Test
    fun testLoadChargepointListStartkey() {
        val response = runBlocking {
            api.getChargepoints(1.0, 1.0, 2.0, 2.0, 1f)
        }
        assertTrue(response.isSuccessful)
        val body = response.body()!!
        assertEquals("ok", body.status)
        assertEquals(2, body.startkey)
        assertEquals(2, body.chargelocations.size)
        val charger = body.chargelocations[0] as GEChargeLocation
        assertEquals(41161, charger.id)
    }
}