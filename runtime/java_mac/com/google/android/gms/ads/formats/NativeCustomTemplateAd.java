package com.google.android.gms.ads.formats;

// com.google.android.gms.ads.formats.NativeCustomTemplateAd -- a custom-template ad.
public abstract class NativeCustomTemplateAd {
    public abstract java.util.List<String> getAvailableAssetNames();
    public abstract String getCustomTemplateId();
    public abstract CharSequence getText(String assetName);
    public abstract void recordImpression();
    public abstract void performClick(String assetName);
    public void destroy() { }
}
