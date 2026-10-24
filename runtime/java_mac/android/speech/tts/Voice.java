package android.speech.tts;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

// android.speech.tts.Voice -- a characteristics-and-capabilities descriptor for
// one synthesis voice (API 21+). AOSP frameworks/base
// core/java/android/speech/tts/Voice.java. Mac.apk installs no voices, so
// TextToSpeech.getVoices() is always empty and this exists only for the element
// type to resolve. Parcelable + the quality/latency constants mirror AOSP.
public class Voice implements android.os.Parcelable {

    public static final int QUALITY_VERY_LOW = 100;
    public static final int QUALITY_LOW = 200;
    public static final int QUALITY_NORMAL = 300;
    public static final int QUALITY_HIGH = 400;
    public static final int QUALITY_VERY_HIGH = 500;

    public static final int LATENCY_VERY_LOW = 100;
    public static final int LATENCY_LOW = 200;
    public static final int LATENCY_NORMAL = 300;
    public static final int LATENCY_HIGH = 400;
    public static final int LATENCY_VERY_HIGH = 500;

    private final String mName;
    private final Locale mLocale;
    private final int mQuality;
    private final int mLatency;
    private final boolean mRequiresNetworkConnection;
    private final Set<String> mFeatures;

    public Voice(String name, Locale locale, int quality, int latency,
                 boolean requiresNetworkConnection, Set<String> features) {
        mName = name;
        mLocale = locale;
        mQuality = quality;
        mLatency = latency;
        mRequiresNetworkConnection = requiresNetworkConnection;
        mFeatures = features != null ? features : Collections.<String>emptySet();
    }

    public String getName() { return mName; }
    public Locale getLocale() { return mLocale; }
    public int getQuality() { return mQuality; }
    public int getLatency() { return mLatency; }
    public boolean isNetworkConnectionRequired() { return mRequiresNetworkConnection; }
    public Set<String> getFeatures() { return mFeatures; }

    @Override public String toString() {
        return "Voice[Name: " + mName + ", locale: " + mLocale + ", quality: " + mQuality
            + ", latency: " + mLatency + ", requiresNetwork: " + mRequiresNetworkConnection
            + ", features: " + mFeatures + "]";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeSerializable(mLocale);
        dest.writeInt(mQuality);
        dest.writeInt(mLatency);
        dest.writeInt(mRequiresNetworkConnection ? 1 : 0);
    }

    public static final android.os.Parcelable.Creator<Voice> CREATOR =
        new android.os.Parcelable.Creator<Voice>() {
            @Override public Voice createFromParcel(android.os.Parcel in) {
                String name = in.readString();
                Locale locale = (Locale) in.readSerializable();
                int quality = in.readInt();
                int latency = in.readInt();
                boolean requiresNetwork = in.readInt() != 0;
                return new Voice(name, locale, quality, latency, requiresNetwork, null);
            }
            @Override public Voice[] newArray(int size) {
                return new Voice[size];
            }
        };
}
