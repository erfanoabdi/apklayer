package android.media;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Describes how an app would like the platform mixer configured for a given
 * output device (API 34) -- the format to mix at, and whether the mixer should
 * be bypassed entirely for bit-perfect delivery.
 *
 * This is app-supplied REQUEST data; whether the request can be honoured is
 * answered separately by AudioManager.getSupportedMixerAttributes(), which
 * reports none on this host (CoreAudio owns the mix and Mac.apk cannot hand an
 * app an exclusive-mode bit-perfect path). So an app can build and inspect these
 * objects exactly as on Android, and the platform honestly says it supports
 * none.
 *
 * Source: frameworks/base/media/java/android/media/AudioMixerAttributes.java
 * (android16-release).
 */
public final class AudioMixerAttributes implements Parcelable {

    public static final int MIXER_BEHAVIOR_DEFAULT = 0;
    public static final int MIXER_BEHAVIOR_BIT_PERFECT = 1;

    private final AudioFormat mFormat;
    private final int mMixerBehavior;

    AudioMixerAttributes(AudioFormat format, int mixerBehavior) {
        mFormat = format;
        mMixerBehavior = mixerBehavior;
    }

    public AudioFormat getFormat() { return mFormat; }
    public int getMixerBehavior() { return mMixerBehavior; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mFormat, flags);
        dest.writeInt(mMixerBehavior);
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AudioMixerAttributes)) return false;
        AudioMixerAttributes o = (AudioMixerAttributes) obj;
        return Objects.equals(mFormat, o.mFormat) && mMixerBehavior == o.mMixerBehavior;
    }

    @Override public int hashCode() { return Objects.hash(mFormat, mMixerBehavior); }

    @Override public String toString() {
        return "AudioMixerAttributes{ format=" + mFormat + ", mixerBehavior=" + mMixerBehavior
                + " }";
    }

    public static final Parcelable.Creator<AudioMixerAttributes> CREATOR =
            new Parcelable.Creator<AudioMixerAttributes>() {
        @Override public AudioMixerAttributes createFromParcel(Parcel in) {
            AudioFormat format = in.readTypedObject(AudioFormat.CREATOR);
            return new AudioMixerAttributes(format, in.readInt());
        }
        @Override public AudioMixerAttributes[] newArray(int size) {
            return new AudioMixerAttributes[size];
        }
    };

    /** AOSP default mixer behavior is MIXER_BEHAVIOR_DEFAULT
     *  (AudioMixerAttributes.java L150-160). */
    public static final class Builder {
        private final AudioFormat mFormat;
        private int mMixerBehavior = MIXER_BEHAVIOR_DEFAULT;

        public Builder(AudioFormat format) {
            mFormat = Objects.requireNonNull(format, "format must not be null");
        }

        /** AOSP rejects anything outside the two defined behaviours
         *  (AudioMixerAttributes.java L173-180). */
        public Builder setMixerBehavior(int mixerBehavior) {
            if (mixerBehavior != MIXER_BEHAVIOR_DEFAULT
                    && mixerBehavior != MIXER_BEHAVIOR_BIT_PERFECT) {
                throw new IllegalArgumentException("Unsupported mixer behavior=" + mixerBehavior);
            }
            mMixerBehavior = mixerBehavior;
            return this;
        }

        public AudioMixerAttributes build() {
            return new AudioMixerAttributes(mFormat, mMixerBehavior);
        }
    }
}
