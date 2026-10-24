// com.google.android.gms.ads.AdLoadCallback<AdT>
//
// GMS Ads reference: the base callback for full-screen format loads.
// Format-specific callbacks (InterstitialAdLoadCallback, etc.) extend
// this. On a no-backend device only onAdFailedToLoad ever fires.
package com.google.android.gms.ads;

public abstract class AdLoadCallback<AdT> {
    public void onAdFailedToLoad(LoadAdError loadAdError) { }

    public void onAdLoaded(AdT ad) { }
}
