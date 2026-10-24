// com.google.android.gms.ads.internal.MainThread
//
// Mac.apk-internal helper (NOT part of the public GMS API surface) used
// by our ads shims to deliver load/init callbacks asynchronously on the
// main thread, matching how the real Google Mobile Ads SDK delivers ad
// load errors off the caller's stack. Lives in an internal package so it
// never shadows a real GMS class.
package com.google.android.gms.ads.internal;

import android.os.Handler;
import android.os.Looper;

public final class MainThread {
    private MainThread() { }

    public static void post(Runnable r) {
        if (r == null) return;
        try {
            new Handler(Looper.getMainLooper()).post(r);
        } catch (Throwable t) {
            // Headless / no-looper path: run inline so the callback is
            // not silently dropped.
            r.run();
        }
    }
}
