package com.google.android.gms.ads;
import android.content.Context;
/** Legacy interstitial ad. No ad loads on a de-Googled device: isLoaded is
 *  false and show is a no-op. */
public final class InterstitialAd {
    private String mAdUnitId;
    private AdListener mAdListener;
    public InterstitialAd(Context context) { }
    public void setAdUnitId(String adUnitId) { mAdUnitId = adUnitId; }
    public String getAdUnitId() { return mAdUnitId; }
    public void setAdListener(AdListener adListener) { mAdListener = adListener; }
    public AdListener getAdListener() { return mAdListener; }
    public void loadAd(AdRequest adRequest) { }
    public boolean isLoaded() { return false; }
    public boolean isLoading() { return false; }
    public void show() { }
    public void setImmersiveMode(boolean immersiveModeEnabled) { }
    public void setRewardedVideoAdListener(Object listener) { }
}
