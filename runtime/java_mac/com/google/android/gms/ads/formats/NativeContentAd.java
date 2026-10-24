package com.google.android.gms.ads.formats;

// com.google.android.gms.ads.formats.NativeContentAd -- legacy AdMob native-ads API.
// De-Googled device: no ad loads, so this is inert (see the package note).

public abstract class NativeContentAd {
    public abstract CharSequence getHeadline();
    public abstract CharSequence getBody();
    public abstract CharSequence getCallToAction();
    public abstract CharSequence getAdvertiser();
    public void destroy() { }
    public android.os.Bundle getExtras() { return new android.os.Bundle(); }
}
