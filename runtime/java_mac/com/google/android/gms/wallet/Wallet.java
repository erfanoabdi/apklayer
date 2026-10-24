package com.google.android.gms.wallet;

import android.app.Activity;
import android.content.Context;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

// com.google.android.gms.wallet.Wallet -- entry point for Google Pay. Returns a
// PaymentsClient that honestly reports Pay is not available (isReadyToPay ->
// false) and never completes a real payment. NEVER fabricates a token or a
// purchase (de-Googled policy). Mirrors the published static factory + nested
// WalletOptions builder. Ref: developers.google.com/android/reference/.../wallet/Wallet
public final class Wallet {
    private Wallet() {}

    public static PaymentsClient getPaymentsClient(Activity activity, WalletOptions options) {
        return PAYMENTS;
    }

    public static PaymentsClient getPaymentsClient(Context context, WalletOptions options) {
        return PAYMENTS;
    }

    private static final PaymentsClient PAYMENTS = new PaymentsClient() {
        @Override public Task<Boolean> isReadyToPay(IsReadyToPayRequest request) {
            // Honest negative: Google Pay is not available on a de-Googled device.
            return Tasks.forResult(Boolean.FALSE);
        }
        @Override public Task<Object> loadPaymentData(PaymentDataRequest request) {
            return Tasks.forException(new com.google.android.gms.common.api.ApiException(
                    new com.google.android.gms.common.api.Status(
                            com.google.android.gms.common.api.CommonStatusCodes.API_NOT_CONNECTED,
                            "Google Pay unavailable")));
        }
        @Override public void loadPaymentData(PaymentDataRequest request, Activity activity, int requestCode) {
            // No Pay backend to launch; nothing resolves back to onActivityResult.
        }
    };

    // com.google.android.gms.wallet.Wallet.WalletOptions -- environment config.
    public static final class WalletOptions {
        public final int environment;
        public final int theme;
        private WalletOptions(int environment, int theme) {
            this.environment = environment;
            this.theme = theme;
        }
        public static final class Builder {
            private int environment = WalletConstants.ENVIRONMENT_TEST;
            private int theme = 0;
            public Builder setEnvironment(int environment) { this.environment = environment; return this; }
            public Builder setTheme(int theme) { this.theme = theme; return this; }
            public WalletOptions build() { return new WalletOptions(environment, theme); }
        }
    }
}
