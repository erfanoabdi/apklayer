// com.google.android.gms.ads.AdView
//
// GMS Ads reference: the modern AdMob banner view. On a de-Googled
// device with no ad backend, the view stays an empty container and every
// loadAd fails NO_FILL (delivered to the AdListener asynchronously, as
// the real SDK delivers errors). No ad is ever rendered; no impression
// or click is fabricated. Extends our FrameLayout so it occupies the
// reserved banner slot in the layout without drawing anything.
package com.google.android.gms.ads;

import android.content.Context;
import android.widget.FrameLayout;

import com.google.android.gms.ads.internal.MainThread;

public class AdView extends FrameLayout {
    private String adUnitId;
    private AdSize adSize;
    private AdListener adListener;
    private OnPaidEventListener paidListener;

    public AdView(Context context) { super(context); }

    public AdView(Context context, android.util.AttributeSet attrs) { super(context, attrs); }

    public AdView(Context context, android.util.AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setAdUnitId(String adUnitId) { this.adUnitId = adUnitId; }

    public String getAdUnitId() { return adUnitId; }

    public void setAdSize(AdSize adSize) { this.adSize = adSize; }

    public AdSize getAdSize() { return adSize; }

    public AdSize[] getAdSizes() {
        return adSize == null ? new AdSize[0] : new AdSize[] { adSize };
    }

    public void setAdSizes(AdSize... adSizes) {
        if (adSizes != null && adSizes.length > 0) this.adSize = adSizes[0];
    }

    public void setAdListener(AdListener adListener) { this.adListener = adListener; }

    public AdListener getAdListener() { return adListener; }

    public void setOnPaidEventListener(OnPaidEventListener listener) {
        this.paidListener = listener;
    }

    public ResponseInfo getResponseInfo() { return null; }

    public void loadAd(final AdRequest adRequest) {
        // No backend -> honest NO_FILL, delivered async on the main
        // thread like the real SDK so the app's listener is invoked off
        // the loadAd call stack.
        final AdListener l = adListener;
        if (l == null) return;
        MainThread.post(new Runnable() {
            @Override public void run() { l.onAdFailedToLoad(LoadAdError.noFill()); }
        });
    }

    public void pause() { }

    public void resume() { }

    public void destroy() { }

    public boolean isLoading() { return false; }

    public void recordManualImpression() { }

    public com.google.android.gms.ads.VideoController getVideoController() { return new com.google.android.gms.ads.VideoController(); }
}

