// com.google.android.gms.ads.MediaContent
//
// GMS Ads reference: the media (image/video) asset of a native ad.
// Never populated on a no-backend device (no native ad delivered), but
// app code that wires a MediaView references the type.
package com.google.android.gms.ads;

public interface MediaContent {
    boolean hasVideoContent();

    float getAspectRatio();

    float getCurrentTime();

    float getDuration();

    VideoController getVideoController();

    android.graphics.drawable.Drawable getMainImage();

    void setMainImage(android.graphics.drawable.Drawable drawable);
}
