// android.media.projection.MediaProjectionConfig -- customization for the
// consent request built by
// MediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig).
// API 33.
//
// It is a pure value object (two ints) describing what the consent dialog
// should offer: the user's choice of region, or a fixed default display. It
// carries no capability of its own, so nothing about it is unsupported here --
// what stays unsupported is the capture the consent would have granted, and
// that answer is given where it belongs, in MediaProjectionManager (no consent
// transport, so getMediaProjection returns null).
//
// AOSP ref: frameworks/base/media/java/android/media/projection/MediaProjectionConfig.java
// (android14-release). AOSP generates the boilerplate with @DataClass; the
// generated equals/hashCode/writeToParcel/CREATOR are reproduced by hand below,
// field order included, because the generator is not available here.
package android.media.projection;

import android.os.Parcel;
import android.os.Parcelable;

public final class MediaProjectionConfig implements Parcelable {

    /** The user, rather than the host app, picks the region to capture. @hide */
    public static final int CAPTURE_REGION_USER_CHOICE = 0;

    /** The host app names a particular display to capture. @hide */
    public static final int CAPTURE_REGION_FIXED_DISPLAY = 1;

    /** The display to capture; only meaningful when the region is
     *  CAPTURE_REGION_FIXED_DISPLAY. AOSP only supports Display.DEFAULT_DISPLAY. */
    private int mDisplayToCapture;

    /** Defaults to the user's choice, as AOSP's field initializer does. */
    private int mRegionToCapture = CAPTURE_REGION_USER_CHOICE;

    private MediaProjectionConfig() {
    }

    private MediaProjectionConfig(int captureRegion) {
        mRegionToCapture = captureRegion;
    }

    /** Restricts the user to capturing the default display. */
    public static MediaProjectionConfig createConfigForDefaultDisplay() {
        MediaProjectionConfig config = new MediaProjectionConfig(CAPTURE_REGION_FIXED_DISPLAY);
        config.mDisplayToCapture = android.view.Display.DEFAULT_DISPLAY;
        return config;
    }

    /** Lets the user decide which region is captured; the consent dialog then
     *  looks the same as for the no-argument createScreenCaptureIntent(). */
    public static MediaProjectionConfig createConfigForUserChoice() {
        return new MediaProjectionConfig(CAPTURE_REGION_USER_CHOICE);
    }

    /** @hide */
    public int getDisplayToCapture() {
        return mDisplayToCapture;
    }

    /** @hide */
    public int getRegionToCapture() {
        return mRegionToCapture;
    }

    // ---- app-content-sharing sources (API 36) ----------------------------
    // Still pure request DATA: the app declares which sources the consent
    // dialog should OFFER (a whole display, another app's window, a piece of
    // app content). Whether any of it can be granted is answered where it
    // belongs -- MediaProjectionManager, which has no consent transport -- so
    // storing and reading these back is honest, not a capability claim.

    public static final int PROJECTION_SOURCE_DISPLAY = 2;
    public static final int PROJECTION_SOURCE_APP = 8;
    public static final int PROJECTION_SOURCE_APP_CONTENT = 16;

    private int mProjectionSources = PROJECTION_SOURCE_DISPLAY;
    private int mInitiallySelectedSource = PROJECTION_SOURCE_DISPLAY;
    private CharSequence mRequesterHint;

    /** The bitmask of sources the consent dialog should offer. */
    public int getProjectionSources() { return mProjectionSources; }

    /** Which of those sources starts out selected. */
    public int getInitiallySelectedSource() { return mInitiallySelectedSource; }

    /** @Nullable in AOSP: an app-supplied string shown in the consent dialog. */
    public CharSequence getRequesterHint() { return mRequesterHint; }

    public boolean isSourceEnabled(int projectionSource) {
        return (mProjectionSources & projectionSource) != 0;
    }

    /** API 36. AOSP's Builder default enables PROJECTION_SOURCE_DISPLAY only
     *  (MediaProjectionConfig.java, Builder). */
    public static final class Builder {
        private int mProjectionSources = PROJECTION_SOURCE_DISPLAY;
        private int mInitiallySelectedSource = PROJECTION_SOURCE_DISPLAY;
        private CharSequence mRequesterHint;

        public Builder() { }

        public Builder setSourceEnabled(int projectionSource, boolean enabled) {
            if (enabled) mProjectionSources |= projectionSource;
            else mProjectionSources &= ~projectionSource;
            return this;
        }

        public Builder setInitiallySelectedSource(int projectionSource) {
            mInitiallySelectedSource = projectionSource;
            return this;
        }

        public Builder setRequesterHint(String requesterHint) {
            mRequesterHint = requesterHint;
            return this;
        }

        public MediaProjectionConfig build() {
            MediaProjectionConfig config =
                    new MediaProjectionConfig(CAPTURE_REGION_USER_CHOICE);
            config.mProjectionSources = mProjectionSources;
            config.mInitiallySelectedSource = mInitiallySelectedSource;
            config.mRequesterHint = mRequesterHint;
            return config;
        }
    }

    private static String captureRegionToString(int value) {
        switch (value) {
            case CAPTURE_REGION_USER_CHOICE:
                return "CAPTURE_REGION_USERS_CHOICE";
            case CAPTURE_REGION_FIXED_DISPLAY:
                return "CAPTURE_REGION_GIVEN_DISPLAY";
            default:
                return Integer.toHexString(value);
        }
    }

    @Override
    public String toString() {
        return "MediaProjectionConfig { "
                + "displayToCapture = " + mDisplayToCapture + ", "
                + "regionToCapture = " + captureRegionToString(mRegionToCapture)
                + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaProjectionConfig that = (MediaProjectionConfig) o;
        return mDisplayToCapture == that.mDisplayToCapture
                && mRegionToCapture == that.mRegionToCapture;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = 31 * hash + mDisplayToCapture;
        hash = 31 * hash + mRegionToCapture;
        return hash;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mDisplayToCapture);
        dest.writeInt(mRegionToCapture);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private MediaProjectionConfig(Parcel in) {
        mDisplayToCapture = in.readInt();
        mRegionToCapture = in.readInt();
    }

    public static final Parcelable.Creator<MediaProjectionConfig> CREATOR =
            new Parcelable.Creator<MediaProjectionConfig>() {
                @Override
                public MediaProjectionConfig createFromParcel(Parcel in) {
                    return new MediaProjectionConfig(in);
                }

                @Override
                public MediaProjectionConfig[] newArray(int size) {
                    return new MediaProjectionConfig[size];
                }
            };
}
