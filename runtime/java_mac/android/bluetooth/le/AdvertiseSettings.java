// android.bluetooth.le.AdvertiseSettings -- mode / TX power / connectable /
// timeout for a legacy BLE advertisement.
//
// A REAL, complete immutable value type built by its own Builder, with AOSP's
// own validation. No radio is involved in building one; see
// BluetoothLeAdvertiser for what is absent.
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/le/AdvertiseSettings.java.
package android.bluetooth.le;

import android.os.Parcel;
import android.os.Parcelable;

public final class AdvertiseSettings implements Parcelable {

    public static final int ADVERTISE_MODE_LOW_POWER = 0;
    public static final int ADVERTISE_MODE_BALANCED = 1;
    public static final int ADVERTISE_MODE_LOW_LATENCY = 2;

    public static final int ADVERTISE_TX_POWER_ULTRA_LOW = 0;
    public static final int ADVERTISE_TX_POWER_LOW = 1;
    public static final int ADVERTISE_TX_POWER_MEDIUM = 2;
    public static final int ADVERTISE_TX_POWER_HIGH = 3;

    /** AOSP's cap on the advertising timeout, in milliseconds. */
    private static final int LIMITED_ADVERTISING_MAX_MILLIS = 180 * 1000;

    private final int mAdvertiseMode;
    private final int mAdvertiseTxPowerLevel;
    private final int mAdvertiseTimeoutMillis;
    private final boolean mAdvertiseConnectable;

    private AdvertiseSettings(int advertiseMode, int advertiseTxPowerLevel,
            boolean advertiseConnectable, int advertiseTimeout) {
        mAdvertiseMode = advertiseMode;
        mAdvertiseTxPowerLevel = advertiseTxPowerLevel;
        mAdvertiseConnectable = advertiseConnectable;
        mAdvertiseTimeoutMillis = advertiseTimeout;
    }

    public int getMode() { return mAdvertiseMode; }

    public int getTxPowerLevel() { return mAdvertiseTxPowerLevel; }

    public boolean isConnectable() { return mAdvertiseConnectable; }

    public int getTimeout() { return mAdvertiseTimeoutMillis; }

    @Override public String toString() {
        return "Settings [mAdvertiseMode=" + mAdvertiseMode
                + ", mAdvertiseTxPowerLevel=" + mAdvertiseTxPowerLevel
                + ", mAdvertiseConnectable=" + mAdvertiseConnectable
                + ", mAdvertiseTimeoutMillis=" + mAdvertiseTimeoutMillis + "]";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mAdvertiseMode);
        dest.writeInt(mAdvertiseTxPowerLevel);
        dest.writeInt(mAdvertiseConnectable ? 1 : 0);
        dest.writeInt(mAdvertiseTimeoutMillis);
    }

    public static final Creator<AdvertiseSettings> CREATOR = new Creator<AdvertiseSettings>() {
        @Override public AdvertiseSettings createFromParcel(Parcel in) {
            Builder b = new Builder();
            b.setAdvertiseMode(in.readInt());
            b.setTxPowerLevel(in.readInt());
            b.setConnectable(in.readInt() != 0);
            b.setTimeout(in.readInt());
            return b.build();
        }

        @Override public AdvertiseSettings[] newArray(int size) {
            return new AdvertiseSettings[size];
        }
    };

    public static final class Builder {
        private int mMode = ADVERTISE_MODE_LOW_POWER;
        private int mTxPowerLevel = ADVERTISE_TX_POWER_MEDIUM;
        private int mConnectable = 1;
        private int mTimeoutMillis = 0;

        public Builder() {}

        public Builder setAdvertiseMode(int advertiseMode) {
            if (advertiseMode < ADVERTISE_MODE_LOW_POWER
                    || advertiseMode > ADVERTISE_MODE_LOW_LATENCY) {
                throw new IllegalArgumentException("unknown mode " + advertiseMode);
            }
            mMode = advertiseMode;
            return this;
        }

        public Builder setTxPowerLevel(int txPowerLevel) {
            if (txPowerLevel < ADVERTISE_TX_POWER_ULTRA_LOW
                    || txPowerLevel > ADVERTISE_TX_POWER_HIGH) {
                throw new IllegalArgumentException("unknown tx power level " + txPowerLevel);
            }
            mTxPowerLevel = txPowerLevel;
            return this;
        }

        public Builder setConnectable(boolean connectable) {
            mConnectable = connectable ? 1 : 0;
            return this;
        }

        /** AOSP: 0 means "advertise forever"; otherwise 1..180000 ms. */
        public Builder setTimeout(int timeoutMillis) {
            if (timeoutMillis < 0 || timeoutMillis > LIMITED_ADVERTISING_MAX_MILLIS) {
                throw new IllegalArgumentException("timeoutMillis invalid (must be 0-"
                        + LIMITED_ADVERTISING_MAX_MILLIS + " milliseconds)");
            }
            mTimeoutMillis = timeoutMillis;
            return this;
        }

        public AdvertiseSettings build() {
            return new AdvertiseSettings(mMode, mTxPowerLevel, mConnectable != 0,
                    mTimeoutMillis);
        }
    }
}
