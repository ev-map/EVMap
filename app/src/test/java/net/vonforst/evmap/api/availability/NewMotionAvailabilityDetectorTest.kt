package net.vonforst.evmap.api.availability

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.api.goingelectric.GoingElectricApi
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
                            "9.47108/9.67108/54.4116/54.6116" -> 2105
                            "9.444284/9.644283999999999/54.376699/54.576699000000005" -> 18284
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
            val charger = api.getChargepointDetail(chargepoint)
                .execute().body()!!
                .chargelocations[0] as ChargeLocation
            println(charger)

            runBlocking {
                val result = newMotion.getAvailability(charger)
                println(result)
            }
        }
    }
}
