// android.widget.Magnifier -- a floating loupe that magnifies a region of a
// view (used for text-selection handles). Was a bare `extends Object` stub.
// We keep the show/dismiss/update state + the Builder; the magnified glass is
// not painted (no compositor loupe surface). AOSP:
//   frameworks/base/core/java/android/widget/Magnifier.java
//   public final class Magnifier
package android.widget;

import android.view.View;

public final class Magnifier {
    public static final int SOURCE_BOUND_MAX_IN_SURFACE = 0;
    public static final int SOURCE_BOUND_MAX_VISIBLE = 1;

    private final View mView;
    private float mShowX = Float.NaN;
    private float mShowY = Float.NaN;
    private int mWidth;
    private int mHeight;
    private float mZoom = 1.25f;
    private boolean mShowing;

    public Magnifier(View view) { mView = view; }

    public void show(float sourceCenterX, float sourceCenterY) {
        show(sourceCenterX, sourceCenterY, Float.NaN, Float.NaN);
    }
    public void show(float sourceCenterX, float sourceCenterY,
            float magnifierCenterX, float magnifierCenterY) {
        mShowX = sourceCenterX;
        mShowY = sourceCenterY;
        mShowing = true;
    }

    public void dismiss() { mShowing = false; }
    public void update() { /* re-fetch the magnified pixels; no-op here */ }

    public int getWidth() { return mWidth; }
    public int getHeight() { return mHeight; }
    public float getZoom() { return mZoom; }
    public int getSourceWidth() { return (int) (mWidth / mZoom); }
    public int getSourceHeight() { return (int) (mHeight / mZoom); }

    public float getPositionX() { return mShowX; }
    public float getPositionY() { return mShowY; }

    public android.graphics.Point getPosition() {
        return Float.isNaN(mShowX) ? null
                : new android.graphics.Point((int) mShowX, (int) mShowY);
    }
    public android.graphics.Point getSourcePosition() { return getPosition(); }

    public int getDefaultHorizontalSourceToMagnifierOffset() { return 0; }
    public int getDefaultVerticalSourceToMagnifierOffset() { return 0; }
    public float getElevation() { return 0f; }
    public float getCornerRadius() { return 0f; }
    public int getOverlayDefaultSize() { return 0; }

    public static final class Builder {
        private final View mView;
        private int mWidth;
        private int mHeight;
        private float mZoom = 1.25f;
        private float mElevation;
        private float mCornerRadius;

        public Builder(View view) { mView = view; }

        public Builder setSize(int width, int height) {
            mWidth = width; mHeight = height; return this;
        }
        public Builder setZoom(float zoom) { mZoom = zoom; return this; }
        public Builder setElevation(float elevation) { mElevation = elevation; return this; }
        public Builder setCornerRadius(float cornerRadius) { mCornerRadius = cornerRadius; return this; }
        public Builder setInitialZoom(float zoom) { mZoom = zoom; return this; }
        public Builder setDefaultSourceToMagnifierOffset(int horizontal, int vertical) { return this; }
        public Builder setSourceBounds(int left, int top, int right, int bottom) { return this; }
        // AOSP Magnifier.Builder :1566 / :1525 store these; they used to be
        // discarded, which is why Magnifier.isClippingEnabled()/getOverlay()
        // (added below) could not exist.
        private boolean mClippingEnabled = true;   // AOSP Builder default :1445
        private android.graphics.drawable.Drawable mOverlay;
        public Builder setClippingEnabled(boolean clip) { mClippingEnabled = clip; return this; }
        public Builder setOverlay(android.graphics.drawable.Drawable overlay) {
            mOverlay = overlay; return this;
        }

        public Magnifier build() {
            Magnifier m = new Magnifier(mView);
            m.mWidth = mWidth;
            m.mHeight = mHeight;
            m.mZoom = mZoom;
            m.mClippingEnabled = mClippingEnabled;
            m.mOverlay = mOverlay;
            return m;
        }
    }

    public void setZoom(float zoom) { }

    // ---- API-36 surface completion ------------------------------------
    // AOSP Magnifier.java :571 / :558. Both are pure reads of Builder-supplied
    // parameters (Magnifier.java :229 / :224). This port draws no magnifier,
    // so neither value is consumed -- but the DEFAULTS are observable, and
    // AOSP's clipping default is true (:1445, and :185 for the non-Builder
    // constructor), not false.
    private boolean mClippingEnabled = true;
    private android.graphics.drawable.Drawable mOverlay;

    public boolean isClippingEnabled() { return mClippingEnabled; }
    public android.graphics.drawable.Drawable getOverlay() { return mOverlay; }
}
