// com.google.android.gms.ads.rewarded.RewardItem
//
// GMS Ads reference: the reward granted when a rewarded ad completes. On
// a no-backend device no rewarded ad ever plays, so a reward is never
// granted (granting one would be fabricating an entitlement). The type
// exists so app code that references it compiles.
package com.google.android.gms.ads.rewarded;

public interface RewardItem {
    // The default zero reward the SDK exposes; never actually delivered
    // on a no-backend device.
    RewardItem DEFAULT_REWARD = new RewardItem() {
        @Override public int getAmount() { return 0; }
        @Override public String getType() { return ""; }
    };

    int getAmount();

    String getType();
}
