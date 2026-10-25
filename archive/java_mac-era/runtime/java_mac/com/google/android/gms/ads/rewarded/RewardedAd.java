// com.google.android.gms.ads.rewarded.RewardedAd
//
// GMS Ads reference: a rewarded full-screen ad. On a de-Googled device
// with no ad backend, load() always fails NO_FILL, so no RewardedAd is
// ever produced and show() is never reached. No reward is ever granted
// (granting one would fabricate an entitlement).
package com.google.android.gms.ads.rewarded;

import android.app.Activity;
import android.content.Context;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnPaidEventListener;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.ResponseInfo;
import com.google.android.gms.ads.internal.MainThread;

public abstract class RewardedAd {
    public static void load(Context context, String adUnitId, AdRequest adRequest,
                            final RewardedAdLoadCallback loadCallback) {
        if (loadCallback == null) return;
        MainThread.post(new Runnable() {
            @Override public void run() {
                loadCallback.onAdFailedToLoad(LoadAdError.noFill());
            }
        });
    }

    public abstract String getAdUnitId();

    public abstract RewardItem getRewardItem();

    public abstract FullScreenContentCallback getFullScreenContentCallback();

    public abstract void setFullScreenContentCallback(FullScreenContentCallback callback);

    public abstract OnPaidEventListener getOnPaidEventListener();

    public abstract void setOnPaidEventListener(OnPaidEventListener listener);

    public abstract void setImmersiveMode(boolean immersiveModeEnabled);

    public abstract ServerSideVerificationOptions getServerSideVerificationOptions();

    public abstract void setServerSideVerificationOptions(ServerSideVerificationOptions options);

    public abstract ResponseInfo getResponseInfo();

    public abstract void show(Activity activity, OnUserEarnedRewardListener callback);
}
