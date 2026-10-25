// com.google.android.gms.ads.FullScreenContentCallback
//
// GMS Ads reference: lifecycle callbacks for full-screen formats
// (interstitial / rewarded / app-open). Since no full-screen ad is ever
// shown on a no-backend device, only onAdFailedToShowFullScreenContent
// would ever fire (and only if an app calls show() on an unloaded ad);
// the rest stay empty defaults.
package com.google.android.gms.ads;

public abstract class FullScreenContentCallback {
    public void onAdClicked() { }
    public void onAdDismissedFullScreenContent() { }
    public void onAdFailedToShowFullScreenContent(AdError adError) { }
    public void onAdImpression() { }
    public void onAdShowedFullScreenContent() { }
}
