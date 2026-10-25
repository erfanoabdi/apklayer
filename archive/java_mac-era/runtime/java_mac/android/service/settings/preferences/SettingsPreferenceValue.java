package android.service.settings.preferences;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A single Settings preference's value, tagged with which primitive it holds.
 *
 * PACKAGE NOTE -- applies to android.service.settings.preferences. This API 36
 * package ("Settings catalyst") lets a privileged app read and write the
 * device's Settings preferences through a bound SettingsPreferenceService.
 * Mac.apk is not an Android Settings host: there is no Settings app to bind,
 * and a guest does not get to write the host's system settings. So the SERVICE
 * side is never bound and the CLIENT side reports failure -- see
 * SettingsPreferenceServiceClient, which reports the AOSP "no service" outcome
 * rather than silently hanging.
 *
 * The request/result objects are ordinary value types with builders, and those
 * are faithful.
 *
 * ⚠ Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt.
 * The TYPE_* run here is NOT in the obvious order: BOOLEAN=0, LONG=1,
 * DOUBLE=2, STRING=3, INT=4. LONG before DOUBLE before STRING before INT is
 * exactly the ordering a from-memory transcription gets wrong.
 *
 * Source: frameworks/base/core/java/android/service/settings/preferences/SettingsPreferenceValue.java
 */
public final class SettingsPreferenceValue implements Parcelable {

    public static final int TYPE_BOOLEAN = 0;
    public static final int TYPE_LONG = 1;
    public static final int TYPE_DOUBLE = 2;
    public static final int TYPE_STRING = 3;
    public static final int TYPE_INT = 4;

    private final int mType;
    private final boolean mBooleanValue;
    private final long mLongValue;
    private final double mDoubleValue;
    private final String mStringValue;
    private final int mIntValue;

    private SettingsPreferenceValue(Builder b) {
        mType = b.mType;
        mBooleanValue = b.mBooleanValue;
        mLongValue = b.mLongValue;
        mDoubleValue = b.mDoubleValue;
        mStringValue = b.mStringValue;
        mIntValue = b.mIntValue;
    }

    public int getType() { return mType; }

    public boolean getBooleanValue() { return mBooleanValue; }

    public long getLongValue() { return mLongValue; }

    public double getDoubleValue() { return mDoubleValue; }

    public String getStringValue() { return mStringValue; }

    public int getIntValue() { return mIntValue; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeBoolean(mBooleanValue);
        dest.writeLong(mLongValue);
        dest.writeDouble(mDoubleValue);
        dest.writeString(mStringValue);
        dest.writeInt(mIntValue);
    }

    @Override public String toString() {
        return "SettingsPreferenceValue[type=" + mType + "]";
    }

    public static final Creator<SettingsPreferenceValue> CREATOR =
            new Creator<SettingsPreferenceValue>() {
        @Override public SettingsPreferenceValue createFromParcel(Parcel in) {
            Builder b = new Builder(in.readInt());
            b.setBooleanValue(in.readBoolean());
            b.setLongValue(in.readLong());
            b.setDoubleValue(in.readDouble());
            b.setStringValue(in.readString());
            b.setIntValue(in.readInt());
            return b.build();
        }
        @Override public SettingsPreferenceValue[] newArray(int size) {
            return new SettingsPreferenceValue[size];
        }
    };

    public static final class Builder {
        private final int mType;
        private boolean mBooleanValue;
        private long mLongValue;
        private double mDoubleValue;
        private String mStringValue;
        private int mIntValue;

        public Builder(int type) { mType = type; }

        public Builder setBooleanValue(boolean booleanValue) {
            mBooleanValue = booleanValue;
            return this;
        }

        public Builder setLongValue(long longValue) { mLongValue = longValue; return this; }

        public Builder setDoubleValue(double doubleValue) {
            mDoubleValue = doubleValue;
            return this;
        }

        public Builder setStringValue(String stringValue) {
            mStringValue = stringValue;
            return this;
        }

        public Builder setIntValue(int intValue) { mIntValue = intValue; return this; }

        public SettingsPreferenceValue build() { return new SettingsPreferenceValue(this); }
    }
}
