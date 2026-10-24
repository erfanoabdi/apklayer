package android.service.settings.preferences;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The answer to a MetadataRequest.
 *
 * See the package note on SettingsPreferenceValue.
 *
 * ⚠ Constants transcribed VERBATIM: RESULT_OK=0, RESULT_UNSUPPORTED=1,
 * RESULT_INTERNAL_ERROR=2. Note this is a DIFFERENT and SHORTER run than
 * GetValueResult's and SetValueResult's -- the three classes do NOT share a
 * result enum, which is exactly the trap in this package.
 *
 * Source: frameworks/base/core/java/android/service/settings/preferences/MetadataResult.java
 */
public final class MetadataResult implements Parcelable {

    public static final int RESULT_OK = 0;
    public static final int RESULT_UNSUPPORTED = 1;
    public static final int RESULT_INTERNAL_ERROR = 2;

    private final int mResultCode;
    private final List<SettingsPreferenceMetadata> mMetadataList;

    private MetadataResult(int resultCode, List<SettingsPreferenceMetadata> metadataList) {
        mResultCode = resultCode;
        mMetadataList = metadataList;
    }

    public int getResultCode() { return mResultCode; }

    /** Never null in AOSP -- an empty list on a failure code. */
    public List<SettingsPreferenceMetadata> getMetadataList() { return mMetadataList; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mResultCode);
        dest.writeTypedList(mMetadataList);
    }

    public static final Creator<MetadataResult> CREATOR = new Creator<MetadataResult>() {
        @Override public MetadataResult createFromParcel(Parcel in) {
            final Builder b = new Builder(in.readInt());
            List<SettingsPreferenceMetadata> list =
                    in.createTypedArrayList(SettingsPreferenceMetadata.CREATOR);
            if (list != null) b.setMetadataList(list);
            return b.build();
        }
        @Override public MetadataResult[] newArray(int size) {
            return new MetadataResult[size];
        }
    };

    public static final class Builder {
        private final int mResultCode;
        private List<SettingsPreferenceMetadata> mMetadataList = Collections.emptyList();

        public Builder(int resultCode) { mResultCode = resultCode; }

        public Builder setMetadataList(List<SettingsPreferenceMetadata> metadataList) {
            if (metadataList == null) throw new NullPointerException("metadataList");
            mMetadataList = new ArrayList<SettingsPreferenceMetadata>(metadataList);
            return this;
        }

        public MetadataResult build() {
            return new MetadataResult(mResultCode, mMetadataList);
        }
    }
}
