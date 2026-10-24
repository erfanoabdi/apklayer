package com.google.android.gms.ads.doubleclick;
import android.content.Context;
import com.google.android.gms.ads.AdListener;
/** Legacy GAM interstitial. No ad loads. */
public final class PublisherInterstitialAd {
    private String mAdUnitId;
    private AdListener mAdListener;
    public PublisherInterstitialAd(Context context) { }
    public void setAdUnitId(String adUnitId) { mAdUnitId = adUnitId; }
    public String getAdUnitId() { return mAdUnitId; }
    public void setAdListener(AdListener adListener) { mAdListener = adListener; }
    public AdListener getAdListener() { return mAdListener; }
    public void setAppEventListener(AppEventListener appEventListener) { }
    public void loadAd(PublisherAdRequest adRequest) { }
    public boolean isLoaded() { return false; }
    public boolean isLoading() { return false; }
    public void show() { }
    public void setImmersiveMode(boolean immersiveModeEnabled) { }
}
