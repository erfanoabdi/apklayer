// com.google.android.gms.ads.appopen.AppOpenAd
//
// GMS Ads reference: an app-open full-screen ad. On a de-Googled device
// with no ad backend, load() always fails NO_FILL, so no AppOpenAd is
// ever produced and show() is never reached. No ad is shown.
package com.google.android.gms.ads.appopen;

import android.app.Activity;
import android.content.Context;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdLoadCallback;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnPaidEventListener;
import com.google.android.gms.ads.ResponseInfo;
import com.google.android.gms.ads.internal.MainThread;

public abstract class AppOpenAd {
    // Orientation hints from the GMS reference (deprecated but referenced
    // by some older integrations).
    public static final int APP_OPEN_AD_ORIENTATION_PORTRAIT = 1;
    public static final int APP_OPEN_AD_ORIENTATION_LANDSCAPE = 2;

    public static void load(Context context, String adUnitId, AdRequest adRequest,
                            final AppOpenAdLoadCallback loadCallback) {
        if (loadCallback == null) return;
        MainThread.post(new Runnable() {
            @Override public void run() {
                loadCallback.onAdFailedToLoad(LoadAdError.noFill());
            }
        });
    }

    public abstract String getAdUnitId();

    public abstract FullScreenContentCallback getFullScreenContentCallback();

    public abstract void setFullScreenContentCallback(FullScreenContentCallback callback);

    public abstract OnPaidEventListener getOnPaidEventListener();

    public abstract void setOnPaidEventListener(OnPaidEventListener listener);

    public abstract void setImmersiveMode(boolean immersiveModeEnabled);

    public abstract ResponseInfo getResponseInfo();

    public abstract void show(Activity activity);

    // The format-specific load callback nested as the SDK ships it.
    public abstract static class AppOpenAdLoadCallback extends AdLoadCallback<AppOpenAd> {
        public void onAppOpenAdLoaded(AppOpenAd ad) { }
        public void onAppOpenAdFailedToLoad(com.google.android.gms.ads.LoadAdError error) { }
        public void onAppOpenAdFailedToLoad(int errorCode) { }
    }
}
