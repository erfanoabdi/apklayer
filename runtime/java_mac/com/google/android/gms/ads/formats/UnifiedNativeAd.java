package com.google.android.gms.ads.formats;
// Legacy AdMob unified-native-ads API. De-Googled device: no ad loads, inert.
public abstract class UnifiedNativeAd {
    public abstract CharSequence getHeadline();
    public abstract CharSequence getBody();
    public abstract CharSequence getCallToAction();
    public abstract CharSequence getAdvertiser();
    public abstract java.util.List<Object> getImages();
    public void destroy() { }
    public void recordImpression() { }
    public android.os.Bundle getExtras() { return new android.os.Bundle(); }
    public abstract static class OnUnifiedNativeAdLoadedListener {
        public abstract void onUnifiedNativeAdLoaded(UnifiedNativeAd ad);
    }
}
