package android.service.credentials;

import android.content.pm.SigningInfo;
import android.os.Parcel;
import android.os.Parcelable;

/** Identity of the app whose credential request a provider is servicing. API 34.
 *  Source: frameworks/base/core/java/android/service/credentials/CallingAppInfo.java */
public final class CallingAppInfo implements Parcelable {
    private final String mPackageName;
    private final SigningInfo mSigningInfo;
    private final String mOrigin;

    public CallingAppInfo(String packageName, SigningInfo signingInfo) {
        this(packageName, signingInfo, null);
    }

    public CallingAppInfo(String packageName, SigningInfo signingInfo, String origin) {
        mPackageName = packageName; mSigningInfo = signingInfo; mOrigin = origin;
    }

    public String getPackageName() { return mPackageName; }
    public SigningInfo getSigningInfo() { return mSigningInfo; }
    public String getOrigin() { return mOrigin; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeTypedObject(mSigningInfo, flags);
        dest.writeString(mOrigin);
    }

    public static final Parcelable.Creator<CallingAppInfo> CREATOR =
            new Parcelable.Creator<CallingAppInfo>() {
                @Override public CallingAppInfo createFromParcel(Parcel in) {
                    return new CallingAppInfo(in.readString(),
                            in.readTypedObject(SigningInfo.CREATOR), in.readString());
                }
                @Override public CallingAppInfo[] newArray(int size) {
                    return new CallingAppInfo[size];
                }
            };
}
