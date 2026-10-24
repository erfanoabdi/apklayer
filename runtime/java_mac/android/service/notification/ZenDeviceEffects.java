package android.service.notification;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * API 35 companion to ZenPolicy: the DEVICE-level changes a zen mode makes
 * (grayscale display, dimmed wallpaper, night mode, ambient display off) as
 * opposed to which notifications get through.
 *
 * Mac.apk applies none of these to the host -- a guest does not get to grayscale
 * the user's Mac -- but the object is pure state and an app that builds one,
 * attaches it to an AutomaticZenRule and reads it back gets exactly what it
 * put in.
 *
 * Source: frameworks/base/core/java/android/service/notification/ZenDeviceEffects.java
 */
public final class ZenDeviceEffects implements Parcelable {

    private boolean mGrayscale;
    private boolean mSuppressAmbientDisplay;
    private boolean mDimWallpaper;
    private boolean mNightMode;

    private ZenDeviceEffects() { }

    public boolean shouldDisplayGrayscale() { return mGrayscale; }

    public boolean shouldSuppressAmbientDisplay() { return mSuppressAmbientDisplay; }

    public boolean shouldDimWallpaper() { return mDimWallpaper; }

    public boolean shouldUseNightMode() { return mNightMode; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mGrayscale ? 1 : 0);
        dest.writeInt(mSuppressAmbientDisplay ? 1 : 0);
        dest.writeInt(mDimWallpaper ? 1 : 0);
        dest.writeInt(mNightMode ? 1 : 0);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ZenDeviceEffects)) return false;
        ZenDeviceEffects e = (ZenDeviceEffects) o;
        return mGrayscale == e.mGrayscale
                && mSuppressAmbientDisplay == e.mSuppressAmbientDisplay
                && mDimWallpaper == e.mDimWallpaper
                && mNightMode == e.mNightMode;
    }

    @Override public int hashCode() {
        return (mGrayscale ? 1 : 0) | (mSuppressAmbientDisplay ? 2 : 0)
                | (mDimWallpaper ? 4 : 0) | (mNightMode ? 8 : 0);
    }

    @Override public String toString() {
        return "ZenDeviceEffects[grayscale=" + mGrayscale
                + ", suppressAmbientDisplay=" + mSuppressAmbientDisplay
                + ", dimWallpaper=" + mDimWallpaper
                + ", nightMode=" + mNightMode + "]";
    }

    public static final Creator<ZenDeviceEffects> CREATOR = new Creator<ZenDeviceEffects>() {
        @Override public ZenDeviceEffects createFromParcel(Parcel source) {
            ZenDeviceEffects e = new ZenDeviceEffects();
            e.mGrayscale = source.readInt() != 0;
            e.mSuppressAmbientDisplay = source.readInt() != 0;
            e.mDimWallpaper = source.readInt() != 0;
            e.mNightMode = source.readInt() != 0;
            return e;
        }
        @Override public ZenDeviceEffects[] newArray(int size) {
            return new ZenDeviceEffects[size];
        }
    };

    public static final class Builder {
        private final ZenDeviceEffects mEffects = new ZenDeviceEffects();

        public Builder() { }

        /** AOSP's copy constructor; the new builder starts from an existing
         *  set of effects rather than from the defaults. */
        public Builder(ZenDeviceEffects zenDeviceEffects) {
            if (zenDeviceEffects != null) {
                mEffects.mGrayscale = zenDeviceEffects.mGrayscale;
                mEffects.mSuppressAmbientDisplay = zenDeviceEffects.mSuppressAmbientDisplay;
                mEffects.mDimWallpaper = zenDeviceEffects.mDimWallpaper;
                mEffects.mNightMode = zenDeviceEffects.mNightMode;
            }
        }

        public Builder setShouldDisplayGrayscale(boolean grayscale) {
            mEffects.mGrayscale = grayscale;
            return this;
        }

        public Builder setShouldSuppressAmbientDisplay(boolean suppressAmbientDisplay) {
            mEffects.mSuppressAmbientDisplay = suppressAmbientDisplay;
            return this;
        }

        public Builder setShouldDimWallpaper(boolean dimWallpaper) {
            mEffects.mDimWallpaper = dimWallpaper;
            return this;
        }

        public Builder setShouldUseNightMode(boolean nightMode) {
            mEffects.mNightMode = nightMode;
            return this;
        }

        public ZenDeviceEffects build() {
            ZenDeviceEffects copy = new ZenDeviceEffects();
            copy.mGrayscale = mEffects.mGrayscale;
            copy.mSuppressAmbientDisplay = mEffects.mSuppressAmbientDisplay;
            copy.mDimWallpaper = mEffects.mDimWallpaper;
            copy.mNightMode = mEffects.mNightMode;
            return copy;
        }
    }
}
