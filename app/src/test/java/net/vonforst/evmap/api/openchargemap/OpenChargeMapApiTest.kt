package net.vonforst.evmap.api.openchargemap

import kotlinx.coroutines.runBlocking
import net.vonforst.evmap.notFoundResponse
import net.vonforst.evmap.okResponse
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenChargeMapApiTest {
    val api: OpenChargeMapApi
    val webServer = MockWebServer()

    init {
        webServer.start()

        val apikey = ""
        val baseurl = webServer.url("/ocm/").toString()
        api = OpenChargeMapApi.create(apikey, baseurl)

        webServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val segments = request.requestUrl!!.pathSegments
                val urlHead = segments.subList(0, 2).joinToString("/")
                when (urlHead) {
                    "ocm/poi" -> {
                        val id = request.requestUrl!!.queryParameter("chargepointid")
                        val compact = request.requestUrl!!.queryParameter("compact") == "true"
                        if (id != null) {
                            return okResponse(
                                if (compact) {
                                    "/openchargemap/${id}_compact.json"
                                } else {
                                    "/openchargemap/$id.json"
                                }
                            )
                        } else {
                            val boundingBox = request.requestUrl!!.queryParameter("boundingbox")
                            assertEquals(boundingBox, "(54.0,9.0),(54.1,9.1)")
                            return okResponse(
                                if (compact) {
                                    "/openchargemap/list_compact.json"
                                } else {
                                    "/openchargemap/list.json"
                                }
                            )
                        }
                    }
                    else -> return notFoundResponse
                }
            }
        }
    }

    @Test
    fun testLoadChargepointDetail() {
        val response = runBlocking { api.getChargepointDetail(175585, compact = false) }
        assertTrue(response.isSuccessful)
        val body = response.body()!!
        assertEquals(1, body.size)
        val charger = body[0]
        assertEquals(175585, charger.id)
    }

    @Test
    fun testLoadChargepointDetailCompact() {
        val response = runBlocking { api.getChargepointDetail(175585, compact = true) }
        assertTrue(response.isSuccessful)
        val body = response.body()!!
        assertEquals(1, body.size)
        val charger = body[0]
        assertEquals(175585, charger.id)
    }

    @Test
    fun testLoadChargepointList() {
        val response = runBlocking {
            api.getChargepoints(OCMBoundingBox(54.0, 9.0, 54.1, 9.1), compact = false)
        }
        assertTrue(response.isSuccessful)
        val body = response.body()!!
        assertEquals(4, body.size)
        val charger = body[0]
        assertEquals(102167, charger.id)
    }

    @Test
    fun testLoadChargepointListCompact() {
        val response = runBlocking {
            api.getChargepoints(OCMBoundingBox(54.0, 9.0, 54.1, 9.1), compact = true)
        }
        assertTrue(response.isSuccessful)
        val body = response.body()!!
        assertEquals(4, body.size)
        val charger = body[0]
        assertEquals(102167, charger.id)
    }
}