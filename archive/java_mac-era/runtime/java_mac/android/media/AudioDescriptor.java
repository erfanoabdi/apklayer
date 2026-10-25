// android.media.AudioDescriptor (API 31).
//
// An opaque, standard-defined blob describing a playback/capture capability the
// platform does not otherwise recognise -- an EDID Short Audio Descriptor from
// an HDMI sink, a Speaker Allocation Data Block, a vendor block. Apps get them
// from AudioDeviceInfo.getAudioDescriptors and hand them to a decoder or use
// them to decide whether a passthrough format is available.
//
// This is a REAL implementation, not a device-absent answer. AOSP's class is a
// pure immutable value type with no device access at all, so every method below
// does exactly what AOSP's does, including Parcelable round-tripping. What is
// device-dependent is who PRODUCES one -- see AudioDeviceInfo.getAudioDescriptors,
// which honestly reports none for a built-in speaker.
//
// Mirrored from
// frameworks/base/media/java/android/media/AudioDescriptor.java
// (aosp-mirror/platform_frameworks_base, main): class L35, STANDARD_* L39-51,
// constructor L75-79, getStandard L84, getDescriptor L92, getEncapsulationType
// L110, hashCode L115, equals L120-128, toString L131-140, describeContents
// L143, writeToParcel L148-152, the Parcel constructor L154-158 and CREATOR
// L160-174. Note AOSP declares the class non-final.
package android.media;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Objects;

public class AudioDescriptor implements Parcelable {
    /** The audio standard is not specified. */
    public static final int STANDARD_NONE = 0;
    /** Extended Display Identification Data, for a short audio descriptor. */
    public static final int STANDARD_EDID = 1;
    /** Speaker Allocation Data Block. */
    public static final int STANDARD_SADB = 2;
    /** Vendor-Specific Audio Data Block. */
    public static final int STANDARD_VSADB = 3;

    private final int mStandard;
    private final byte[] mDescriptor;
    private final int mEncapsulationType;

    /** AOSP marks this @SystemApi/@hide but declares it public; kept public
     *  because a hidden-but-public AOSP member is still something app bytecode
     *  can reference, and because AudioDeviceInfo needs to construct one the
     *  moment a device does report descriptors. */
    public AudioDescriptor(int standard, int encapsulationType, byte[] descriptor) {
        mStandard = standard;
        mEncapsulationType = encapsulationType;
        mDescriptor = descriptor;
    }

    /** The standard that defines how getDescriptor() should be parsed. */
    public int getStandard() {
        return mStandard;
    }

    /** The raw capability bytes, encoded per getStandard(). */
    public byte[] getDescriptor() {
        return mDescriptor;
    }

    /** Whether playing to this capability requires encapsulation, and in which
     *  scheme -- one of AudioProfile.AUDIO_ENCAPSULATION_TYPE_*. */
    public int getEncapsulationType() {
        return mEncapsulationType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStandard, mEncapsulationType, Arrays.hashCode(mDescriptor));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AudioDescriptor that = (AudioDescriptor) o;
        return mStandard == that.mStandard
                && mEncapsulationType == that.mEncapsulationType
                && Arrays.equals(mDescriptor, that.mDescriptor);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("standard=" + mStandard);
        sb.append(", encapsulation type=" + mEncapsulationType);
        if (mDescriptor != null && mDescriptor.length > 0) {
            sb.append(", descriptor=").append(Arrays.toString(mDescriptor));
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mStandard);
        dest.writeInt(mEncapsulationType);
        dest.writeByteArray(mDescriptor);
    }

    private AudioDescriptor(Parcel in) {
        mStandard = in.readInt();
        mEncapsulationType = in.readInt();
        mDescriptor = in.createByteArray();
    }

    public static final Parcelable.Creator<AudioDescriptor> CREATOR =
            new Parcelable.Creator<AudioDescriptor>() {
                @Override public AudioDescriptor createFromParcel(Parcel p) {
                    return new AudioDescriptor(p);
                }

                @Override public AudioDescriptor[] newArray(int size) {
                    return new AudioDescriptor[size];
                }
            };
}
