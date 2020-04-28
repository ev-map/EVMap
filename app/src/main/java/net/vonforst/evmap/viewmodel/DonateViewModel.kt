package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.*

class DonateViewModel(application: Application) : AndroidViewModel(application),
    PurchasesUpdatedListener {
    private var billingClient = BillingClient.newBuilder(application)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    val products: MutableLiveData<List<SkuDetails>> by lazy {
        MutableLiveData<List<SkuDetails>>().apply {
            value = null

            val params = SkuDetailsParams.newBuilder().setType(BillingClient.SkuType.INAPP).build()
            billingClient.querySkuDetailsAsync(params) { result, details ->
                value = details
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        TODO("Not yet implemented")
    }
}