// android.os.VibrationAttributes -- usage/flags metadata attached to a
// VibrationEffect, the haptics analogue of AudioAttributes.
//
// A Mac has no vibrator, so nothing here ever changes what the hardware does.
// The object itself is still a plain VALUE type (two or three ints), which apps
// build, store, compare and pass around -- so it marshals for real. It used to
// write NOTHING (writeToParcel was `dest.writeValue(this)`, CREATOR was
// `readValue(null)`), which only closed because Parcel detects the
// self-delegation and falls back to its non-AOSP VAL_MACAPK_LIVEREF tag and
// hands the SAME object back. That fallback exists for classes holding a live
// OS resource with no byte form; three ints are not that.
//
// AOSP ref: frameworks/base/core/java/android/os/VibrationAttributes.java
// (android14-release, which is the API level whose USAGE_MEDIA /
// USAGE_ACCESSIBILITY constants this file already carried).
package android.os;

import android.media.AudioAttributes;

import java.util.Objects;

public final class VibrationAttributes implements Parcelable {
    // USAGE_* = (subclass nibble) | USAGE_CLASS_*, exactly as AOSP composes them
    // (frameworks/base/core/java/android/os/VibrationAttributes.java). The four
    // ALARM-class usages were scrambled here (e.g. USAGE_ALARM held 0x21, which
    // is real AOSP's USAGE_RINGTONE) -- confirmed field-by-field against AOSP
    // source and debug/framework_lint.py's constant-value-diff finding. A guest
    // compiled against real AOSP has the CORRECT literal inlined by javac at its
    // own compile time (these are compile-time constants), so on a mismatch our
    // Vibrator/VibrationEffect code comparing against ITS OWN (wrong) field
    // silently misclassifies the usage -- nothing throws.
    public static final int USAGE_UNKNOWN = 0;
    public static final int USAGE_ACCESSIBILITY = 0x42;
    public static final int USAGE_ALARM = 0x11;
    public static final int USAGE_COMMUNICATION_REQUEST = 0x41;
    public static final int USAGE_HARDWARE_FEEDBACK = 0x32;
    public static final int USAGE_MEDIA = 0x13;
    public static final int USAGE_NOTIFICATION = 0x31;
    public static final int USAGE_PHYSICAL_EMULATION = 0x22;
    public static final int USAGE_RINGTONE = 0x21;
    public static final int USAGE_TOUCH = 0x12;
    // AOSP core/java/android/os/VibrationAttributes.java:146
    //     public static final int USAGE_IME_FEEDBACK = 0x50 | USAGE_CLASS_FEEDBACK;
    // USAGE_CLASS_FEEDBACK is 0x2 (declared below), so the value is 0x52.
    public static final int USAGE_IME_FEEDBACK = 0x50 | 0x2;

    public static final int USAGE_CLASS_ALARM = 0x1;
    public static final int USAGE_CLASS_FEEDBACK = 0x2;
    public static final int USAGE_CLASS_MEDIA = 0x3;
    public static final int USAGE_CLASS_UNKNOWN = 0x0;
    public static final int USAGE_CLASS_MASK = 0xf;

    public static final int FLAG_BYPASS_INTERRUPTION_POLICY = 1;

    // API 33+ flags. AOSP (android14) VibrationAttributes.java L172/L187/L199.
    // FLAG_BYPASS_USER_VIBRATION_INTENSITY_OFF is public API; the other two are
    // @hide but participate in FLAG_ALL_SUPPORTED, which is the mask the
    // constructor applies -- so they have to exist for the mask to be right.
    public static final int FLAG_BYPASS_USER_VIBRATION_INTENSITY_OFF = 1 << 1;
    /** @hide */
    public static final int FLAG_INVALIDATE_SETTINGS_CACHE = 1 << 2;
    /** @hide */
    public static final int FLAG_PIPELINED_EFFECT = 1 << 3;
    /** @hide */
    public static final int FLAG_ALL_SUPPORTED =
            FLAG_BYPASS_INTERRUPTION_POLICY | FLAG_BYPASS_USER_VIBRATION_INTENSITY_OFF
                    | FLAG_INVALIDATE_SETTINGS_CACHE | FLAG_PIPELINED_EFFECT;

    /** @hide -- matches every usage, for filtering APIs. */
    public static final int USAGE_FILTER_MATCH_ALL = -1;

    // AudioAttributes' bypass flags are @hide in AOSP (AudioAttributes.java:
    // FLAG_BYPASS_INTERRUPTION_POLICY = 0x1 << 6, FLAG_BYPASS_MUTE = 0x1 << 7)
    // and this tree's AudioAttributes -- another lane's file -- does not declare
    // them. They are named here rather than inlined so the Builder below reads
    // the same as AOSP's; an app can only ever set them through hidden API, so
    // in practice they are zero and the translation is a no-op.
    private static final int AUDIO_FLAG_BYPASS_INTERRUPTION_POLICY = 0x1 << 6;
    private static final int AUDIO_FLAG_BYPASS_MUTE = 0x1 << 7;

    private final int mUsage;
    private final int mOriginalAudioUsage;
    private final int mFlags;

    private VibrationAttributes(int usage, int audioUsage, int flags) {
        mUsage = usage;
        mOriginalAudioUsage = audioUsage;
        // AOSP masks on construction, so an unknown bit never survives into a
        // getFlags() an app reads back.
        mFlags = flags & FLAG_ALL_SUPPORTED;
    }

    /** AOSP createForUsage(int) -- API 33. */
    public static VibrationAttributes createForUsage(int usage) {
        return new VibrationAttributes.Builder().setUsage(usage).build();
    }

    public int getUsage() { return mUsage; }
    public int getUsageClass() { return mUsage & USAGE_CLASS_MASK; }
    public int getFlags() { return mFlags; }
    public boolean isFlagSet(int flag) { return (mFlags & flag) > 0; }

    /**
     * The AudioAttributes usage equivalent to {@link #getUsage()}. @hide in
     * AOSP (@TestApi), mirrored because the field it reads is marshalled and a
     * reader has to be able to observe what was stored.
     */
    public int getAudioUsage() {
        if (mOriginalAudioUsage != AudioAttributes.USAGE_UNKNOWN) {
            // Return the same audio usage the Builder was given.
            return mOriginalAudioUsage;
        }
        switch (mUsage) {
            case USAGE_NOTIFICATION:
                return AudioAttributes.USAGE_NOTIFICATION;
            case USAGE_COMMUNICATION_REQUEST:
                return AudioAttributes.USAGE_VOICE_COMMUNICATION;
            case USAGE_RINGTONE:
                return AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
            case USAGE_TOUCH:
                return AudioAttributes.USAGE_ASSISTANCE_SONIFICATION;
            case USAGE_ALARM:
                return AudioAttributes.USAGE_ALARM;
            case USAGE_ACCESSIBILITY:
                return AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY;
            case USAGE_MEDIA:
                return AudioAttributes.USAGE_MEDIA;
            default:
                return AudioAttributes.USAGE_UNKNOWN;
        }
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mUsage);
        dest.writeInt(mOriginalAudioUsage);
        dest.writeInt(mFlags);
    }

    private VibrationAttributes(Parcel src) {
        mUsage = src.readInt();
        mOriginalAudioUsage = src.readInt();
        mFlags = src.readInt();
    }

    public static final Creator<VibrationAttributes> CREATOR = new Creator<VibrationAttributes>() {
        @Override public VibrationAttributes createFromParcel(Parcel p) {
            return new VibrationAttributes(p);
        }
        @Override public VibrationAttributes[] newArray(int size) {
            return new VibrationAttributes[size];
        }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VibrationAttributes rhs = (VibrationAttributes) o;
        return mUsage == rhs.mUsage && mOriginalAudioUsage == rhs.mOriginalAudioUsage
                && mFlags == rhs.mFlags;
    }

    @Override public int hashCode() {
        return Objects.hash(mUsage, mOriginalAudioUsage, mFlags);
    }

    // AOSP prints AudioAttributes.usageToString(mOriginalAudioUsage); that
    // helper is @hide there and absent from this tree's AudioAttributes (another
    // lane's file), so the raw constant is printed instead. toString is
    // diagnostic only -- no app parses it -- so this is the one place a
    // divergence costs nothing.
    @Override public String toString() {
        return "VibrationAttributes:"
                + " Usage=" + usageToString()
                + " Audio Usage= " + mOriginalAudioUsage
                + " Flags=" + mFlags;
    }

    /** @hide */
    public String usageToString() {
        return usageToString(mUsage);
    }

    /** @hide */
    public static String usageToString(int usage) {
        switch (usage) {
            case USAGE_UNKNOWN:                return "UNKNOWN";
            case USAGE_ALARM:                  return "ALARM";
            case USAGE_ACCESSIBILITY:          return "ACCESSIBILITY";
            case USAGE_RINGTONE:               return "RINGTONE";
            case USAGE_NOTIFICATION:           return "NOTIFICATION";
            case USAGE_COMMUNICATION_REQUEST:  return "COMMUNICATION_REQUEST";
            case USAGE_MEDIA:                  return "MEDIA";
            case USAGE_TOUCH:                  return "TOUCH";
            case USAGE_PHYSICAL_EMULATION:     return "PHYSICAL_EMULATION";
            case USAGE_HARDWARE_FEEDBACK:      return "HARDWARE_FEEDBACK";
            default:                           return "unknown usage " + usage;
        }
    }

    public static final class Builder {
        private int mUsage = USAGE_UNKNOWN;
        private int mOriginalAudioUsage = AudioAttributes.USAGE_UNKNOWN;
        private int mFlags = 0x0;

        public Builder() {}

        public Builder(VibrationAttributes vib) {
            if (vib != null) {
                mUsage = vib.mUsage;
                mOriginalAudioUsage = vib.mOriginalAudioUsage;
                mFlags = vib.mFlags;
            }
        }

        /** AOSP derives BOTH the usage and the flags from the AudioAttributes;
         *  the old stub here accepted the argument and dropped it, so an app
         *  that built a VibrationAttributes from its AudioAttributes got
         *  USAGE_UNKNOWN back instead of the usage it had just described. */
        public Builder(AudioAttributes audio) {
            setUsage(audio);
            setFlags(audio);
        }

        private void setUsage(AudioAttributes audio) {
            mOriginalAudioUsage = audio.getUsage();
            switch (audio.getUsage()) {
                case AudioAttributes.USAGE_NOTIFICATION:
                case AudioAttributes.USAGE_NOTIFICATION_EVENT:
                case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED:
                case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT:
                case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST:
                    mUsage = USAGE_NOTIFICATION;
                    break;
                case AudioAttributes.USAGE_VOICE_COMMUNICATION:
                case AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING:
                case AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
                case AudioAttributes.USAGE_ASSISTANT:
                    mUsage = USAGE_COMMUNICATION_REQUEST;
                    break;
                case AudioAttributes.USAGE_NOTIFICATION_RINGTONE:
                    mUsage = USAGE_RINGTONE;
                    break;
                case AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY:
                    mUsage = USAGE_ACCESSIBILITY;
                    break;
                case AudioAttributes.USAGE_ASSISTANCE_SONIFICATION:
                    mUsage = USAGE_TOUCH;
                    break;
                case AudioAttributes.USAGE_ALARM:
                    mUsage = USAGE_ALARM;
                    break;
                case AudioAttributes.USAGE_MEDIA:
                case AudioAttributes.USAGE_GAME:
                    mUsage = USAGE_MEDIA;
                    break;
                default:
                    mUsage = USAGE_UNKNOWN;
            }
        }

        private void setFlags(AudioAttributes audio) {
            // AOSP reads audio.getAllFlags(); this tree's AudioAttributes.getFlags()
            // is unmasked (its own comment says so), which is the same value.
            final int audioFlags = audio.getFlags();
            if ((audioFlags & AUDIO_FLAG_BYPASS_INTERRUPTION_POLICY) != 0) {
                mFlags |= FLAG_BYPASS_INTERRUPTION_POLICY;
            }
            if ((audioFlags & AUDIO_FLAG_BYPASS_MUTE) != 0) {
                // A muted audio stream translates to the vibration usage having
                // VIBRATION_INTENSITY_OFF set in the user setting.
                mFlags |= FLAG_BYPASS_USER_VIBRATION_INTENSITY_OFF;
            }
        }

        public VibrationAttributes build() {
            return new VibrationAttributes(mUsage, mOriginalAudioUsage, mFlags);
        }

        /** AOSP clears the remembered audio usage: an explicit vibration usage
         *  supersedes whatever an AudioAttributes implied. */
        public Builder setUsage(int usage) {
            mOriginalAudioUsage = AudioAttributes.USAGE_UNKNOWN;
            mUsage = usage;
            return this;
        }

        public Builder setFlags(int flags, int mask) {
            mask &= FLAG_ALL_SUPPORTED;
            mFlags = (mFlags & ~mask) | (flags & mask);
            return this;
        }

        /** AOSP's single-argument overload (@hide): sets ALL supported flags,
         *  overriding anything previously set on this builder -- so the mask is
         *  FLAG_ALL_SUPPORTED, not `flags`. */
        public Builder setFlags(int flags) {
            return setFlags(flags, FLAG_ALL_SUPPORTED);
        }
    }
}
