// com.google.android.gms.ads.nativead.NativeAdView
//
// GMS Ads reference: the container ViewGroup an app populates with a
// native ad's asset views. On a no-backend device no NativeAd is ever
// delivered, so setNativeAd is never called with a real ad; the view
// stays empty. We keep the full asset-view setter surface so layout
// inflation and findViewById wiring compile and run.
package com.google.android.gms.ads.nativead;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

public class NativeAdView extends FrameLayout {
    private View headlineView;
    private View bodyView;
    private View callToActionView;
    private View iconView;
    private View priceView;
    private View starRatingView;
    private View storeView;
    private View advertiserView;
    private View imageView;
    private MediaView mediaView;
    private View adChoicesView;
    private NativeAd nativeAd;

    public NativeAdView(Context context) { super(context); }

    public NativeAdView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public NativeAdView(Context context, android.util.AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setHeadlineView(View v) { this.headlineView = v; }
    public View getHeadlineView() { return headlineView; }

    public void setBodyView(View v) { this.bodyView = v; }
    public View getBodyView() { return bodyView; }

    public void setCallToActionView(View v) { this.callToActionView = v; }
    public View getCallToActionView() { return callToActionView; }

    public void setIconView(View v) { this.iconView = v; }
    public View getIconView() { return iconView; }

    public void setPriceView(View v) { this.priceView = v; }
    public View getPriceView() { return priceView; }

    public void setStarRatingView(View v) { this.starRatingView = v; }
    public View getStarRatingView() { return starRatingView; }

    public void setStoreView(View v) { this.storeView = v; }
    public View getStoreView() { return storeView; }

    public void setAdvertiserView(View v) { this.advertiserView = v; }
    public View getAdvertiserView() { return advertiserView; }

    public void setImageView(View v) { this.imageView = v; }
    public View getImageView() { return imageView; }

    public void setMediaView(MediaView v) { this.mediaView = v; }
    public MediaView getMediaView() { return mediaView; }

    public void setAdChoicesView(View v) { this.adChoicesView = v; }
    public View getAdChoicesView() { return adChoicesView; }

    public void setNativeAd(NativeAd nativeAd) { this.nativeAd = nativeAd; }

    public void destroy() { this.nativeAd = null; }
}
