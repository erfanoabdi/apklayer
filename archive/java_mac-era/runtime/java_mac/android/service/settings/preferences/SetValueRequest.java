package android.service.settings.preferences;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Asks a SettingsPreferenceService to WRITE one preference.
 *
 * See the package note on SettingsPreferenceValue -- and note specifically
 * that Mac.apk never carries this request to anything: a guest app does not get
 * to write the host's system settings, and there is no Android Settings host
 * to write to in the first place. The object itself is a plain value type.
 *
 * Source: frameworks/base/core/java/android/service/settings/preferences/SetValueRequest.java
 */
public final class SetValueRequest implements Parcelable {

    private final String mScreenKey;
    private final String mPreferenceKey;
    private final SettingsPreferenceValue mPreferenceValue;

    private SetValueRequest(String screenKey, String preferenceKey,
            SettingsPreferenceValue preferenceValue) {
        mScreenKey = screenKey;
        mPreferenceKey = preferenceKey;
        mPreferenceValue = preferenceValue;
    }

    public String getScreenKey() { return mScreenKey; }

    public String getPreferenceKey() { return mPreferenceKey; }

    public SettingsPreferenceValue getPreferenceValue() { return mPreferenceValue; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mScreenKey);
        dest.writeString(mPreferenceKey);
        dest.writeTypedObject(mPreferenceValue, flags);
    }

    public static final Creator<SetValueRequest> CREATOR = new Creator<SetValueRequest>() {
        @Override public SetValueRequest createFromParcel(Parcel in) {
            return new SetValueRequest(in.readString(), in.readString(),
                    in.readTypedObject(SettingsPreferenceValue.CREATOR));
        }
        @Override public SetValueRequest[] newArray(int size) {
            return new SetValueRequest[size];
        }
    };

    public static final class Builder {
        private final String mScreenKey;
        private final String mPreferenceKey;
        private final SettingsPreferenceValue mPreferenceValue;

        public Builder(String screenKey, String preferenceKey,
                SettingsPreferenceValue preferenceValue) {
            if (screenKey == null) throw new NullPointerException("screenKey");
            if (preferenceKey == null) throw new NullPointerException("preferenceKey");
            if (preferenceValue == null) throw new NullPointerException("preferenceValue");
            mScreenKey = screenKey;
            mPreferenceKey = preferenceKey;
            mPreferenceValue = preferenceValue;
        }

        public SetValueRequest build() {
            return new SetValueRequest(mScreenKey, mPreferenceKey, mPreferenceValue);
        }
    }
}
