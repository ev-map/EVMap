package net.vonforst.evmap.api.fronyx

import kotlinx.coroutines.runBlocking
import net.vonforst.evmap.okResponse
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.HttpURLConnection
import java.time.ZoneOffset
import java.time.ZonedDateTime

class FronyxApiTest {
    val webServer = MockWebServer()
    val fronyx: FronyxApi

    init {
        webServer.start()

        val apikey = ""
        fronyx = FronyxApi(
            apikey,
            webServer.url("/").toString()
        )

        webServer.dispatcher = object : Dispatcher() {
            val notFoundResponse = MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)

            override fun dispatch(request: RecordedRequest): MockResponse {
                val segments = request.requestUrl!!.pathSegments
                val urlHead = segments.subList(0, 2).joinToString("/")
                when (urlHead) {
                    "predictions/evse-id" -> {
                        val id = segments[2]
                        return okResponse("/fronyx/${id.replace("*", "_")}.json")
                    }
                    "predictions/evses" -> {
                        val ids = request.requestUrl!!.queryParameter("evseIds")!!.split(",")
                        return okResponse(
                            "/fronyx/${
                                ids.map { it.replace("*", "_") }.joinToString(",")
                            }.json"
                        )
                    }
                    else -> return notFoundResponse
                }
            }
        }
    }

    @Test
    fun apiTestSingle() {
        val evseId = "DE*ION*E202102"

        runBlocking {
            val result = fronyx.getPredictionsForEvseId(evseId)
            assertEquals(result.evseId, evseId)
            assertEquals(25, result.predictions.size)
            assertEquals(
                ZonedDateTime.of(2022, 9, 18, 13, 45, 0, 0, ZoneOffset.UTC),
                result.predictions[0].timestamp
            )
            assertEquals(FronyxStatus.AVAILABLE, result.predictions[0].status)
        }
    }

    @Test
    fun apiTestMultiple() {
        val evseIds = listOf("DE*ION*E202101", "DE*ION*E202102")

        runBlocking {
            val results = fronyx.getPredictionsForEvseIds(evseIds)
            results.forEachIndexed { i, result ->
                assertEquals(result.evseId, evseIds[i])
                assertEquals(25, result.predictions.size)
                assertEquals(
                    ZonedDateTime.of(2022, 11, 16, 18, 0, 0, 0, ZoneOffset.UTC),
                    result.predictions[0].timestamp
                )
                assertEquals(
                    if (i == 0) FronyxStatus.UNAVAILABLE else FronyxStatus.AVAILABLE,
                    result.predictions[0].status
                )
            }
        }
    }
}
