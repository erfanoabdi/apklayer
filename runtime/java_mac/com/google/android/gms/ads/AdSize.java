// com.google.android.gms.ads.AdSize
//
// GMS Ads reference: describes the size of a banner ad. We keep the real
// width/height values and the public constant set so layout code that
// reads them (to reserve space) gets correct dimensions even though no
// ad is ever shown.
package com.google.android.gms.ads;

import android.content.Context;

public final class AdSize {
    // Width/height in density-independent pixels per the GMS reference.
    public static final int FULL_WIDTH = -1;
    public static final int AUTO_HEIGHT = -2;

    public static final AdSize BANNER = new AdSize(320, 50);
    public static final AdSize FULL_BANNER = new AdSize(468, 60);
    public static final AdSize LARGE_BANNER = new AdSize(320, 100);
    public static final AdSize LEADERBOARD = new AdSize(728, 90);
    public static final AdSize MEDIUM_RECTANGLE = new AdSize(300, 250);
    public static final AdSize WIDE_SKYSCRAPER = new AdSize(160, 600);
    public static final AdSize SMART_BANNER = new AdSize(FULL_WIDTH, AUTO_HEIGHT);
    public static final AdSize FLUID = new AdSize(FULL_WIDTH, AUTO_HEIGHT);
    public static final AdSize INVALID = new AdSize(0, 0);
    public static final AdSize SEARCH = new AdSize(FULL_WIDTH, AUTO_HEIGHT);

    private final int widthDp;
    private final int heightDp;

    public AdSize(int widthDp, int heightDp) {
        this.widthDp = widthDp;
        this.heightDp = heightDp;
    }

    public int getWidth() { return widthDp; }

    public int getHeight() { return heightDp; }

    // Pixel sizes per the device density. -1 (FULL_WIDTH) / -2
    // (AUTO_HEIGHT) propagate through so callers can detect adaptive
    // sizes.
    public int getWidthInPixels(Context context) {
        if (widthDp == FULL_WIDTH) return FULL_WIDTH;
        return Math.round(widthDp * density(context));
    }

    public int getHeightInPixels(Context context) {
        if (heightDp == AUTO_HEIGHT) return AUTO_HEIGHT;
        return Math.round(heightDp * density(context));
    }

    public boolean isFullWidth() { return widthDp == FULL_WIDTH; }

    public boolean isAutoHeight() { return heightDp == AUTO_HEIGHT; }

    public boolean isFluid() { return this == FLUID; }

    private static float density(Context context) {
        try {
            if (context != null && context.getResources() != null
                    && context.getResources().getDisplayMetrics() != null) {
                return context.getResources().getDisplayMetrics().density;
            }
        } catch (Throwable ignore) { }
        return 1.0f;
    }

    // Adaptive-banner factories. We compute the recommended adaptive
    // height the SDK formula yields (roughly width/3, clamped 50..90 dp,
    // capped by screen-height fraction). No backend needed -- this is a
    // pure local sizing helper.
    public static AdSize getCurrentOrientationAnchoredAdaptiveBannerAdSize(Context context, int widthDp) {
        return new AdSize(widthDp, adaptiveHeight(widthDp));
    }

    public static AdSize getLandscapeAnchoredAdaptiveBannerAdSize(Context context, int widthDp) {
        return new AdSize(widthDp, adaptiveHeight(widthDp));
    }

    public static AdSize getPortraitAnchoredAdaptiveBannerAdSize(Context context, int widthDp) {
        return new AdSize(widthDp, adaptiveHeight(widthDp));
    }

    public static AdSize getInlineAdaptiveBannerAdSize(int widthDp, int maxHeightDp) {
        return new AdSize(widthDp, maxHeightDp);
    }

    public static AdSize getCurrentOrientationInlineAdaptiveBannerAdSize(Context context, int widthDp) {
        return new AdSize(widthDp, adaptiveHeight(widthDp));
    }

    public static AdSize getLandscapeInlineAdaptiveBannerAdSize(Context context, int widthDp) {
        return new AdSize(widthDp, adaptiveHeight(widthDp));
    }

    public static AdSize getPortraitInlineAdaptiveBannerAdSize(Context context, int widthDp) {
        return new AdSize(widthDp, adaptiveHeight(widthDp));
    }

    private static int adaptiveHeight(int widthDp) {
        int h = Math.round(widthDp / 3.0f);
        if (h < 50) h = 50;
        if (h > 90) h = 90;
        return h;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdSize)) return false;
        AdSize a = (AdSize) o;
        return a.widthDp == widthDp && a.heightDp == heightDp;
    }

    @Override public int hashCode() { return widthDp * 31 + heightDp; }

    @Override public String toString() {
        if (this == SMART_BANNER) return "SMART_BANNER";
        if (this == FLUID) return "FLUID";
        if (this == INVALID) return "INVALID";
        return widthDp + "x" + heightDp + "_as";
    }
}
