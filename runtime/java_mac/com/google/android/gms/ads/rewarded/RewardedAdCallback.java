package com.google.android.gms.ads.rewarded;
/** Legacy full-screen rewarded-ad presentation callback. Never invoked. */
public abstract class RewardedAdCallback {
    public RewardedAdCallback() {}
    public void onRewardedAdOpened() {}
    public void onRewardedAdClosed() {}
    public void onUserEarnedReward(com.google.android.gms.ads.reward.RewardItem reward) {}
    public void onRewardedAdFailedToShow(int errorCode) {}
    public void onRewardedAdFailedToShow(com.google.android.gms.ads.AdError adError) {}
}
