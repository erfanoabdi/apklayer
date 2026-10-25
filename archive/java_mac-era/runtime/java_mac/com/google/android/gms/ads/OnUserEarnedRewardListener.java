// com.google.android.gms.ads.OnUserEarnedRewardListener
//
// GMS Ads reference: callback invoked when the user earns a reward.
// Never fires on a no-backend device (no rewarded ad plays), but apps
// pass it to RewardedAd.show.
package com.google.android.gms.ads;

import com.google.android.gms.ads.rewarded.RewardItem;

public interface OnUserEarnedRewardListener {
    void onUserEarnedReward(RewardItem rewardItem);
}
