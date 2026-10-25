// com.google.android.gms.ads.MobileAds
//
// GMS Ads reference: the modern AdMob entry point. Mac.apk runs on a
// de-Googled device with no reachable ad backend. Per the owner's
// emulator-grade policy (same as the legacy com.google.ads.* shims):
// initialize() fires onInitializationComplete so the app's startup flow
// proceeds, but every subsequent ad load fails with the honest NO_FILL
// result a real device returns when no ad config is reachable. No ad is
// ever shown; no impression, click, or paid event is fabricated.
package com.google.android.gms.ads;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

public final class MobileAds {
    private static volatile RequestConfiguration requestConfiguration =
            new RequestConfiguration.Builder().build();
    private static volatile boolean appMuted = false;
    private static volatile float appVolume = 1.0f;

    private MobileAds() { }

    public static void initialize(Context context) {
        initialize(context, null);
    }

    public static void initialize(Context context,
                                  final OnInitializationCompleteListener listener) {
        if (listener == null) return;
        // Real SDK delivers the callback asynchronously on the main
        // thread; mirror that so apps that touch UI in the callback are
        // on the right thread.
        post(new Runnable() {
            @Override public void run() {
                listener.onInitializationComplete(InitializationStatus.coreReady());
            }
        });
    }

    public static void setRequestConfiguration(RequestConfiguration configuration) {
        if (configuration != null) requestConfiguration = configuration;
    }

    public static RequestConfiguration getRequestConfiguration() {
        return requestConfiguration;
    }

    public static String getVersionString() { return "23.0.0"; }

    public static com.google.android.gms.ads.VersionInfo getVersion() {
        return new com.google.android.gms.ads.VersionInfo(23, 0, 0);
    }

    public static void setAppMuted(boolean muted) { appMuted = muted; }

    public static void setAppVolume(float volume) { appVolume = volume; }

    public static void setAppRecordEnabled(boolean enabled) { }

    public static void disableMediationAdapterInitialization(Context context) { }

    // Legacy (pre-2019) rewarded-video entry point. HCR 1.43 calls this 5x at boot;
    // it was missing -> NoSuchMethodError. Returns a no-op RewardedVideoAd (no ad
    // backend on a no-Google-Play device -> reports NO_FILL; never fabricates a reward).
    public static com.google.android.gms.ads.reward.RewardedVideoAd
            getRewardedVideoAdInstance(Context context) {
        return new com.google.android.gms.ads.reward.RewardedVideoAd();
    }

    public static void enableSameAppKey(boolean enable) { }

    public static void putPublisherFirstPartyIdEnabled(boolean enabled) { }

    public static void registerWebView(android.webkit.WebView webView) { }

    // No ad inspector backend; honest failure would require an
    // OnAdInspectorClosedListener round-trip. The real SDK opens an
    // activity; on a no-backend device we no-op.
    public static void openAdInspector(Context context, Object listener) { }

    public static void openDebugMenu(Context context, String adUnitId) { }

    private static void post(Runnable r) {
        try {
            new Handler(Looper.getMainLooper()).post(r);
        } catch (Throwable t) {
            // No main looper available (rare in our headless paths) --
            // run inline rather than dropping the init callback.
            r.run();
        }
    }
}
