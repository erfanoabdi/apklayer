// android.bluetooth.le.PeriodicAdvertisingParameters -- interval and
// include-TX-power for periodic advertising.
//
// A REAL, complete immutable value type built by its own Builder. See
// BluetoothLeAdvertiser for what is absent (a radio).
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/le/PeriodicAdvertisingParameters.java.
package android.bluetooth.le;

import android.os.Parcel;
import android.os.Parcelable;

public final class PeriodicAdvertisingParameters implements Parcelable {

    /** AOSP's bounds on the periodic advertising interval. */
    private static final int INTERVAL_MIN = 80;
    private static final int INTERVAL_MAX = 65519;

    private final boolean mIncludeTxPower;
    private final int mInterval;

    private PeriodicAdvertisingParameters(boolean includeTxPower, int interval) {
        mIncludeTxPower = includeTxPower;
        mInterval = interval;
    }

    public boolean getIncludeTxPower() { return mIncludeTxPower; }

    public int getInterval() { return mInterval; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mIncludeTxPower ? 1 : 0);
        dest.writeInt(mInterval);
    }

    public static final Creator<PeriodicAdvertisingParameters> CREATOR =
            new Creator<PeriodicAdvertisingParameters>() {
        @Override public PeriodicAdvertisingParameters createFromParcel(Parcel in) {
            return new PeriodicAdvertisingParameters(in.readInt() != 0, in.readInt());
        }

        @Override public PeriodicAdvertisingParameters[] newArray(int size) {
            return new PeriodicAdvertisingParameters[size];
        }
    };

    public static final class Builder {
        private boolean mIncludeTxPower = false;
        private int mInterval = INTERVAL_MAX;

        public Builder() {}

        public Builder setIncludeTxPower(boolean includeTxPower) {
            mIncludeTxPower = includeTxPower;
            return this;
        }

        public Builder setInterval(int interval) {
            if (interval < INTERVAL_MIN || interval > INTERVAL_MAX) {
                throw new IllegalArgumentException("Invalid interval (must be " + INTERVAL_MIN
                        + "-" + INTERVAL_MAX + ")");
            }
            mInterval = interval;
            return this;
        }

        public PeriodicAdvertisingParameters build() {
            return new PeriodicAdvertisingParameters(mIncludeTxPower, mInterval);
        }
    }
}
