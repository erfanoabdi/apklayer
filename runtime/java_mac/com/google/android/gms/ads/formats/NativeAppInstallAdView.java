package com.google.android.gms.ads.formats;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

// com.google.android.gms.ads.formats.NativeAppInstallAdView -- legacy AdMob native-ads API.
// De-Googled device: no ad loads, so this is inert (see the package note).

public class NativeAppInstallAdView extends FrameLayout {
    private final java.util.HashMap<String, View> mAssets = new java.util.HashMap<>();
    private MediaView mMediaView;
    public NativeAppInstallAdView(Context context) { super(context); }
    public NativeAppInstallAdView(Context context, AttributeSet attrs) { super(context, attrs); }
    public MediaView getMediaView() { return mMediaView; }
    public void setMediaView(MediaView mediaView) { mMediaView = mediaView; }
    public View getHeadlineView() { return mAssets.get("Headline"); }
    public void setHeadlineView(View view) { if (view != null) mAssets.put("Headline", view); }
    public View getBodyView() { return mAssets.get("Body"); }
    public void setBodyView(View view) { if (view != null) mAssets.put("Body", view); }
    public View getCallToActionView() { return mAssets.get("CallToAction"); }
    public void setCallToActionView(View view) { if (view != null) mAssets.put("CallToAction", view); }
    public View getIconView() { return mAssets.get("Icon"); }
    public void setIconView(View view) { if (view != null) mAssets.put("Icon", view); }
    public View getImageView() { return mAssets.get("Image"); }
    public void setImageView(View view) { if (view != null) mAssets.put("Image", view); }
    public View getPriceView() { return mAssets.get("Price"); }
    public void setPriceView(View view) { if (view != null) mAssets.put("Price", view); }
    public View getStarRatingView() { return mAssets.get("StarRating"); }
    public void setStarRatingView(View view) { if (view != null) mAssets.put("StarRating", view); }
    public View getStoreView() { return mAssets.get("Store"); }
    public void setStoreView(View view) { if (view != null) mAssets.put("Store", view); }
    /** Bind the ad to the registered views. No ad loads here, so this is a no-op. */
    public void setNativeAd(NativeAppInstallAd ad) { }
    public void destroy() { mAssets.clear(); }
}
