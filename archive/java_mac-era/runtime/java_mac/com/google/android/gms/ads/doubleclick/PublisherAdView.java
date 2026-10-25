package com.google.android.gms.ads.doubleclick;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

// com.google.android.gms.ads.doubleclick.PublisherAdView -- legacy GAM banner.
// De-Googled device: no ad loads; an inert container.
public final class PublisherAdView extends FrameLayout {
    public PublisherAdView(Context context) { super(context); }
    public PublisherAdView(Context context, AttributeSet attrs) { super(context, attrs); }
    public void loadAd(Object adRequest) { }
    private String mAdUnitId;
    private com.google.android.gms.ads.AdListener mAdListener;
    public void setAdUnitId(String adUnitId) { mAdUnitId = adUnitId; }
    public String getAdUnitId() { return mAdUnitId; }
    public void setAdListener(com.google.android.gms.ads.AdListener adListener) { mAdListener = adListener; }
    public com.google.android.gms.ads.AdListener getAdListener() { return mAdListener; }
    public com.google.android.gms.ads.AdSize getAdSize() { return null; }
    public com.google.android.gms.ads.VideoController getVideoController() { return new com.google.android.gms.ads.VideoController(); }
    public void setAppEventListener(AppEventListener appEventListener) { }
    public void setAdSizes(com.google.android.gms.ads.AdSize... adSizes) { }

    public void destroy() { }
    public void pause() { }
    public void resume() { }
}
