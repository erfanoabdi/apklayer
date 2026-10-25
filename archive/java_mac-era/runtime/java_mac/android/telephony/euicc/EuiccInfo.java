package android.telephony.euicc;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Information about an embedded SIM (eUICC).
 *
 * There is no eUICC on a Mac, so EuiccManager.getEuiccInfo() returns null and
 * this type is never produced by the framework. Immutable value holder.
 *
 * Source: frameworks/base/telephony/java/android/telephony/euicc/EuiccInfo.java
 */
public final class EuiccInfo implements Parcelable {

    private final String mOsVersion;

    public EuiccInfo(String osVersion) { mOsVersion = osVersion; }

    private EuiccInfo(Parcel in) { mOsVersion = in.readString(); }

    public String getOsVersion() { return mOsVersion; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) { dest.writeString(mOsVersion); }

    public static final Creator<EuiccInfo> CREATOR = new Creator<EuiccInfo>() {
        @Override public EuiccInfo createFromParcel(Parcel in) { return new EuiccInfo(in); }
        @Override public EuiccInfo[] newArray(int size) { return new EuiccInfo[size]; }
    };
}
