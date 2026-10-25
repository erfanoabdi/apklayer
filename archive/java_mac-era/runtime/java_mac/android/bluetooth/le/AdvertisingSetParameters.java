// android.bluetooth.le.AdvertisingSetParameters -- the API-26 advertising-set
// parameters (connectable/scannable/legacy/anonymous, PHYs, interval, TX power).
//
// A REAL, complete immutable value type built by its own Builder, with AOSP's
// range checks. No radio is involved in building one; see
// BluetoothLeAdvertiser for what is absent.
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/le/AdvertisingSetParameters.java.
package android.bluetooth.le;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.Parcelable;

public final class AdvertisingSetParameters implements Parcelable {

    public static final int INTERVAL_HIGH = 1600;
    public static final int INTERVAL_MEDIUM = 400;
    public static final int INTERVAL_LOW = 160;
    public static final int INTERVAL_MIN = 160;
    public static final int INTERVAL_MAX = 16777215;

    public static final int TX_POWER_ULTRA_LOW = -21;
    public static final int TX_POWER_LOW = -15;
    public static final int TX_POWER_MEDIUM = -7;
    public static final int TX_POWER_HIGH = 1;
    public static final int TX_POWER_MIN = -127;
    public static final int TX_POWER_MAX = 1;

    private final boolean mConnectable;
    private final boolean mScannable;
    private final boolean mIsLegacy;
    private final boolean mIsAnonymous;
    private final boolean mIncludeTxPower;
    private final int mPrimaryPhy;
    private final int mSecondaryPhy;
    private final int mInterval;
    private final int mTxPowerLevel;

    private AdvertisingSetParameters(boolean connectable, boolean scannable,
            boolean isLegacy, boolean isAnonymous, boolean includeTxPower,
            int primaryPhy, int secondaryPhy, int interval, int txPowerLevel) {
        mConnectable = connectable;
        mScannable = scannable;
        mIsLegacy = isLegacy;
        mIsAnonymous = isAnonymous;
        mIncludeTxPower = includeTxPower;
        mPrimaryPhy = primaryPhy;
        mSecondaryPhy = secondaryPhy;
        mInterval = interval;
        mTxPowerLevel = txPowerLevel;
    }

    public boolean isConnectable() { return mConnectable; }

    public boolean isScannable() { return mScannable; }

    public boolean isLegacy() { return mIsLegacy; }

    public boolean isAnonymous() { return mIsAnonymous; }

    public boolean includeTxPower() { return mIncludeTxPower; }

    public int getPrimaryPhy() { return mPrimaryPhy; }

    public int getSecondaryPhy() { return mSecondaryPhy; }

    public int getInterval() { return mInterval; }

    public int getTxPowerLevel() { return mTxPowerLevel; }

    @Override public String toString() {
        return "AdvertisingSetParameters [connectable=" + mConnectable
                + ", isLegacy=" + mIsLegacy + ", primaryPhy=" + mPrimaryPhy
                + ", secondaryPhy=" + mSecondaryPhy + ", interval=" + mInterval
                + ", txPowerLevel=" + mTxPowerLevel + ", isAnonymous=" + mIsAnonymous
                + ", includeTxPower=" + mIncludeTxPower + "]";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mConnectable ? 1 : 0);
        dest.writeInt(mScannable ? 1 : 0);
        dest.writeInt(mIsLegacy ? 1 : 0);
        dest.writeInt(mIsAnonymous ? 1 : 0);
        dest.writeInt(mIncludeTxPower ? 1 : 0);
        dest.writeInt(mPrimaryPhy);
        dest.writeInt(mSecondaryPhy);
        dest.writeInt(mInterval);
        dest.writeInt(mTxPowerLevel);
    }

    public static final Creator<AdvertisingSetParameters> CREATOR =
            new Creator<AdvertisingSetParameters>() {
        @Override public AdvertisingSetParameters createFromParcel(Parcel in) {
            return new AdvertisingSetParameters(in.readInt() != 0, in.readInt() != 0,
                    in.readInt() != 0, in.readInt() != 0, in.readInt() != 0,
                    in.readInt(), in.readInt(), in.readInt(), in.readInt());
        }

        @Override public AdvertisingSetParameters[] newArray(int size) {
            return new AdvertisingSetParameters[size];
        }
    };

    public static final class Builder {
        private boolean mConnectable = true;
        private boolean mScannable = true;
        private boolean mIsLegacy = true;
        private boolean mIsAnonymous = false;
        private boolean mIncludeTxPower = false;
        private int mPrimaryPhy = BluetoothDevice.PHY_LE_1M;
        private int mSecondaryPhy = BluetoothDevice.PHY_LE_1M;
        private int mInterval = INTERVAL_LOW;
        private int mTxPowerLevel = TX_POWER_MEDIUM;

        public Builder() {}

        public Builder setConnectable(boolean connectable) {
            mConnectable = connectable;
            return this;
        }

        public Builder setScannable(boolean scannable) {
            mScannable = scannable;
            return this;
        }

        public Builder setLegacyMode(boolean isLegacy) {
            mIsLegacy = isLegacy;
            return this;
        }

        public Builder setAnonymous(boolean isAnonymous) {
            mIsAnonymous = isAnonymous;
            return this;
        }

        public Builder setIncludeTxPower(boolean includeTxPower) {
            mIncludeTxPower = includeTxPower;
            return this;
        }

        /** AOSP: the primary PHY must be 1M or CODED (2M is secondary-only). */
        public Builder setPrimaryPhy(int primaryPhy) {
            if (primaryPhy != BluetoothDevice.PHY_LE_1M
                    && primaryPhy != BluetoothDevice.PHY_LE_CODED) {
                throw new IllegalArgumentException("bad primaryPhy " + primaryPhy);
            }
            mPrimaryPhy = primaryPhy;
            return this;
        }

        public Builder setSecondaryPhy(int secondaryPhy) {
            if (secondaryPhy != BluetoothDevice.PHY_LE_1M
                    && secondaryPhy != BluetoothDevice.PHY_LE_2M
                    && secondaryPhy != BluetoothDevice.PHY_LE_CODED) {
                throw new IllegalArgumentException("bad secondaryPhy " + secondaryPhy);
            }
            mSecondaryPhy = secondaryPhy;
            return this;
        }

        public Builder setInterval(int interval) {
            if (interval < INTERVAL_MIN || interval > INTERVAL_MAX) {
                throw new IllegalArgumentException("unknown interval " + interval);
            }
            mInterval = interval;
            return this;
        }

        public Builder setTxPowerLevel(int txPowerLevel) {
            if (txPowerLevel < TX_POWER_MIN || txPowerLevel > TX_POWER_MAX) {
                throw new IllegalArgumentException("unknown txPowerLevel " + txPowerLevel);
            }
            mTxPowerLevel = txPowerLevel;
            return this;
        }

        /** AOSP rejects the combinations the LE spec forbids. */
        public AdvertisingSetParameters build() {
            if (mIsLegacy) {
                if (mIsAnonymous) {
                    throw new IllegalArgumentException("Legacy advertising can't be anonymous");
                }
                if (mConnectable && !mScannable) {
                    throw new IllegalStateException(
                            "Legacy advertisement can't be connectable and non-scannable");
                }
                if (mIncludeTxPower) {
                    throw new IllegalStateException(
                            "Legacy advertising can't include TX power level in header");
                }
            } else {
                if (mConnectable && mScannable) {
                    throw new IllegalStateException(
                            "Advertising can't be both connectable and scannable");
                }
                if (mIsAnonymous && mConnectable) {
                    throw new IllegalStateException(
                            "Anonymous advertising can't be connectable");
                }
            }
            return new AdvertisingSetParameters(mConnectable, mScannable, mIsLegacy,
                    mIsAnonymous, mIncludeTxPower, mPrimaryPhy, mSecondaryPhy, mInterval,
                    mTxPowerLevel);
        }
    }
}
