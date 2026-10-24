package com.google.android.gms.ads.formats;
public final class NativeAdOptions {
    public static final int ADCHOICES_TOP_LEFT = 0, ADCHOICES_TOP_RIGHT = 1, ADCHOICES_BOTTOM_RIGHT = 2, ADCHOICES_BOTTOM_LEFT = 3;
    private NativeAdOptions() {}
    public int getAdChoicesPlacement() { return ADCHOICES_TOP_RIGHT; }
    public boolean shouldReturnUrlsForImageAssets() { return false; }
    public boolean shouldRequestMultipleImages() { return false; }
    public static final class Builder {
        public Builder setAdChoicesPlacement(int adChoicesPlacement) { return this; }
        public Builder setReturnUrlsForImageAssets(boolean flag) { return this; }
        public Builder setRequestMultipleImages(boolean flag) { return this; }
        public NativeAdOptions build() { return new NativeAdOptions(); }
    }
}
