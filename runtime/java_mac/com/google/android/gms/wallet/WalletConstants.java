package com.google.android.gms.wallet;

// com.google.android.gms.wallet.WalletConstants -- the Google Pay environment and
// status constants apps reference when building a PaymentsClient. Mac.apk has no
// Google Pay backend, but the constants must link. Values match the published SDK.
// Ref: developers.google.com/android/reference/.../wallet/WalletConstants
public final class WalletConstants {
    private WalletConstants() {}

    public static final int ENVIRONMENT_PRODUCTION = 1;
    public static final int ENVIRONMENT_TEST = 3;

    public static final int PAYMENT_METHOD_CARD = 1;
    public static final int PAYMENT_METHOD_TOKENIZED_CARD = 2;

    public static final int CARD_NETWORK_AMEX = 1;
    public static final int CARD_NETWORK_DISCOVER = 2;
    public static final int CARD_NETWORK_JCB = 3;
    public static final int CARD_NETWORK_MASTERCARD = 4;
    public static final int CARD_NETWORK_VISA = 5;

    public static final int ERROR_CODE_UNKNOWN = -1;
    public static final int ERROR_CODE_SERVICE_UNAVAILABLE = 412;
}
