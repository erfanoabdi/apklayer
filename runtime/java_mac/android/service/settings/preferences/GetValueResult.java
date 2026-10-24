package android.service.settings.preferences;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * The answer to a GetValueRequest.
 *
 * See the package note on SettingsPreferenceValue.
 *
 * ⚠ Constants transcribed VERBATIM from /tmp/aosp_api/android16.txt:
 *   RESULT_OK=0, RESULT_UNSUPPORTED=1, RESULT_UNAVAILABLE=2,
 *   RESULT_REQUIRE_APP_PERMISSION=3, RESULT_DISALLOW=4,
 *   RESULT_INVALID_REQUEST=5, RESULT_INTERNAL_ERROR=6.
 * This is NOT the same run as SetValueResult's, which has ten values and puts
 * RESULT_DISABLED at 2.
 *
 * Source: frameworks/base/core/java/android/service/settings/preferences/GetValueResult.java
 */
public final class GetValueResult implements Parcelable {

    public static final int RESULT_OK = 0;
    public static final int RESULT_UNSUPPORTED = 1;
    public static final int RESULT_UNAVAILABLE = 2;
    public static final int RESULT_REQUIRE_APP_PERMISSION = 3;
    public static final int RESULT_DISALLOW = 4;
    public static final int RESULT_INVALID_REQUEST = 5;
    public static final int RESULT_INTERNAL_ERROR = 6;

    private final int mResultCode;
    private final SettingsPreferenceValue mValue;
    private final SettingsPreferenceMetadata mMetadata;

    private GetValueResult(int resultCode, SettingsPreferenceValue value,
            SettingsPreferenceMetadata metadata) {
        mResultCode = resultCode;
        mValue = value;
        mMetadata = metadata;
    }

    public int getResultCode() { return mResultCode; }

    public SettingsPreferenceValue getValue() { return mValue; }

    public SettingsPreferenceMetadata getMetadata() { return mMetadata; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mResultCode);
        dest.writeTypedObject(mValue, flags);
        dest.writeTypedObject(mMetadata, flags);
    }

    public static final Creator<GetValueResult> CREATOR = new Creator<GetValueResult>() {
        @Override public GetValueResult createFromParcel(Parcel in) {
            return new GetValueResult(in.readInt(),
                    in.readTypedObject(SettingsPreferenceValue.CREATOR),
                    in.readTypedObject(SettingsPreferenceMetadata.CREATOR));
        }
        @Override public GetValueResult[] newArray(int size) {
            return new GetValueResult[size];
        }
    };

    public static final class Builder {
        private final int mResultCode;
        private SettingsPreferenceValue mValue;
        private SettingsPreferenceMetadata mMetadata;

        public Builder(int resultCode) { mResultCode = resultCode; }

        public Builder setValue(SettingsPreferenceValue value) {
            mValue = value;
            return this;
        }

        public Builder setMetadata(SettingsPreferenceMetadata metadata) {
            mMetadata = metadata;
            return this;
        }

        public GetValueResult build() {
            return new GetValueResult(mResultCode, mValue, mMetadata);
        }
    }
}
