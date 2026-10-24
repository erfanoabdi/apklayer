package com.android.billingclient.api;

import android.os.Handler;
import android.os.Looper;

// MacApkBilling -- Mac.apk-internal (package-private) helper for the Play
// Billing shim. NOT part of the public com.android.billingclient.api surface,
// so it never shadows a real Play Billing class; it just centralizes the two
// things every shimmed BillingClient call needs:
//
//   (1) Canonical BillingResult builders for the response codes we return.
//       Mac.apk runs as a de-Googled device with no Play Store service, so
//       every billing path resolves to an HONEST negative -- almost always
//       BILLING_UNAVAILABLE (the Play service is structurally absent), never
//       a fabricated OK with a fake purchase / entitlement.
//
//   (2) Off-stack callback delivery on the main thread, mirroring how the
//       real Play Billing Library delivers listener callbacks asynchronously
//       rather than re-entrantly on the caller's stack.
final class MacApkBilling {
    private MacApkBilling() { }

    // The standard human-readable debug message Play Billing itself attaches
    // when the Play Store service cannot be reached on a device without it.
    private static final String MSG_UNAVAILABLE =
            "Billing service unavailable on device.";
    private static final String MSG_DISCONNECTED =
            "Service connection is disconnected.";

    static BillingResult result(int responseCode, String debugMessage) {
        return BillingResult.newBuilder()
                .setResponseCode(responseCode)
                .setDebugMessage(debugMessage == null ? "" : debugMessage)
                .build();
    }

    static BillingResult unavailable() {
        return result(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
                MSG_UNAVAILABLE);
    }

    static BillingResult disconnected() {
        return result(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                MSG_DISCONNECTED);
    }

    static BillingResult ok() {
        return result(BillingClient.BillingResponseCode.OK, "");
    }

    // Deliver a listener callback off the caller's stack on the main thread,
    // matching real Play Billing's asynchronous delivery. Falls back to inline
    // execution on a headless / no-looper path so a callback is never dropped.
    static void post(Runnable r) {
        if (r == null) return;
        try {
            new Handler(Looper.getMainLooper()).post(r);
        } catch (Throwable t) {
            r.run();
        }
    }
}
