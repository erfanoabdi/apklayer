package com.google.android.gms.ads.reward;

// Legacy GMS rewarded-video listener (pre-2019 unified RewardedAd). Apps register
// it via RewardedVideoAd.setRewardedVideoAdListener. On a no-Google-Play device
// the load honestly fails (no fill); we never fabricate a reward.
public interface RewardedVideoAdListener {
    void onRewardedVideoAdLoaded();
    void onRewardedVideoAdOpened();
    void onRewardedVideoStarted();
    void onRewardedVideoAdClosed();
    void onRewarded(RewardItem reward);
    void onRewardedVideoAdLeftApplication();
    void onRewardedVideoAdFailedToLoad(int errorCode);
    default void onRewardedVideoCompleted() {}
}
