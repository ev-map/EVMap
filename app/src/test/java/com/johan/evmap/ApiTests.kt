package com.johan.evmap

import com.johan.evmap.api.ChargeLocation
import com.johan.evmap.api.GoingElectricApi
import org.junit.Test

class ApiTests {
    val api: GoingElectricApi

    init {
        val apikey = System.getenv("GOINGELECTRIC_API_KEY")
            ?: throw IllegalArgumentException("please provide a GoingElectric.de API key in the GOINGELECTRIC_API_KEY environment variable.")
        api = GoingElectricApi.create(apikey)
    }

    @Test
    fun apiTest() {
        val charger = api.getChargepointDetail(2105)
            .execute().body()!!
            .chargelocations[0] as ChargeLocation
        print(charger)
    }
}
