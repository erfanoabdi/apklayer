// android.bluetooth.le.ScanSettings -- configuration for a BLE scan.
//
// A REAL, complete immutable value type. Mac.apk never scans (see
// BluetoothLeScanner), but the settings object is pure data, so it stores and
// reports everything it is given.
//
// (The previous version's Builder ACCEPTED setLegacy(), setPhy(),
// setMatchMode() and setNumOfMatches() and stored none of them, and the class
// had no getters for them at all -- so an app configuring an extended-PHY scan
// could not read back what it had asked for. Nothing about the missing radio
// required that; it is fixed here.)
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/le/ScanSettings.java.
package android.bluetooth.le;

import android.os.Parcel;
import android.os.Parcelable;

public final class ScanSettings implements Parcelable {

    public static final int SCAN_MODE_OPPORTUNISTIC = -1;
    public static final int SCAN_MODE_LOW_POWER = 0;
    public static final int SCAN_MODE_BALANCED = 1;
    public static final int SCAN_MODE_LOW_LATENCY = 2;

    public static final int CALLBACK_TYPE_ALL_MATCHES = 1;
    public static final int CALLBACK_TYPE_FIRST_MATCH = 2;
    public static final int CALLBACK_TYPE_MATCH_LOST = 4;

    public static final int MATCH_MODE_AGGRESSIVE = 1;
    public static final int MATCH_MODE_STICKY = 2;

    public static final int MATCH_NUM_ONE_ADVERTISEMENT = 1;
    public static final int MATCH_NUM_FEW_ADVERTISEMENT = 2;
    public static final int MATCH_NUM_MAX_ADVERTISEMENT = 3;

    /** AOSP: scan on every PHY the controller supports. */
    public static final int PHY_LE_ALL_SUPPORTED = 255;

    private final int mScanMode;
    private final int mCallbackType;
    private final int mScanResultType;
    private final long mReportDelayMillis;
    private final int mMatchMode;
    private final int mNumOfMatchesPerFilter;
    private final boolean mLegacy;
    private final int mPhy;

    private ScanSettings(int scanMode, int callbackType, int scanResultType,
            long reportDelayMillis, int matchMode, int numOfMatchesPerFilter,
            boolean legacy, int phy) {
        mScanMode = scanMode;
        mCallbackType = callbackType;
        mScanResultType = scanResultType;
        mReportDelayMillis = reportDelayMillis;
        mMatchMode = matchMode;
        mNumOfMatchesPerFilter = numOfMatchesPerFilter;
        mLegacy = legacy;
        mPhy = phy;
    }

    public int getScanMode() { return mScanMode; }
    public int getCallbackType() { return mCallbackType; }
    public long getReportDelayMillis() { return mReportDelayMillis; }

    /** AOSP: SCAN_RESULT_TYPE_FULL (0) or ABBREVIATED (1). @hide-configurable,
     *  so it is always FULL here, which is AOSP's default too. */
    public int getScanResultType() { return mScanResultType; }

    public boolean getLegacy() { return mLegacy; }

    public int getPhy() { return mPhy; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mScanMode);
        dest.writeInt(mCallbackType);
        dest.writeInt(mScanResultType);
        dest.writeLong(mReportDelayMillis);
        dest.writeInt(mMatchMode);
        dest.writeInt(mNumOfMatchesPerFilter);
        dest.writeInt(mLegacy ? 1 : 0);
        dest.writeInt(mPhy);
    }

    public static final Creator<ScanSettings> CREATOR = new Creator<ScanSettings>() {
        @Override public ScanSettings createFromParcel(Parcel in) {
            return new ScanSettings(in.readInt(), in.readInt(), in.readInt(), in.readLong(),
                    in.readInt(), in.readInt(), in.readInt() != 0, in.readInt());
        }

        @Override public ScanSettings[] newArray(int size) {
            return new ScanSettings[size];
        }
    };

    public static final class Builder {
        private int mScanMode = SCAN_MODE_LOW_POWER;
        private int mCallbackType = CALLBACK_TYPE_ALL_MATCHES;
        /** AOSP's SCAN_RESULT_TYPE_FULL; the ABBREVIATED variant is @hide. */
        private int mScanResultType = 0;
        private long mReportDelayMillis = 0;
        private int mMatchMode = MATCH_MODE_AGGRESSIVE;
        private int mNumOfMatchesPerFilter = MATCH_NUM_MAX_ADVERTISEMENT;
        private boolean mLegacy = true;
        private int mPhy = PHY_LE_ALL_SUPPORTED;

        public Builder() {}

        /** AOSP validates the mode rather than storing an unusable one. */
        public Builder setScanMode(int scanMode) {
            if (scanMode < SCAN_MODE_OPPORTUNISTIC || scanMode > SCAN_MODE_LOW_LATENCY) {
                throw new IllegalArgumentException("invalid scan mode " + scanMode);
            }
            mScanMode = scanMode;
            return this;
        }

        public Builder setCallbackType(int callbackType) {
            if (!isValidCallbackType(callbackType)) {
                throw new IllegalArgumentException("invalid callback type - " + callbackType);
            }
            mCallbackType = callbackType;
            return this;
        }

        private static boolean isValidCallbackType(int callbackType) {
            if (callbackType == CALLBACK_TYPE_ALL_MATCHES
                    || callbackType == CALLBACK_TYPE_FIRST_MATCH
                    || callbackType == CALLBACK_TYPE_MATCH_LOST) {
                return true;
            }
            return callbackType == (CALLBACK_TYPE_FIRST_MATCH | CALLBACK_TYPE_MATCH_LOST);
        }

        public Builder setReportDelay(long reportDelayMillis) {
            if (reportDelayMillis < 0) {
                throw new IllegalArgumentException("reportDelay must be > 0");
            }
            mReportDelayMillis = reportDelayMillis;
            return this;
        }

        public Builder setMatchMode(int matchMode) {
            if (matchMode < MATCH_MODE_AGGRESSIVE || matchMode > MATCH_MODE_STICKY) {
                throw new IllegalArgumentException("invalid matchMode " + matchMode);
            }
            mMatchMode = matchMode;
            return this;
        }

        public Builder setNumOfMatches(int numOfMatches) {
            if (numOfMatches < MATCH_NUM_ONE_ADVERTISEMENT
                    || numOfMatches > MATCH_NUM_MAX_ADVERTISEMENT) {
                throw new IllegalArgumentException("invalid numOfMatches " + numOfMatches);
            }
            mNumOfMatchesPerFilter = numOfMatches;
            return this;
        }

        public Builder setLegacy(boolean legacy) {
            mLegacy = legacy;
            return this;
        }

        public Builder setPhy(int phy) {
            mPhy = phy;
            return this;
        }

        public ScanSettings build() {
            return new ScanSettings(mScanMode, mCallbackType, mScanResultType,
                    mReportDelayMillis, mMatchMode, mNumOfMatchesPerFilter, mLegacy, mPhy);
        }
    }
}
