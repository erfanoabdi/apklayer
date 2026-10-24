package com.google.android.gms.ads.rewardedinterstitial;
public abstract class RewardedInterstitialAd {
    public abstract String getAdUnitId();
    public abstract void show(android.app.Activity activity,
            com.google.android.gms.ads.OnUserEarnedRewardListener listener);
}
