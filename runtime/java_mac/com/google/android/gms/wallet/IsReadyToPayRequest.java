package com.google.android.gms.wallet;

// com.google.android.gms.wallet.IsReadyToPayRequest -- the request an app builds
// (from a JSON string) to ask whether Google Pay is available. Pure data; Mac.apk
// always answers "not ready" (no Google Pay backend), but the type must link.
// Mirrors the published fromJson factory.
// Ref: developers.google.com/android/reference/.../wallet/IsReadyToPayRequest
public class IsReadyToPayRequest {

    private final String json;

    private IsReadyToPayRequest(String json) { this.json = json; }

    public String toJson() { return json; }

    public static IsReadyToPayRequest fromJson(String json) {
        return new IsReadyToPayRequest(json);
    }
}
