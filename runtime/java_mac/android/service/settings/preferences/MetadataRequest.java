package android.service.settings.preferences;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Asks a SettingsPreferenceService for every preference it exposes. AOSP's
 * public API gives it NO fields at all -- the builder exists so future
 * arguments can be added without breaking callers.
 *
 * See the package note on SettingsPreferenceValue.
 *
 * Source: frameworks/base/core/java/android/service/settings/preferences/MetadataRequest.java
 */
public final class MetadataRequest implements Parcelable {

    private MetadataRequest() { }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) { }

    public static final Creator<MetadataRequest> CREATOR = new Creator<MetadataRequest>() {
        @Override public MetadataRequest createFromParcel(Parcel in) {
            return new MetadataRequest();
        }
        @Override public MetadataRequest[] newArray(int size) {
            return new MetadataRequest[size];
        }
    };

    public static final class Builder {
        public Builder() { }

        public MetadataRequest build() { return new MetadataRequest(); }
    }
}
