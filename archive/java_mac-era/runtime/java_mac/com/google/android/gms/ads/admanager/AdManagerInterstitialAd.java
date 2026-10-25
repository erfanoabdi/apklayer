package com.google.android.gms.ads.admanager;
public abstract class AdManagerInterstitialAd {
    public abstract String getAdUnitId();
    public abstract void show(android.app.Activity activity);
    public abstract void setAppEventListener(AppEventListener appEventListener);
    public abstract void setFullScreenContentCallback(com.google.android.gms.ads.FullScreenContentCallback callback);
}
