package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;

/**
 * Which bands/channels of one radio-access network a network scan should cover.
 *
 * Immutable value type; no radio needed to construct one. Mac.apk's
 * requestNetworkScan reports ERROR_UNSUPPORTED rather than pretending to scan.
 *
 * Source: frameworks/base/telephony/java/android/telephony/RadioAccessSpecifier.java
 */
public final class RadioAccessSpecifier implements Parcelable {

    private final int mRadioAccessNetwork;
    private final int[] mBands;
    private final int[] mChannels;

    public RadioAccessSpecifier(int ran, int[] bands, int[] channels) {
        mRadioAccessNetwork = ran;
        mBands = bands == null ? null : bands.clone();
        mChannels = channels == null ? null : channels.clone();
    }

    private RadioAccessSpecifier(Parcel in) {
        mRadioAccessNetwork = in.readInt();
        mBands = in.createIntArray();
        mChannels = in.createIntArray();
    }

    public int getRadioAccessNetwork() { return mRadioAccessNetwork; }

    public int[] getBands() { return mBands == null ? null : mBands.clone(); }

    public int[] getChannels() { return mChannels == null ? null : mChannels.clone(); }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRadioAccessNetwork);
        dest.writeIntArray(mBands);
        dest.writeIntArray(mChannels);
    }

    public static final Creator<RadioAccessSpecifier> CREATOR =
            new Creator<RadioAccessSpecifier>() {
        @Override public RadioAccessSpecifier createFromParcel(Parcel in) {
            return new RadioAccessSpecifier(in);
        }
        @Override public RadioAccessSpecifier[] newArray(int size) {
            return new RadioAccessSpecifier[size];
        }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RadioAccessSpecifier)) return false;
        RadioAccessSpecifier ras = (RadioAccessSpecifier) o;
        return mRadioAccessNetwork == ras.mRadioAccessNetwork
                && Arrays.equals(mBands, ras.mBands)
                && Arrays.equals(mChannels, ras.mChannels);
    }

    @Override public int hashCode() {
        return ((mRadioAccessNetwork * 31) + Arrays.hashCode(mBands)) * 31
                + Arrays.hashCode(mChannels);
    }

    @Override public String toString() {
        return "RadioAccessSpecifier[mRadioAccessNetwork=" + mRadioAccessNetwork
                + ", mBands=" + Arrays.toString(mBands)
                + ", mChannels=" + Arrays.toString(mChannels) + "]";
    }
}
