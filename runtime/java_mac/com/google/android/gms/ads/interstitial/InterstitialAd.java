// com.google.android.gms.ads.interstitial.InterstitialAd
//
// GMS Ads reference: full-screen interstitial. On a de-Googled device
// with no ad backend, load() always fails NO_FILL (delivered async like
// the real SDK), so no InterstitialAd instance is ever produced and
// show() is never reached. No ad is shown; nothing is fabricated.
package com.google.android.gms.ads.interstitial;

import android.app.Activity;
import android.content.Context;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnPaidEventListener;
import com.google.android.gms.ads.ResponseInfo;
import com.google.android.gms.ads.internal.MainThread;

public abstract class InterstitialAd {
    public static void load(Context context, String adUnitId, AdRequest adRequest,
                            final InterstitialAdLoadCallback loadCallback) {
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

    public abstract void setImmersiveMode(boolean immersiveModeEnabled);

    public abstract OnPaidEventListener getOnPaidEventListener();

    public abstract void setOnPaidEventListener(OnPaidEventListener listener);

    public abstract ResponseInfo getResponseInfo();

    public abstract void show(Activity activity);
}
