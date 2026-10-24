package com.google.android.gms.ads.reward;

import android.content.Context;
import android.os.Bundle;

// Legacy GMS RewardedVideoAd (pre-2019, before the unified rewarded.RewardedAd).
// HCR 1.43 obtains it via MobileAds.getRewardedVideoAdInstance(ctx). Honest no-op
// for a no-Google-Play device: no ad backend, so isLoaded()=false and loadAd
// reports NO_FILL (error code 3). NEVER fabricates a reward / impression.
public class RewardedVideoAd {
    private RewardedVideoAdListener listener;

    public void setRewardedVideoAdListener(RewardedVideoAdListener l) { this.listener = l; }
    public RewardedVideoAdListener getRewardedVideoAdListener() { return listener; }

    public void loadAd(String adUnitId, com.google.android.gms.ads.AdRequest adRequest) {
        failNoFill();
    }
    public void loadAd(String adUnitId, Bundle adRequest) { failNoFill(); }

    private void failNoFill() {
        // AdRequest.ERROR_CODE_NO_FILL == 3 in the legacy SDK.
        if (listener != null) listener.onRewardedVideoAdFailedToLoad(3);
    }

    public boolean isLoaded() { return false; }
    public void show() { }
    public void pause(Context context) { }
    public void resume(Context context) { }
    public void destroy(Context context) { }
    public void destroy() { }
    public void setUserId(String userId) { }
    public void setImmersiveMode(boolean immersiveModeEnabled) { }
    public void setCustomData(String customData) { }
    public String getUserId() { return null; }
    public String getMediationAdapterClassName() { return null; }
    public Bundle getAdMetadata() { return new Bundle(); }
    public void setAdMetadataListener(Object listener) { }
}
