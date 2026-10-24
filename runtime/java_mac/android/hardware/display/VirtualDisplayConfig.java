// android.hardware.display.VirtualDisplayConfig
//
// The parameter object form of DisplayManager.createVirtualDisplay. AOSP added
// it because the positional overload had run out of room; every field it
// carries is one the 8-arg overload either took positionally or could not
// express at all (display categories, requested refresh rate, brightness).
//
// Mirrors frameworks/base/core/java/android/hardware/display/VirtualDisplayConfig.java
// (android16-release). The class is a pure value carrier -- no system service
// is involved in building one -- so this is a faithful reimplementation rather
// than a stand-in: every getter answers exactly what the builder was given.
// Whether a virtual display can then actually be CREATED is DisplayManager's
// question, and it answers null; see the long note at
// DisplayManager.createVirtualDisplay.
//
// The Builder's validation is copied verbatim from AOSP :466-500, :554-600,
// :660-740 because it is observable: an app that builds a config with a
// zero width, a negative refresh rate, or a dim brightness above the default
// gets IllegalArgumentException on a device and must get it here too. Getting
// this wrong in the lenient direction is the worse failure -- the app would
// sail past a bad config and blame the display for the result.
package android.hardware.display;

import android.view.Surface;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

public final class VirtualDisplayConfig implements android.os.Parcelable {

    // AOSP's unset marker for dim brightness is PowerManager.BRIGHTNESS_INVALID,
    // which is Float.NaN. Using the same value (rather than, say, -1f) matters
    // because build()'s guard is `isValidBrightness(mDimBrightness) &&
    // mDimBrightness > mDefaultBrightness`, and isValidBrightness rejects NaN --
    // so an unset dim brightness must be NaN or the guard changes meaning.
    private static final float BRIGHTNESS_INVALID = Float.NaN;
    private static final float BRIGHTNESS_MIN = 0.0f;
    private static final float BRIGHTNESS_MAX = 1.0f;

    private final String mName;
    private final int mWidth;
    private final int mHeight;
    private final int mDensityDpi;
    private final int mFlags;
    private final Surface mSurface;
    private final Set<String> mDisplayCategories;
    private final float mRequestedRefreshRate;
    private final float mDefaultBrightness;
    private final float mDimBrightness;

    private VirtualDisplayConfig(String name, int width, int height, int densityDpi,
            int flags, Surface surface, Set<String> displayCategories,
            float requestedRefreshRate, float defaultBrightness, float dimBrightness) {
        mName = name;
        mWidth = width;
        mHeight = height;
        mDensityDpi = densityDpi;
        mFlags = flags;
        mSurface = surface;
        mDisplayCategories = Collections.unmodifiableSet(
                new LinkedHashSet<>(displayCategories));
        mRequestedRefreshRate = requestedRefreshRate;
        mDefaultBrightness = defaultBrightness;
        mDimBrightness = dimBrightness;
    }

    public String getName() { return mName; }
    public int getWidth() { return mWidth; }
    public int getHeight() { return mHeight; }
    public int getDensityDpi() { return mDensityDpi; }
    public int getFlags() { return mFlags; }
    public Surface getSurface() { return mSurface; }
    public Set<String> getDisplayCategories() { return mDisplayCategories; }
    public float getRequestedRefreshRate() { return mRequestedRefreshRate; }
    public float getDefaultBrightness() { return mDefaultBrightness; }
    public float getDimBrightness() { return mDimBrightness; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeInt(mWidth);
        dest.writeInt(mHeight);
        dest.writeInt(mDensityDpi);
        dest.writeInt(mFlags);
        dest.writeInt(mDisplayCategories.size());
        for (String c : mDisplayCategories) dest.writeString(c);
        dest.writeFloat(mRequestedRefreshRate);
        dest.writeFloat(mDefaultBrightness);
        dest.writeFloat(mDimBrightness);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof VirtualDisplayConfig)) return false;
        VirtualDisplayConfig that = (VirtualDisplayConfig) other;
        return Objects.equals(mName, that.mName)
                && mWidth == that.mWidth
                && mHeight == that.mHeight
                && mDensityDpi == that.mDensityDpi
                && mFlags == that.mFlags
                && Objects.equals(mSurface, that.mSurface)
                && Objects.equals(mDisplayCategories, that.mDisplayCategories)
                && mRequestedRefreshRate == that.mRequestedRefreshRate
                && floatSame(mDefaultBrightness, that.mDefaultBrightness)
                && floatSame(mDimBrightness, that.mDimBrightness);
    }

    // NaN is the unset marker for dim brightness, and NaN != NaN under ==, so
    // two configs that both left it unset would compare unequal. Float.compare
    // treats NaN as equal to itself, which is the intent here.
    private static boolean floatSame(float a, float b) {
        return Float.compare(a, b) == 0;
    }

    @Override public int hashCode() {
        return Objects.hash(mName, mWidth, mHeight, mDensityDpi, mFlags, mSurface,
                mDisplayCategories, mRequestedRefreshRate,
                mDefaultBrightness, mDimBrightness);
    }

    @Override public String toString() {
        return "VirtualDisplayConfig{name=" + mName + ", width=" + mWidth
                + ", height=" + mHeight + ", densityDpi=" + mDensityDpi
                + ", flags=" + mFlags + "}";
    }

    public static final Creator<VirtualDisplayConfig> CREATOR =
            new Creator<VirtualDisplayConfig>() {
        @Override public VirtualDisplayConfig createFromParcel(android.os.Parcel in) {
            String name = in.readString();
            int width = in.readInt();
            int height = in.readInt();
            int densityDpi = in.readInt();
            int flags = in.readInt();
            int n = in.readInt();
            Set<String> cats = new LinkedHashSet<>();
            for (int i = 0; i < n; i++) cats.add(in.readString());
            float refresh = in.readFloat();
            float defaultBrightness = in.readFloat();
            float dimBrightness = in.readFloat();
            // The Surface is not carried: it is a live buffer-queue handle, and
            // nothing in this tree can reconstitute one from a Parcel.
            return new VirtualDisplayConfig(name, width, height, densityDpi, flags,
                    null, cats, refresh, defaultBrightness, dimBrightness);
        }
        @Override public VirtualDisplayConfig[] newArray(int size) {
            return new VirtualDisplayConfig[size];
        }
    };

    /** AOSP VirtualDisplayConfig.BrightnessListener -- notified when the
     *  system changes the virtual display's brightness. Nothing here ever
     *  creates a virtual display, so a registered listener is never called;
     *  the type must exist so an app's lambda/anonymous class resolves. */
    public interface BrightnessListener {
        void onBrightnessChanged(float brightness);
    }

    public static final class Builder {
        private final String mName;
        private final int mWidth;
        private final int mHeight;
        private final int mDensityDpi;
        private int mFlags = 0;
        private Surface mSurface = null;
        private final Set<String> mDisplayCategories = new LinkedHashSet<>();
        private float mRequestedRefreshRate = 0.0f;
        private float mDefaultBrightness = 0.0f;
        private float mDimBrightness = BRIGHTNESS_INVALID;

        /** AOSP :466. Note AOSP raises IllegalArgumentException for a null name
         *  here, NOT the NullPointerException that
         *  DisplayManager.createVirtualDisplay(String, ...) raises for the same
         *  mistake -- the two entry points genuinely differ, so both are
         *  mirrored as written rather than harmonised. */
        public Builder(String name, int width, int height, int densityDpi) {
            if (name == null) {
                throw new IllegalArgumentException("Virtual display name is required");
            }
            if (width <= 0) {
                throw new IllegalArgumentException("Virtual display width must be positive");
            }
            if (height <= 0) {
                throw new IllegalArgumentException("Virtual display height must be positive");
            }
            if (densityDpi <= 0) {
                throw new IllegalArgumentException("Virtual display density must be positive");
            }
            mName = name;
            mWidth = width;
            mHeight = height;
            mDensityDpi = densityDpi;
        }

        public Builder setFlags(int flags) {
            mFlags = flags;
            return this;
        }

        public Builder setSurface(Surface surface) {
            mSurface = surface;
            return this;
        }

        public Builder setDisplayCategories(Set<String> displayCategories) {
            mDisplayCategories.clear();
            mDisplayCategories.addAll(Objects.requireNonNull(displayCategories));
            return this;
        }

        public Builder addDisplayCategory(String displayCategory) {
            mDisplayCategories.add(Objects.requireNonNull(displayCategory));
            return this;
        }

        public Builder setRequestedRefreshRate(float requestedRefreshRate) {
            if (requestedRefreshRate < 0.0f) {
                throw new IllegalArgumentException(
                        "Virtual display requested refresh rate must be non-negative");
            }
            mRequestedRefreshRate = requestedRefreshRate;
            return this;
        }

        public Builder setDefaultBrightness(float brightness) {
            if (!isValidBrightness(brightness)) {
                throw new IllegalArgumentException(
                        "Virtual display default brightness must be in range [0.0, 1.0]");
            }
            mDefaultBrightness = brightness;
            return this;
        }

        public Builder setDimBrightness(float brightness) {
            if (!isValidBrightness(brightness)) {
                throw new IllegalArgumentException(
                        "Virtual display dim brightness must be in range [0.0, 1.0]");
            }
            mDimBrightness = brightness;
            return this;
        }

        /** AOSP stores an executor+listener delegate that the display service
         *  would call back on. There is no display service, so the pair is held
         *  only so the null checks AOSP performs still fire. */
        public Builder setBrightnessListener(Executor executor, BrightnessListener listener) {
            Objects.requireNonNull(executor);
            Objects.requireNonNull(listener);
            return this;
        }

        private static boolean isValidBrightness(float brightness) {
            return !Float.isNaN(brightness) && BRIGHTNESS_MIN <= brightness
                    && brightness <= BRIGHTNESS_MAX;
        }

        /** AOSP :728. The cross-field check (dim must not exceed default) can
         *  only be made once both are known, which is why it lives here rather
         *  than in the setters. */
        public VirtualDisplayConfig build() {
            if (isValidBrightness(mDimBrightness) && mDimBrightness > mDefaultBrightness) {
                throw new IllegalArgumentException(
                        "The dim brightness must not be greater than the default brightness");
            }
            return new VirtualDisplayConfig(mName, mWidth, mHeight, mDensityDpi, mFlags,
                    mSurface, mDisplayCategories, mRequestedRefreshRate,
                    mDefaultBrightness, mDimBrightness);
        }
    }
}
