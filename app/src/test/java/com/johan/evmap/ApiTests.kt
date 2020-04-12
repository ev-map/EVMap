package com.johan.evmap

import com.johan.evmap.api.availability.availabilityDetectors
import com.johan.evmap.api.goingelectric.ChargeLocation
import com.johan.evmap.api.goingelectric.GoingElectricApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ApiTests {
    val api: GoingElectricApi

    init {
        val apikey = System.getenv("GOINGELECTRIC_API_KEY")
            ?: throw IllegalArgumentException("please provide a GoingElectric.de API key in the GOINGELECTRIC_API_KEY environment variable.")
        api = GoingElectricApi.create(apikey)
    }

    @ExperimentalCoroutinesApi
    @Test
    fun apiTest() {
        val charger = api.getChargepointDetail(2105)
            .execute().body()!!
            .chargelocations[0] as ChargeLocation
        print(charger)

        runBlocking {
            val result = availabilityDetectors[0].getAvailability(charger)
            print(result)
        }
    }
}
