package android.service.settings.preferences;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * The answer to a SetValueRequest.
 *
 * See the package note on SettingsPreferenceValue.
 *
 * ⚠ Constants transcribed VERBATIM from /tmp/aosp_api/android16.txt:
 *   RESULT_OK=0, RESULT_UNSUPPORTED=1, RESULT_DISABLED=2, RESULT_RESTRICTED=3,
 *   RESULT_UNAVAILABLE=4, RESULT_REQUIRE_APP_PERMISSION=5,
 *   RESULT_REQUIRE_USER_CONSENT=6, RESULT_DISALLOW=7,
 *   RESULT_INVALID_REQUEST=8, RESULT_INTERNAL_ERROR=9.
 * ⚠ These OVERLAP GetValueResult's names with DIFFERENT values -- UNAVAILABLE
 * is 2 there and 4 here, DISALLOW is 4 there and 7 here. Copying one run onto
 * the other is the failure mode this note exists to prevent.
 *
 * Source: frameworks/base/core/java/android/service/settings/preferences/SetValueResult.java
 */
public final class SetValueResult implements Parcelable {

    public static final int RESULT_OK = 0;
    public static final int RESULT_UNSUPPORTED = 1;
    public static final int RESULT_DISABLED = 2;
    public static final int RESULT_RESTRICTED = 3;
    public static final int RESULT_UNAVAILABLE = 4;
    public static final int RESULT_REQUIRE_APP_PERMISSION = 5;
    public static final int RESULT_REQUIRE_USER_CONSENT = 6;
    public static final int RESULT_DISALLOW = 7;
    public static final int RESULT_INVALID_REQUEST = 8;
    public static final int RESULT_INTERNAL_ERROR = 9;

    private final int mResultCode;

    private SetValueResult(int resultCode) { mResultCode = resultCode; }

    public int getResultCode() { return mResultCode; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mResultCode);
    }

    public static final Creator<SetValueResult> CREATOR = new Creator<SetValueResult>() {
        @Override public SetValueResult createFromParcel(Parcel in) {
            return new SetValueResult(in.readInt());
        }
        @Override public SetValueResult[] newArray(int size) {
            return new SetValueResult[size];
        }
    };

    public static final class Builder {
        private final int mResultCode;

        public Builder(int resultCode) { mResultCode = resultCode; }

        public SetValueResult build() { return new SetValueResult(mResultCode); }
    }
}
