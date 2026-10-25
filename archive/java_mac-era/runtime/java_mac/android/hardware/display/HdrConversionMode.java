// android.hardware.display.HdrConversionMode
//
// The device's HDR output-conversion policy: does the display pass an HDR
// stream through untouched, convert it to a type the panel prefers, or is
// conversion not a thing this device does at all.
//
// Mirrors frameworks/base/core/java/android/hardware/display/HdrConversionMode.java
// (android16-release). The constructors' argument validation is copied
// verbatim from :86-102 because it is observable: an app that constructs
// HdrConversionMode(HDR_CONVERSION_PASSTHROUGH, someType) gets
// IllegalArgumentException on a device and must get it here too.
package android.hardware.display;

import android.view.Display;

public final class HdrConversionMode implements android.os.Parcelable {

    /** The device does not support HDR output conversion. */
    public static final int HDR_CONVERSION_UNSUPPORTED = 0;
    /** HDR output is passed through unconverted. */
    public static final int HDR_CONVERSION_PASSTHROUGH = 1;
    /** The system picks the output type. */
    public static final int HDR_CONVERSION_SYSTEM = 2;
    /** Conversion is forced to a caller-chosen output type. */
    public static final int HDR_CONVERSION_FORCE = 3;

    private final int mConversionMode;
    private final int mPreferredHdrOutputType;

    /** AOSP HdrConversionMode.java:86. The guard is AOSP's, verbatim: a
     *  preferred output type is meaningless for PASSTHROUGH and UNSUPPORTED,
     *  and AOSP rejects the combination rather than ignoring it. */
    public HdrConversionMode(int conversionMode, int preferredHdrOutputType) {
        if ((conversionMode == HDR_CONVERSION_PASSTHROUGH
                || conversionMode == HDR_CONVERSION_UNSUPPORTED)
                && preferredHdrOutputType != Display.HdrCapabilities.HDR_TYPE_INVALID) {
            throw new IllegalArgumentException("preferredHdrOutputType must not be set if"
                    + " the conversion mode is " + conversionMode);
        }
        mConversionMode = conversionMode;
        mPreferredHdrOutputType = preferredHdrOutputType;
    }

    /** AOSP HdrConversionMode.java:99 -- the one-arg form leaves the preferred
     *  output type at HDR_TYPE_INVALID and performs NO validation, so
     *  `new HdrConversionMode(HDR_CONVERSION_FORCE)` is legal there and here. */
    public HdrConversionMode(int conversionMode) {
        mConversionMode = conversionMode;
        mPreferredHdrOutputType = Display.HdrCapabilities.HDR_TYPE_INVALID;
    }

    public int getConversionMode() { return mConversionMode; }

    public int getPreferredHdrOutputType() { return mPreferredHdrOutputType; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mConversionMode);
        dest.writeInt(mPreferredHdrOutputType);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof HdrConversionMode)) return false;
        HdrConversionMode o = (HdrConversionMode) other;
        return o.mConversionMode == mConversionMode
                && o.mPreferredHdrOutputType == mPreferredHdrOutputType;
    }

    @Override public int hashCode() {
        return mConversionMode * 31 + mPreferredHdrOutputType;
    }

    @Override public String toString() {
        return "HdrConversionMode{conversionMode=" + mConversionMode
                + ", preferredHdrOutputType=" + mPreferredHdrOutputType + "}";
    }

    public static final Creator<HdrConversionMode> CREATOR = new Creator<HdrConversionMode>() {
        @Override public HdrConversionMode createFromParcel(android.os.Parcel source) {
            return new HdrConversionMode(source.readInt(), source.readInt());
        }
        @Override public HdrConversionMode[] newArray(int size) {
            return new HdrConversionMode[size];
        }
    };
}
