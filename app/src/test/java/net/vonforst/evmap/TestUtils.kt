package net.vonforst.evmap

import net.vonforst.evmap.api.goingelectric.GoingElectricApiTest
import okhttp3.mockwebserver.MockResponse
import java.net.HttpURLConnection

val notFoundResponse: MockResponse =
    MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)

fun okResponse(file: String): MockResponse {
    val body = readResource(file) ?: return notFoundResponse
    return MockResponse().setResponseCode(HttpURLConnection.HTTP_OK).setBody(body)
}

private fun readResource(s: String) =
    GoingElectricApiTest::class.java.getResource(s)?.readText()