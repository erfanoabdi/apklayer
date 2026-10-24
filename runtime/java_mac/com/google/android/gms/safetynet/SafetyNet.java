package com.google.android.gms.safetynet;

import android.app.Activity;
import android.content.Context;

// com.google.android.gms.safetynet.SafetyNet -- the static entry point apps call
// to obtain a SafetyNetClient. Mirrors the published GMS SafetyNet facade
// (getClient(Context) / getClient(Activity)). The returned client honours
// project policy: attestation/reCAPTCHA Tasks FAIL (never forged), enable/list
// resolve with honest empty/negative results.
public final class SafetyNet {
    private SafetyNet() {}

    public static SafetyNetClient getClient(Context context) {
        return new SafetyNetClient();
    }

    public static SafetyNetClient getClient(Activity activity) {
        return new SafetyNetClient();
    }
}
