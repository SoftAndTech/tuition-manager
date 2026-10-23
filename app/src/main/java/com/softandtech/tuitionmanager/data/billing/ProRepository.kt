package com.softandtech.tuitionmanager.data.billing

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class BillingStatus { CONNECTING, READY, UNAVAILABLE }

/**
 * Pro is a one-time in-app product bought through Google Play. Play Billing talks to the Play
 * Store app on the phone, so this works without the app having internet access.
 *
 * The unlocked state is cached so Pro keeps working offline; it's re-checked with Play on each
 * launch, and turned off again only if Play positively reports no purchase (e.g. after a refund).
 * There is no server, so purchases aren't verified server-side — an accepted trade-off for an
 * app that never goes online.
 */
@Singleton
class ProRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val isPro: Flow<Boolean> = dataStore.data.map { it[PRO] ?: false }

    private val _status = MutableStateFlow(BillingStatus.CONNECTING)
    val status: StateFlow<BillingStatus> = _status.asStateFlow()

    private val _product = MutableStateFlow<ProductDetails?>(null)
    /** Pro product with its local price (e.g. "₹399"); null until Play returns it. */
    val product: StateFlow<ProductDetails?> = _product.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases.orEmpty()) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** Connects to Play, refreshes ownership and loads the price. Safe to call repeatedly. */
    fun start() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _status.value = BillingStatus.READY
                    refresh()
                } else {
                    // No Play Store (some emulators, de-Googled phones): keep the cached state
                    _status.value = BillingStatus.UNAVAILABLE
                }
            }

            override fun onBillingServiceDisconnected() {
                _status.value = BillingStatus.CONNECTING
            }
        })
    }

    /** Re-checks purchases with Play — used by "Restore purchase" too. */
    fun refresh() {
        if (!client.isReady) return start()
        val productParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(productParams) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _product.value = details.productDetailsList.firstOrNull()
            }
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.any { it.isProPurchased() }
                scope.launch { setPro(owned) }
                handle(purchases)
            }
        }
    }

    /** Opens Google Play's purchase sheet. Returns false if the product isn't available yet. */
    fun launchPurchase(activity: Activity): Boolean {
        val details = _product.value ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()))
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun handle(purchases: List<Purchase>) {
        purchases.filter { it.isProPurchased() }.forEach { purchase ->
            scope.launch { setPro(true) }
            // Play refunds purchases that aren't acknowledged within 3 days
            if (!purchase.isAcknowledged) {
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) {}
            }
        }
    }

    private fun Purchase.isProPurchased() =
        PRO_PRODUCT_ID in products && purchaseState == Purchase.PurchaseState.PURCHASED

    /** Also used by the debug-build switch on the Pro screen, to test free limits without a real purchase. */
    suspend fun setPro(enabled: Boolean) {
        dataStore.edit { it[PRO] = enabled }
    }

    companion object {
        /** Must match the in-app product ID created in Play Console. */
        const val PRO_PRODUCT_ID = "pro_unlock"
        private val PRO = booleanPreferencesKey("pro_unlocked")
    }
}
