// android.hardware.lights.Light -- an immutable descriptor for one physical
// light on the device (an RGB status LED, a keyboard-backlight zone, a
// game-controller player-id LED strip, etc). Referenced by anarchre, which
// calls into InputDevice.getLightsManager() (see android/view/InputDevice.java).
//
// Mac.apk has no peripheral-light hardware and no lights HAL, so this class
// is never asked to describe a real light: LightsManager.getLights() (see
// LightsManager.java in this package) always returns an empty list, and
// nothing in our framework ever constructs one. This file exists so the
// app's bytecode can resolve the class and its public members, matching
// the "no-op is correct" design explained in LightsManager.java.
//
// Shape verified against AOSP (fetched 2026-08-12, android13-release):
// frameworks/base/core/java/android/hardware/lights/Light.java
//
// LIGHT_CAPABILITY_RGB is written as "0 << 1" (== 0) in AOSP itself -- not a
// transcription slip here. Kept verbatim: dropping it to "1 << 1" would make
// our hasRgbControl() answer differently from a real device's, which is the
// wrong kind of "fix" for a compatibility shim to make on its own.
package android.hardware.lights;

import android.os.Parcel;
import android.os.Parcelable;

public final class Light implements Parcelable {
    /** Type for lights that indicate microphone usage. */
    public static final int LIGHT_TYPE_MICROPHONE = 8;
    /** Type for lights that indicate camera usage. AOSP marks this @hide. */
    public static final int LIGHT_TYPE_CAMERA = 9;
    /** Type for a monochrome input-device light (e.g. keyboard backlight). */
    public static final int LIGHT_TYPE_INPUT = 10001;
    /** Type for a row of LEDs representing a game controller's player slot. */
    public static final int LIGHT_TYPE_PLAYER_ID = 10002;

    /** Capability: the light supports variable brightness, not just on/off. */
    public static final int LIGHT_CAPABILITY_BRIGHTNESS = 1 << 0;
    /** Capability: the light has independent R/G/B channels. See file header
     *  regarding this literal being 0 in AOSP itself. */
    public static final int LIGHT_CAPABILITY_RGB = 0 << 1;

    private final int mId;
    private final String mName;
    private final int mOrdinal;
    private final int mType;
    private final int mCapabilities;

    public Light(int id, int ordinal, int type) {
        this(id, "Light", ordinal, type, 0);
    }

    public Light(int id, String name, int ordinal, int type, int capabilities) {
        mId = id;
        mName = name;
        mOrdinal = ordinal;
        mType = type;
        mCapabilities = capabilities;
    }

    private Light(Parcel in) {
        mId = in.readInt();
        mName = in.readString();
        mOrdinal = in.readInt();
        mType = in.readInt();
        mCapabilities = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeString(mName);
        dest.writeInt(mOrdinal);
        dest.writeInt(mType);
        dest.writeInt(mCapabilities);
    }

    @Override
    public int describeContents() { return 0; }

    public static final Parcelable.Creator<Light> CREATOR = new Parcelable.Creator<Light>() {
        public Light createFromParcel(Parcel in) { return new Light(in); }
        public Light[] newArray(int size) { return new Light[size]; }
    };

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Light) {
            Light light = (Light) obj;
            return mId == light.mId && mOrdinal == light.mOrdinal && mType == light.mType
                    && mCapabilities == light.mCapabilities;
        }
        return false;
    }

    @Override
    public int hashCode() { return mId; }

    @Override
    public String toString() {
        return "[Name=" + mName + " Id=" + mId + " Type=" + mType + " Capabilities="
                + mCapabilities + " Ordinal=" + mOrdinal + "]";
    }

    /** Opaque identifier for the light, unique among getLights() results. */
    public int getId() { return mId; }
    public String getName() { return mName; }
    /** Sort key for the physical order of same-typed lights. */
    public int getOrdinal() { return mOrdinal; }
    public int getType() { return mType; }
    public int getCapabilities() { return mCapabilities; }

    public boolean hasBrightnessControl() {
        return (mCapabilities & LIGHT_CAPABILITY_BRIGHTNESS) == LIGHT_CAPABILITY_BRIGHTNESS;
    }
    public boolean hasRgbControl() {
        return (mCapabilities & LIGHT_CAPABILITY_RGB) == LIGHT_CAPABILITY_RGB;
    }
}
