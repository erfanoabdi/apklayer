package com.android.billingclient.api;

import android.app.Activity;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// com.android.billingclient.api.BillingClient -- the central Play Billing entry
// point. Apps build one with newBuilder(context).setListener(...).
// enablePendingPurchases().build(), startConnection(), then query products /
// launch purchase flows.
//
// Mac.apk runs as a de-Googled device: there is NO Play Store service to bind
// to, so this client never reaches the CONNECTED state. Every call resolves to
// an HONEST negative -- startConnection fires BILLING_UNAVAILABLE, isReady() is
// false, queries return empty lists, and flow / acknowledge / consume return
// BILLING_UNAVAILABLE. We NEVER fabricate an OK with a fake purchase,
// entitlement, or token. The full type ships so paid apps init-then-degrade
// instead of crashing with NoClassDefFound on the boot classpath.
//
// Authored against the published Play Billing reference
// (developer.android.com/reference/com/android/billingclient/api/BillingClient
// and its nested ProductType / FeatureType / ConnectionState / BillingResponseCode).
public abstract class BillingClient {

    // com.android.billingclient.api.BillingClient.BillingResponseCode
    public @interface BillingResponseCode {
        int SERVICE_TIMEOUT = -3;
        int FEATURE_NOT_SUPPORTED = -2;
        int SERVICE_DISCONNECTED = -1;
        int OK = 0;
        int USER_CANCELED = 1;
        int SERVICE_UNAVAILABLE = 2;
        int BILLING_UNAVAILABLE = 3;
        int ITEM_UNAVAILABLE = 4;
        int DEVELOPER_ERROR = 5;
        int ERROR = 6;
        int ITEM_ALREADY_OWNED = 7;
        int ITEM_NOT_OWNED = 8;
        int NETWORK_ERROR = 12;
    }

    // com.android.billingclient.api.BillingClient.ProductType
    public @interface ProductType {
        String INAPP = "inapp";
        String SUBS = "subs";
    }

    // com.android.billingclient.api.BillingClient.SkuType (legacy alias, pre-v5)
    public @interface SkuType {
        String INAPP = "inapp";
        String SUBS = "subs";
    }

    // com.android.billingclient.api.BillingClient.FeatureType
    public @interface FeatureType {
        String SUBSCRIPTIONS = "subscriptions";
        String SUBSCRIPTIONS_UPDATE = "subscriptionsUpdate";
        String PRICE_CHANGE_CONFIRMATION = "priceChangeConfirmation";
        String IN_APP_MESSAGING = "bbb";
        String PRODUCT_DETAILS = "fff";
        String BILLING_CONFIG = "ggg";
        String ALTERNATIVE_BILLING_ONLY = "jjj";
        String EXTERNAL_OFFER = "kkk";
        String INCLUDE_SUSPENDED_SUBSCRIPTIONS = "nnn";
    }

    // com.android.billingclient.api.BillingClient.ConnectionState
    public @interface ConnectionState {
        int DISCONNECTED = 0;
        int CONNECTING = 1;
        int CONNECTED = 2;
        int CLOSED = 3;
    }

    public static Builder newBuilder(Context context) {
        return new Builder(context);
    }

    // ---- Connection lifecycle ----
    public abstract void startConnection(BillingClientStateListener listener);
    public abstract void endConnection();
    public abstract boolean isReady();
    public abstract int getConnectionState();

    // ---- Feature support ----
    public abstract BillingResult isFeatureSupported(String feature);

    // ---- Queries (v5+) ----
    public abstract void queryProductDetailsAsync(
            QueryProductDetailsParams params, ProductDetailsResponseListener listener);
    public abstract void queryPurchasesAsync(
            QueryPurchasesParams params, PurchasesResponseListener listener);

    // ---- Queries (legacy, pre-v5) ----
    public abstract void querySkuDetailsAsync(
            SkuDetailsParams params, SkuDetailsResponseListener listener);
    public abstract void queryPurchaseHistoryAsync(
            String skuType, PurchaseHistoryResponseListener listener);

    // ---- Purchase flow / management ----
    public abstract BillingResult launchBillingFlow(Activity activity, BillingFlowParams params);
    public abstract void acknowledgePurchase(
            AcknowledgePurchaseParams params, AcknowledgePurchaseResponseListener listener);
    public abstract void consumeAsync(
            ConsumeParams consumeParams, ConsumeResponseListener listener);

    // ---- Config / messaging ----
    public abstract void getBillingConfigAsync(
            GetBillingConfigParams params, BillingConfigResponseListener listener);
    public abstract BillingResult showInAppMessages(
            Activity activity, InAppMessageParams params, InAppMessageResponseListener listener);

    // com.android.billingclient.api.BillingClient.Builder -- constructs the
    // client. Mirrors the v3..v7 builder surface (the no-arg and
    // PendingPurchasesParams overloads of enablePendingPurchases, plus the v7
    // enableAutoServiceReconnection).
    public static final class Builder {
        private final Context context;
        private PurchasesUpdatedListener listener;

        Builder(Context context) {
            this.context = context;
        }

        public Builder setListener(PurchasesUpdatedListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder enablePendingPurchases() {
            return this;
        }

        public Builder enablePendingPurchases(PendingPurchasesParams params) {
            return this;
        }

        public Builder enableAutoServiceReconnection() {
            return this;
        }

        public Builder enableAlternativeBillingOnly() {
            return this;
        }

        public Builder enableExternalOffer() {
            return this;
        }

        public BillingClient build() {
            return new MacApkBillingClient(context, listener);
        }
    }

    // MacApkBillingClient -- the de-Googled concrete implementation. Defined as
    // a package-private static nested class (NOT a separate public type) so it
    // never shadows a real Play Billing class. Every path is an honest negative;
    // nothing is fabricated.
    static final class MacApkBillingClient extends BillingClient {
        @SuppressWarnings("unused")
        private final Context context;
        @SuppressWarnings("unused")
        private final PurchasesUpdatedListener purchasesUpdatedListener;
        private volatile int connectionState = ConnectionState.DISCONNECTED;

        MacApkBillingClient(Context context, PurchasesUpdatedListener listener) {
            this.context = context;
            this.purchasesUpdatedListener = listener;
        }

        @Override
        public void startConnection(final BillingClientStateListener listener) {
            // Real Play Billing transitions CONNECTING then fires the callback
            // once the service binds. Here the bind can never succeed, so we
            // surface BILLING_UNAVAILABLE off-stack and settle to DISCONNECTED.
            connectionState = ConnectionState.CONNECTING;
            if (listener == null) {
                connectionState = ConnectionState.DISCONNECTED;
                return;
            }
            MacApkBilling.post(new Runnable() {
                @Override public void run() {
                    connectionState = ConnectionState.DISCONNECTED;
                    listener.onBillingSetupFinished(MacApkBilling.unavailable());
                }
            });
        }

        @Override
        public void endConnection() {
            connectionState = ConnectionState.CLOSED;
        }

        @Override
        public boolean isReady() {
            return false;
        }

        @Override
        public int getConnectionState() {
            return connectionState;
        }

        @Override
        public BillingResult isFeatureSupported(String feature) {
            // No Play backend means no billing feature is supported.
            return MacApkBilling.result(BillingResponseCode.BILLING_UNAVAILABLE,
                    "Billing service unavailable on device.");
        }

        @Override
        public void queryProductDetailsAsync(QueryProductDetailsParams params,
                                             final ProductDetailsResponseListener listener) {
            if (listener == null) return;
            MacApkBilling.post(new Runnable() {
                @Override public void run() {
                    listener.onProductDetailsResponse(MacApkBilling.unavailable(),
                            Collections.<ProductDetails>emptyList());
                }
            });
        }

        @Override
        public void queryPurchasesAsync(QueryPurchasesParams params,
                                        final PurchasesResponseListener listener) {
            if (listener == null) return;
            // The user owns nothing through a Play Store that is absent. We
            // return OK with an empty list (the documented shape for "no
            // purchases"), never a fabricated purchase.
            MacApkBilling.post(new Runnable() {
                @Override public void run() {
                    listener.onQueryPurchasesResponse(MacApkBilling.ok(),
                            new ArrayList<Purchase>());
                }
            });
        }

        @Override
        public void querySkuDetailsAsync(SkuDetailsParams params,
                                         final SkuDetailsResponseListener listener) {
            if (listener == null) return;
            MacApkBilling.post(new Runnable() {
                @Override public void run() {
                    listener.onSkuDetailsResponse(MacApkBilling.unavailable(),
                            Collections.<SkuDetails>emptyList());
                }
            });
        }

        @Override
        public void queryPurchaseHistoryAsync(String skuType,
                                              final PurchaseHistoryResponseListener listener) {
            if (listener == null) return;
            MacApkBilling.post(new Runnable() {
                @Override public void run() {
                    listener.onPurchaseHistoryResponse(MacApkBilling.unavailable(),
                            Collections.<PurchaseHistoryRecord>emptyList());
                }
            });
        }

        @Override
        public BillingResult launchBillingFlow(Activity activity, BillingFlowParams params) {
            // No Play purchase UI exists. Return synchronously, as the real
            // client does for its synchronous pre-flight result.
            return MacApkBilling.unavailable();
        }

        @Override
        public void acknowledgePurchase(AcknowledgePurchaseParams params,
                                        final AcknowledgePurchaseResponseListener listener) {
            if (listener == null) return;
            MacApkBilling.post(new Runnable() {
                @Override public void run() {
                    listener.onAcknowledgePurchaseResponse(MacApkBilling.unavailable());
                }
            });
        }

        @Override
        public void consumeAsync(ConsumeParams consumeParams,
                                 final ConsumeResponseListener listener) {
            if (listener == null) return;
            final String token = consumeParams == null ? null
                    : consumeParams.getPurchaseToken();
            MacApkBilling.post(new Runnable() {
                @Override public void run() {
                    listener.onConsumeResponse(MacApkBilling.unavailable(), token);
                }
            });
        }

        @Override
        public void getBillingConfigAsync(GetBillingConfigParams params,
                                          final BillingConfigResponseListener listener) {
            if (listener == null) return;
            MacApkBilling.post(new Runnable() {
                @Override public void run() {
                    listener.onBillingConfigResponse(MacApkBilling.unavailable(), null);
                }
            });
        }

        @Override
        public BillingResult showInAppMessages(Activity activity, InAppMessageParams params,
                                               final InAppMessageResponseListener listener) {
            // No Play in-app message exists to surface; report NO_ACTION_NEEDED
            // off-stack and return OK synchronously (the documented shape).
            if (listener != null) {
                MacApkBilling.post(new Runnable() {
                    @Override public void run() {
                        listener.onInAppMessageResponse(new InAppMessageResult(
                                InAppMessageResult.InAppMessageResponseCode.NO_ACTION_NEEDED,
                                null));
                    }
                });
            }
            return MacApkBilling.ok();
        }
    }
}
