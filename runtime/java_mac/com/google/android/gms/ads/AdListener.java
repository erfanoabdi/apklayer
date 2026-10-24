// com.google.android.gms.ads.AdListener
//
// GMS Ads reference: the banner/ad lifecycle callbacks. This is a class
// (not an interface) in the modern SDK with empty default methods, so
// apps that override only onAdFailedToLoad compile. Mac.apk only ever
// invokes onAdFailedToLoad (NO_FILL) since no ad is shown.
package com.google.android.gms.ads;

public abstract class AdListener {
    public void onAdClicked() { }
    public void onAdClosed() { }
    public void onAdFailedToLoad(LoadAdError loadAdError) { }
    public void onAdImpression() { }
    public void onAdLoaded() { }
    public void onAdOpened() { }
    public void onAdSwipeGestureClicked() { }

    /** Deprecated pre-19.0 error-code form. Mac.apk never shows an ad. */
    public void onAdFailedToLoad(int errorCode) { }

    /** Deprecated pre-19.0 callback; Mac.apk never navigates away for an ad. */
    public void onAdLeftApplication() { }
}


