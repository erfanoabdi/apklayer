package com.google.android.gms.wallet;

// com.google.android.gms.wallet.PaymentDataRequest -- the request an app builds
// (from a JSON string) to launch the Google Pay sheet. Pure data; Mac.apk never
// launches a real Pay sheet (no backend), but the type must link. Mirrors the
// published fromJson factory.
// Ref: developers.google.com/android/reference/.../wallet/PaymentDataRequest
public class PaymentDataRequest {

    private final String json;

    private PaymentDataRequest(String json) { this.json = json; }

    public String toJson() { return json; }

    public static PaymentDataRequest fromJson(String json) {
        return new PaymentDataRequest(json);
    }
}
