package net.vonforst.evmap.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.ProductType
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.adapter.Equatable

class DonateViewModel(application: Application) : AndroidViewModel(application),
    PurchasesUpdatedListener {
    private var billingClient = BillingClient.newBuilder(application)
        .setListener(this)
        .enablePendingPurchases()
        .build()
    
    val products: MutableLiveData<Resource<List<DonationItem>>> by lazy {
        MutableLiveData<Resource<List<DonationItem>>>().apply {
            value = Resource.loading(null)
        }
    }

    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
            }

            override fun onBillingSetupFinished(p0: BillingResult) {
                loadProducts()

                // consume pending purchases
                billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(ProductType.INAPP)
                        .build()
                ) { _, purchasesList ->
                    purchasesList.forEach {
                        if (!it.isAcknowledged) {
                            consumePurchase(it.purchaseToken, false)
                        }
                    }
                }
            }

        })
    }

    private fun loadProducts() {
        val productIds = listOf(
            "donate_1_eur", "donate_2_eur", "donate_5_eur", "donate_10_eur"
        ) + if (BuildConfig.DEBUG) {
            listOf(
                "android.test.purchased", "android.test.canceled",
                "android.test.item_unavailable"
            )
        } else {
            emptyList()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productIds.map {
                QueryProductDetailsParams.Product.newBuilder().setProductType(ProductType.INAPP)
                    .setProductId(it).build()
            })
            .build()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                products.postValue(Resource.success(details
                    .sortedBy { it.oneTimePurchaseOfferDetails!!.priceAmountMicros }
                    .map { DonationItem(it) }
                ))
            } else {
                products.postValue(Resource.error(result.debugMessage, null))
            }
        }
    }

    val purchaseSuccessful = SingleLiveEvent<Nothing>()
    val purchaseFailed = SingleLiveEvent<Nothing>()

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                val purchaseToken = purchase.purchaseToken
                consumePurchase(purchaseToken)
            }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle an error caused by a user cancelling the purchase flow.
        } else {
            purchaseFailed.call()
        }
    }

    private fun consumePurchase(purchaseToken: String, showSuccess: Boolean = true) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        billingClient.consumeAsync(params) { _, _ ->
            if (showSuccess) purchaseSuccessful.call()
        }
    }

    fun startPurchase(it: DonationItem, activity: Activity) {
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(it.product)
                        .build()
                )
            )
            .build()
        val response = billingClient.launchBillingFlow(activity, flowParams)
        if (response.responseCode != BillingClient.BillingResponseCode.OK) {
            purchaseFailed.call()
        }
    }

    override fun onCleared() {
        billingClient.endConnection()
    }
}

data class DonationItem(val product: ProductDetails) : Equatable