package com.google.android.gms.ads.admanager;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import com.google.android.gms.ads.AdListener;
/** Inert ad-view container (no ad loads). */
public final class AdManagerAdView extends FrameLayout {
    private String mAdUnitId;
    private AdListener mAdListener;
    public AdManagerAdView(Context context) { super(context); }
    public AdManagerAdView(Context context, AttributeSet attrs) { super(context, attrs); }
    public AdManagerAdView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); }
    public void setAdUnitId(String adUnitId) { mAdUnitId = adUnitId; }
    public String getAdUnitId() { return mAdUnitId; }
    public void setAdListener(AdListener adListener) { mAdListener = adListener; }
    public AdListener getAdListener() { return mAdListener; }
    public com.google.android.gms.ads.VideoController getVideoController() { return new com.google.android.gms.ads.VideoController(); }
    public void destroy() { }
    public void pause() { }
    public void resume() { }
    public void loadAd(AdManagerAdRequest adRequest) { }
    public void setAdSizes(com.google.android.gms.ads.AdSize... adSizes) { }
    public void setAppEventListener(AppEventListener appEventListener) { }
}
