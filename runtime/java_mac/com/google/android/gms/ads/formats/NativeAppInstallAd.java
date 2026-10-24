package com.google.android.gms.ads.formats;

// com.google.android.gms.ads.formats.NativeAppInstallAd -- legacy AdMob native-ads API.
// De-Googled device: no ad loads, so this is inert (see the package note).

public abstract class NativeAppInstallAd {
    public abstract CharSequence getHeadline();
    public abstract CharSequence getBody();
    public abstract CharSequence getCallToAction();
    public abstract CharSequence getPrice();
    public abstract CharSequence getStore();
    public abstract Double getStarRating();
    public void destroy() { }
    public android.os.Bundle getExtras() { return new android.os.Bundle(); }
}
