package com.google.android.gms.ads.reward;

// Legacy GMS rewarded-video reward item (pre-2019 reward API). Interface in the
// published SDK; consumers read getType()/getAmount() in onRewarded().
public interface RewardItem {
    String getType();
    int getAmount();
}
