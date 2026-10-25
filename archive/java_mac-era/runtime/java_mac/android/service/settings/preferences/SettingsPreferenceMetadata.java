package android.service.settings.preferences;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything about one Settings preference except its value: where it lives,
 * what it is called, whether it can be read or written and under what
 * permissions.
 *
 * See the package note on SettingsPreferenceValue.
 *
 * ⚠ Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt:
 *   NO_SENSITIVITY = 0, EXPECT_POST_CONFIRMATION = 1, DEEPLINK_ONLY = 2,
 *   NO_DIRECT_ACCESS = 3.
 *
 * Source: frameworks/base/core/java/android/service/settings/preferences/SettingsPreferenceMetadata.java
 */
public final class SettingsPreferenceMetadata implements Parcelable {

    public static final int NO_SENSITIVITY = 0;
    public static final int EXPECT_POST_CONFIRMATION = 1;
    public static final int DEEPLINK_ONLY = 2;
    public static final int NO_DIRECT_ACCESS = 3;

    private final String mScreenKey;
    private final String mKey;
    private final String mTitle;
    private final String mSummary;
    private final List<String> mReadPermissions;
    private final List<String> mWritePermissions;
    private final boolean mEnabled;
    private final boolean mAvailable;
    private final boolean mWritable;
    private final boolean mRestricted;
    private final int mSensitivity;
    private final Intent mLaunchIntent;
    private final Bundle mExtras;

    private SettingsPreferenceMetadata(Builder b) {
        mScreenKey = b.mScreenKey;
        mKey = b.mKey;
        mTitle = b.mTitle;
        mSummary = b.mSummary;
        mReadPermissions = b.mReadPermissions;
        mWritePermissions = b.mWritePermissions;
        mEnabled = b.mEnabled;
        mAvailable = b.mAvailable;
        mWritable = b.mWritable;
        mRestricted = b.mRestricted;
        mSensitivity = b.mSensitivity;
        mLaunchIntent = b.mLaunchIntent;
        mExtras = b.mExtras;
    }

    public String getScreenKey() { return mScreenKey; }

    public String getKey() { return mKey; }

    public String getTitle() { return mTitle; }

    public String getSummary() { return mSummary; }

    /** Never null in AOSP -- an empty list when no permission is needed. */
    public List<String> getReadPermissions() { return mReadPermissions; }

    public List<String> getWritePermissions() { return mWritePermissions; }

    public boolean isEnabled() { return mEnabled; }

    public boolean isAvailable() { return mAvailable; }

    public boolean isWritable() { return mWritable; }

    public boolean isRestricted() { return mRestricted; }

    public int getWriteSensitivity() { return mSensitivity; }

    public Intent getLaunchIntent() { return mLaunchIntent; }

    public Bundle getExtras() { return mExtras; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mScreenKey);
        dest.writeString(mKey);
        dest.writeString(mTitle);
        dest.writeString(mSummary);
        dest.writeStringList(mReadPermissions);
        dest.writeStringList(mWritePermissions);
        dest.writeBoolean(mEnabled);
        dest.writeBoolean(mAvailable);
        dest.writeBoolean(mWritable);
        dest.writeBoolean(mRestricted);
        dest.writeInt(mSensitivity);
        dest.writeTypedObject(mLaunchIntent, flags);
        dest.writeBundle(mExtras);
    }

    @Override public String toString() {
        return "SettingsPreferenceMetadata[" + mScreenKey + "/" + mKey + "]";
    }

    public static final Creator<SettingsPreferenceMetadata> CREATOR =
            new Creator<SettingsPreferenceMetadata>() {
        @Override public SettingsPreferenceMetadata createFromParcel(Parcel in) {
            final Builder b = new Builder(in.readString(), in.readString());
            b.setTitle(in.readString());
            b.setSummary(in.readString());
            List<String> read = in.createStringArrayList();
            if (read != null) b.setReadPermissions(read);
            List<String> write = in.createStringArrayList();
            if (write != null) b.setWritePermissions(write);
            b.setEnabled(in.readBoolean());
            b.setAvailable(in.readBoolean());
            b.setWritable(in.readBoolean());
            b.setRestricted(in.readBoolean());
            b.setWriteSensitivity(in.readInt());
            b.setLaunchIntent(in.readTypedObject(Intent.CREATOR));
            Bundle extras = in.readBundle();
            if (extras != null) b.setExtras(extras);
            return b.build();
        }
        @Override public SettingsPreferenceMetadata[] newArray(int size) {
            return new SettingsPreferenceMetadata[size];
        }
    };

    public static final class Builder {
        private final String mScreenKey;
        private final String mKey;
        private String mTitle;
        private String mSummary;
        private List<String> mReadPermissions = Collections.emptyList();
        private List<String> mWritePermissions = Collections.emptyList();
        private boolean mEnabled;
        private boolean mAvailable;
        private boolean mWritable;
        private boolean mRestricted;
        private int mSensitivity = NO_SENSITIVITY;
        private Intent mLaunchIntent;
        private Bundle mExtras = Bundle.EMPTY;

        public Builder(String screenKey, String key) {
            if (screenKey == null) throw new NullPointerException("screenKey");
            if (key == null) throw new NullPointerException("key");
            mScreenKey = screenKey;
            mKey = key;
        }

        public Builder setTitle(String title) { mTitle = title; return this; }

        public Builder setSummary(String summary) { mSummary = summary; return this; }

        public Builder setReadPermissions(List<String> readPermissions) {
            if (readPermissions == null) throw new NullPointerException("readPermissions");
            mReadPermissions = new ArrayList<String>(readPermissions);
            return this;
        }

        public Builder setWritePermissions(List<String> writePermissions) {
            if (writePermissions == null) throw new NullPointerException("writePermissions");
            mWritePermissions = new ArrayList<String>(writePermissions);
            return this;
        }

        public Builder setEnabled(boolean enabled) { mEnabled = enabled; return this; }

        public Builder setAvailable(boolean available) {
            mAvailable = available;
            return this;
        }

        public Builder setWritable(boolean writable) { mWritable = writable; return this; }

        public Builder setRestricted(boolean restricted) {
            mRestricted = restricted;
            return this;
        }

        public Builder setWriteSensitivity(int sensitivity) {
            mSensitivity = sensitivity;
            return this;
        }

        public Builder setLaunchIntent(Intent launchIntent) {
            mLaunchIntent = launchIntent;
            return this;
        }

        public Builder setExtras(Bundle extras) {
            if (extras == null) throw new NullPointerException("extras");
            mExtras = extras;
            return this;
        }

        public SettingsPreferenceMetadata build() {
            return new SettingsPreferenceMetadata(this);
        }
    }
}
