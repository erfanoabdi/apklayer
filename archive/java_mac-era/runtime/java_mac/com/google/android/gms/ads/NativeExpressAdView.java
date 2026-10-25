package com.google.android.gms.ads;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import com.google.android.gms.ads.AdListener;
/** Inert ad-view container (no ad loads). */
public final class NativeExpressAdView extends FrameLayout {
    private String mAdUnitId;
    private AdListener mAdListener;
    public NativeExpressAdView(Context context) { super(context); }
    public NativeExpressAdView(Context context, AttributeSet attrs) { super(context, attrs); }
    public NativeExpressAdView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); }
    public void setAdUnitId(String adUnitId) { mAdUnitId = adUnitId; }
    public String getAdUnitId() { return mAdUnitId; }
    public void setAdListener(AdListener adListener) { mAdListener = adListener; }
    public AdListener getAdListener() { return mAdListener; }
    public com.google.android.gms.ads.VideoController getVideoController() { return new com.google.android.gms.ads.VideoController(); }
    public void destroy() { }
    public void pause() { }
    public void resume() { }
    public void loadAd(com.google.android.gms.ads.AdRequest adRequest) { }
    public void setAdSize(com.google.android.gms.ads.AdSize adSize) { }
    public void setVideoOptions(com.google.android.gms.ads.VideoOptions videoOptions) { }
}
