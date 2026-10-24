package android.service.settings.preferences;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Asks a SettingsPreferenceService for one preference's value, addressed by
 * screen key + preference key.
 *
 * See the package note on SettingsPreferenceValue.
 *
 * Source: frameworks/base/core/java/android/service/settings/preferences/GetValueRequest.java
 */
public final class GetValueRequest implements Parcelable {

    private final String mScreenKey;
    private final String mPreferenceKey;

    private GetValueRequest(String screenKey, String preferenceKey) {
        mScreenKey = screenKey;
        mPreferenceKey = preferenceKey;
    }

    public String getScreenKey() { return mScreenKey; }

    public String getPreferenceKey() { return mPreferenceKey; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mScreenKey);
        dest.writeString(mPreferenceKey);
    }

    public static final Creator<GetValueRequest> CREATOR = new Creator<GetValueRequest>() {
        @Override public GetValueRequest createFromParcel(Parcel in) {
            return new GetValueRequest(in.readString(), in.readString());
        }
        @Override public GetValueRequest[] newArray(int size) {
            return new GetValueRequest[size];
        }
    };

    public static final class Builder {
        private final String mScreenKey;
        private final String mPreferenceKey;

        public Builder(String screenKey, String preferenceKey) {
            if (screenKey == null) throw new NullPointerException("screenKey");
            if (preferenceKey == null) throw new NullPointerException("preferenceKey");
            mScreenKey = screenKey;
            mPreferenceKey = preferenceKey;
        }

        public GetValueRequest build() {
            return new GetValueRequest(mScreenKey, mPreferenceKey);
        }
    }
}
