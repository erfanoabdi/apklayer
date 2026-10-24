package com.google.android.gms.ads.doubleclick;
public interface CustomRenderedAd {
    String getBaseUrl();
    String getContent();
    void onAdRendered(android.view.View view);
    void recordClick();
    void recordImpression();
}
