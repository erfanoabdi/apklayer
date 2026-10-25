package com.google.android.gms.ads.formats;
// Legacy AdMob native ad. De-Googled device: no ad loads, inert.
public abstract class NativeAd {
    public abstract CharSequence getHeadline();
    public abstract CharSequence getBody();
    public abstract CharSequence getCallToAction();
    public void destroy() {}
    public android.os.Bundle getExtras() { return new android.os.Bundle(); }
    public interface OnNativeAdLoadedListener { void onNativeAdLoaded(NativeAd ad); }
}
