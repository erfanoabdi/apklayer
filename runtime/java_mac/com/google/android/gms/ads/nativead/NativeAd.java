// com.google.android.gms.ads.nativead.NativeAd
//
// GMS Ads reference: a native ad's asset bag. On a de-Googled device with
// no ad backend, no native ad is ever delivered (AdLoader fails NO_FILL),
// so no NativeAd instance is constructed. The type + nested types exist
// so app code that references them compiles.
package com.google.android.gms.ads.nativead;

import android.net.Uri;
import android.os.Bundle;

import java.util.List;

import com.google.android.gms.ads.MediaContent;
import com.google.android.gms.ads.ResponseInfo;
import com.google.android.gms.ads.VideoController;

public abstract class NativeAd {
    public abstract String getHeadline();

    public abstract String getBody();

    public abstract String getCallToAction();

    public abstract String getAdvertiser();

    public abstract String getPrice();

    public abstract String getStore();

    public abstract Double getStarRating();

    public abstract Image getIcon();

    public abstract List<Image> getImages();

    public abstract MediaContent getMediaContent();

    public abstract AdChoicesInfo getAdChoicesInfo();

    public abstract ResponseInfo getResponseInfo();

    public abstract Bundle getExtras();

    public abstract VideoController getVideoController();

    public abstract void setOnPaidEventListener(
            com.google.android.gms.ads.OnPaidEventListener listener);

    public abstract void recordCustomClickGesture();

    public abstract void performClick(Bundle clickData);

    public abstract boolean recordImpression(Bundle impressionData);

    public abstract void reportTouchEvent(Bundle touchData);

    public abstract void destroy();

    public abstract void cancelUnconfirmedClick();

    public abstract void enableCustomClickGesture();

    // Nested image asset type from the GMS reference.
    public abstract static class Image {
        public abstract android.graphics.drawable.Drawable getDrawable();
        public abstract Uri getUri();
        public abstract double getScale();
        public int getWidth() { return 0; }
        public int getHeight() { return 0; }
    }

    // Nested AdChoices info from the GMS reference.
    public abstract static class AdChoicesInfo {
        public abstract List<Image> getImages();
        public abstract CharSequence getText();
    }

    // Nested loaded-listener used by AdLoader.Builder.forNativeAd.
    public interface OnNativeAdLoadedListener {
        void onNativeAdLoaded(NativeAd nativeAd);
    }
}
