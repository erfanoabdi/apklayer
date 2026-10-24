package com.google.android.gms.ads.nativead;
import android.view.View;
// com.google.android.gms.ads.nativead.NativeAdViewHolder (the newer nativead pkg).
public class NativeAdViewHolder {
    private final java.util.HashMap<String, View> mAssets = new java.util.HashMap<>();
    public NativeAdViewHolder(View containerView) { }
    public void setHeadlineView(View v) { mAssets.put("Headline", v); }
    public void setBodyView(View v) { mAssets.put("Body", v); }
    public void setCallToActionView(View v) { mAssets.put("CallToAction", v); }
    public void setIconView(View v) { mAssets.put("Icon", v); }
    public void setImageView(View v) { mAssets.put("Image", v); }
    public void setPriceView(View v) { mAssets.put("Price", v); }
    public void setStarRatingView(View v) { mAssets.put("StarRating", v); }
    public void setStoreView(View v) { mAssets.put("Store", v); }
    public void setAdvertiserView(View v) { mAssets.put("Advertiser", v); }
    public void setMediaView(MediaView v) { }
}
