package android.accessibilityservice;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * The magnification state an accessibility service asks the platform for, or
 * reads back from it: which magnifier (fullscreen vs windowed), its scale, the
 * screen point it is centred on, and whether it is active at all.
 *
 * A plain value holder in AOSP too -- the platform reads the fields out of it
 * in AccessibilityService.MagnificationController. Mac.apk drives no magnifier,
 * so a config an app builds is stored and handed back verbatim rather than
 * applied; that keeps a builder round-trip (build -> read the getters back)
 * self-consistent, which is what an app's own unit-test-shaped code checks.
 *
 * Source: frameworks/base/core/java/android/accessibilityservice/MagnificationConfig.java
 * Constant values transcribed verbatim from /tmp/aosp_api/android16.txt
 * (signature format 2.0), package android.accessibilityservice.
 */
public final class MagnificationConfig implements Parcelable {

    /** Let the platform pick, i.e. keep whatever mode is already active. */
    public static final int MAGNIFICATION_MODE_DEFAULT = 0;
    public static final int MAGNIFICATION_MODE_FULLSCREEN = 1;
    public static final int MAGNIFICATION_MODE_WINDOW = 2;

    private int mMode = MAGNIFICATION_MODE_DEFAULT;
    private boolean mActivated = true;
    private float mScale = Float.NaN;
    private float mCenterX = Float.NaN;
    private float mCenterY = Float.NaN;

    // AOSP's constructor is package-private; instances come from the Builder or
    // from the CREATOR. Keep it that way so an app cannot construct a config
    // that skipped the builder's defaults.
    private MagnificationConfig() { }

    public int getMode() { return mMode; }

    public boolean isActivated() { return mActivated; }

    /** NaN when unset, exactly as AOSP reports an unspecified scale. */
    public float getScale() { return mScale; }

    public float getCenterX() { return mCenterX; }

    public float getCenterY() { return mCenterY; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mMode);
        dest.writeInt(mActivated ? 1 : 0);
        dest.writeFloat(mScale);
        dest.writeFloat(mCenterX);
        dest.writeFloat(mCenterY);
    }

    @Override public String toString() {
        return "MagnificationConfig[mode=" + mMode + ", activated=" + mActivated
                + ", scale=" + mScale + ", centerX=" + mCenterX
                + ", centerY=" + mCenterY + "]";
    }

    public static final Creator<MagnificationConfig> CREATOR =
            new Creator<MagnificationConfig>() {
        @Override public MagnificationConfig createFromParcel(Parcel in) {
            MagnificationConfig c = new MagnificationConfig();
            c.mMode = in.readInt();
            c.mActivated = in.readInt() != 0;
            c.mScale = in.readFloat();
            c.mCenterX = in.readFloat();
            c.mCenterY = in.readFloat();
            return c;
        }
        @Override public MagnificationConfig[] newArray(int size) {
            return new MagnificationConfig[size];
        }
    };

    /** AOSP's builder. The fields live on the BUILDER, not on a shared
     *  config instance -- build() snapshots them into a fresh object, so
     *  mutating the builder afterwards cannot change a config already handed
     *  out. Mirrors MagnificationConfig.Builder in AOSP. */
    public static final class Builder {
        private int mMode = MAGNIFICATION_MODE_DEFAULT;
        private boolean mActivated = true;
        private float mScale = Float.NaN;
        private float mCenterX = Float.NaN;
        private float mCenterY = Float.NaN;

        public Builder() { }

        public Builder setMode(int mode) { mMode = mode; return this; }

        public Builder setActivated(boolean activated) { mActivated = activated; return this; }

        public Builder setScale(float scale) { mScale = scale; return this; }

        public Builder setCenterX(float centerX) { mCenterX = centerX; return this; }

        public Builder setCenterY(float centerY) { mCenterY = centerY; return this; }

        public MagnificationConfig build() {
            MagnificationConfig c = new MagnificationConfig();
            c.mMode = mMode;
            c.mActivated = mActivated;
            c.mScale = mScale;
            c.mCenterX = mCenterX;
            c.mCenterY = mCenterY;
            return c;
        }
    }
}
