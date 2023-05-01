package net.vonforst.evmap.api.availability

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import net.vonforst.evmap.api.goingelectric.GoingElectricApi
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.okResponse
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import java.net.HttpURLConnection

class NewMotionAvailabilityDetectorTest {
    val api: GoingElectricApi
    val webServer = MockWebServer()
    val newMotion: NewMotionAvailabilityDetector

    init {
        webServer.start()

        val apikey = ""
        val baseurl = webServer.url("/ge/").toString()
        api = GoingElectricApi.create(apikey, baseurl)
        newMotion = NewMotionAvailabilityDetector(
            OkHttpClient.Builder().build(),
            webServer.url("/nm/").toString()
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
                    "nm/markers" -> {
                        val urlTail = segments.subList(2, segments.size).joinToString("/")
                        val id = when (urlTail) {
                            "9.56608/9.576080000000001/54.5066/54.516600000000004/22" -> 2105
                            "9.539283999999999/9.549284/54.471699/54.481699000000006/22" -> 18284
                            else -> -1
                        }
                        return okResponse("/newmotion/$id/markers.json")
                    }
                    "nm/locations" -> {
                        val id = segments.last()
                        return okResponse("/newmotion/$id.json")
                    }
                    else -> return notFoundResponse
                }
            }
        }
    }

    private fun readResource(s: String) =
        NewMotionAvailabilityDetectorTest::class.java.getResource(s)?.readText()

    @ExperimentalCoroutinesApi
    @Test
    fun apiTest() {
        for (chargepoint in listOf(2105L, 18284L)) {
            val charger = runBlocking { api.getChargepointDetail(chargepoint).body()!! }
                .chargelocations[0].convert("", true) as ChargeLocation
            println(charger)

            runBlocking {
                val result = newMotion.getAvailability(charger)
                println(result)
            }
        }
    }
}
