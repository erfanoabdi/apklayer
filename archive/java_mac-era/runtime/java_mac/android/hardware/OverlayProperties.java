// android.hardware.OverlayProperties -- what the display's hardware composer
// can put straight into an overlay plane without the GPU: which
// dataspace/format combinations it accepts, whether it can mix colour spaces
// across layers, and which colour LUTs it can apply. An app consults it to
// decide whether a given buffer format is worth using.
//
// Mirrors frameworks/base/core/java/android/hardware/OverlayProperties.java
// (android16-release).
//
// EVERY ANSWER HERE IS THE ONE AOSP GIVES WHEN THERE IS NO COMPOSER TO ASK.
// AOSP guards each method with `if (mNativeObject == 0)` and returns
// false/null in that case (:82, :101, :117); Mac.apk composites through
// ANGLE/Metal into one CAMetalLayer with no hardware-composer overlay planes
// at all, so that is exactly our state and those are exactly our answers.
//
// The direction matters: claiming a combination IS supported would send an app
// down a zero-copy overlay path that this compositor cannot honour, and the
// failure would surface as a black or corrupt layer with nothing to explain
// it. Reporting false costs the app only the GPU path it would have taken
// anyway.
package android.hardware;

public final class OverlayProperties implements android.os.Parcelable {

    /** AOSP's constructor takes a native composer-properties pointer. There is
     *  no composer here, so nothing constructs one of these with content; an
     *  app receives an instance from Display/SurfaceControl and asks it
     *  questions, which is what the methods below answer. */
    /* package */ OverlayProperties() { }

    /** AOSP returns null when it has no composer object (:83), not an empty
     *  array. Kept: an app distinguishes "no information" from "no LUTs". */
    public LutProperties[] getLutProperties() {
        return null;
    }

    /** AOSP :101 -- false with no composer. Overlay planes do not exist here. */
    public boolean isCombinationSupported(int dataspace, int format) {
        return false;
    }

    /** AOSP :117 -- false with no composer. */
    public boolean isMixedColorSpacesSupported() {
        return false;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        // AOSP marshals the native composer properties. There are none.
    }

    public static final Creator<OverlayProperties> CREATOR = new Creator<OverlayProperties>() {
        @Override public OverlayProperties createFromParcel(android.os.Parcel in) {
            return new OverlayProperties();
        }
        @Override public OverlayProperties[] newArray(int size) {
            return new OverlayProperties[size];
        }
    };
}
