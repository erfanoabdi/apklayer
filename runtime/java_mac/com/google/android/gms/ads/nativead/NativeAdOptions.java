// com.google.android.gms.ads.nativead.NativeAdOptions
//
// GMS Ads reference: options for native-ad requests. Pure local config;
// no native ad is ever delivered on a no-backend device.
package com.google.android.gms.ads.nativead;

import com.google.android.gms.ads.VideoOptions;

public final class NativeAdOptions {
    public static final int ADCHOICES_TOP_LEFT = 0;
    public static final int ADCHOICES_TOP_RIGHT = 1;
    public static final int ADCHOICES_BOTTOM_RIGHT = 2;
    public static final int ADCHOICES_BOTTOM_LEFT = 3;

    public static final int NATIVE_MEDIA_ASPECT_RATIO_UNKNOWN = 0;
    public static final int NATIVE_MEDIA_ASPECT_RATIO_ANY = 1;
    public static final int NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE = 2;
    public static final int NATIVE_MEDIA_ASPECT_RATIO_PORTRAIT = 3;
    public static final int NATIVE_MEDIA_ASPECT_RATIO_SQUARE = 4;

    public static final int SWIPE_GESTURE_DIRECTION_RIGHT = 1;
    public static final int SWIPE_GESTURE_DIRECTION_LEFT = 2;
    public static final int SWIPE_GESTURE_DIRECTION_UP = 3;
    public static final int SWIPE_GESTURE_DIRECTION_DOWN = 4;

    private final int adChoicesPlacement;
    private final int mediaAspectRatio;
    private final boolean requestMultipleImages;
    private final boolean returnUrlsForImageAssets;
    private final boolean requestCustomMuteThisAd;
    private final VideoOptions videoOptions;

    private NativeAdOptions(Builder b) {
        this.adChoicesPlacement = b.adChoicesPlacement;
        this.mediaAspectRatio = b.mediaAspectRatio;
        this.requestMultipleImages = b.requestMultipleImages;
        this.returnUrlsForImageAssets = b.returnUrlsForImageAssets;
        this.requestCustomMuteThisAd = b.requestCustomMuteThisAd;
        this.videoOptions = b.videoOptions;
    }

    public int getAdChoicesPlacement() { return adChoicesPlacement; }

    public int getMediaAspectRatio() { return mediaAspectRatio; }

    public boolean shouldRequestMultipleImages() { return requestMultipleImages; }

    public boolean shouldReturnUrlsForImageAssets() { return returnUrlsForImageAssets; }

    public boolean shouldRequestCustomMuteThisAd() { return requestCustomMuteThisAd; }

    public VideoOptions getVideoOptions() { return videoOptions; }

    public static final class Builder {
        private int adChoicesPlacement = ADCHOICES_TOP_RIGHT;
        private int mediaAspectRatio = NATIVE_MEDIA_ASPECT_RATIO_UNKNOWN;
        private boolean requestMultipleImages = false;
        private boolean returnUrlsForImageAssets = false;
        private boolean requestCustomMuteThisAd = false;
        private VideoOptions videoOptions = null;

        public Builder setAdChoicesPlacement(int v) { this.adChoicesPlacement = v; return this; }

        public Builder setMediaAspectRatio(int v) { this.mediaAspectRatio = v; return this; }

        public Builder setRequestMultipleImages(boolean v) {
            this.requestMultipleImages = v; return this;
        }

        public Builder setReturnUrlsForImageAssets(boolean v) {
            this.returnUrlsForImageAssets = v; return this;
        }

        public Builder setRequestCustomMuteThisAd(boolean v) {
            this.requestCustomMuteThisAd = v; return this;
        }

        public Builder setVideoOptions(VideoOptions v) { this.videoOptions = v; return this; }

        public NativeAdOptions build() { return new NativeAdOptions(this); }
    }
}
