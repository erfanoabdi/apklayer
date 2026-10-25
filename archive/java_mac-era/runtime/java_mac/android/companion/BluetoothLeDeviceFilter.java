// android.companion.BluetoothLeDeviceFilter -- narrows a companion association
// request to BLE scan results matching a name pattern, a ScanFilter, or a raw
// advertising-data mask, optionally renaming the matched device for display.
//
// Same standing as BluetoothDeviceFilter (read that header): a real
// Builder-built value type; nothing scans, because Mac.apk exposes no
// Bluetooth bridge and no companion pairing UI.
//
// Mirrors
// frameworks/base/core/java/android/companion/BluetoothLeDeviceFilter.java;
// public surface confirmed against frameworks/base/core/api/current.txt.
package android.companion;

import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.os.Parcel;

import java.nio.ByteOrder;
import java.util.regex.Pattern;

public final class BluetoothLeDeviceFilter implements DeviceFilter<ScanResult> {

    /** AOSP's cap on how much of the original name a rename may keep. */
    private static final int RENAME_PREFIX_LENGTH_LIMIT = 10;

    private final Pattern mNamePattern;
    private final ScanFilter mScanFilter;
    private final byte[] mRawDataFilter;
    private final byte[] mRawDataFilterMask;
    private final String mRenamePrefix;
    private final String mRenameSuffix;
    private final int mRenameBytesFrom;
    private final int mRenameBytesLength;
    private final int mRenameNameFrom;
    private final int mRenameNameLength;
    private final boolean mRenameBytesReverseOrder;

    private BluetoothLeDeviceFilter(Builder b) {
        mNamePattern = b.mNamePattern;
        mScanFilter = b.mScanFilter;
        mRawDataFilter = b.mRawDataFilter == null ? null : b.mRawDataFilter.clone();
        mRawDataFilterMask = b.mRawDataFilterMask == null ? null : b.mRawDataFilterMask.clone();
        mRenamePrefix = b.mRenamePrefix;
        mRenameSuffix = b.mRenameSuffix;
        mRenameBytesFrom = b.mRenameBytesFrom;
        mRenameBytesLength = b.mRenameBytesLength;
        mRenameNameFrom = b.mRenameNameFrom;
        mRenameNameLength = b.mRenameNameLength;
        mRenameBytesReverseOrder = b.mRenameBytesReverseOrder;
    }

    public static int getRenamePrefixLengthLimit() {
        return RENAME_PREFIX_LENGTH_LIMIT;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mNamePattern == null ? null : mNamePattern.pattern());
        dest.writeString(mRenamePrefix);
        dest.writeString(mRenameSuffix);
    }

    public static final Creator<BluetoothLeDeviceFilter> CREATOR =
            new Creator<BluetoothLeDeviceFilter>() {
        @Override public BluetoothLeDeviceFilter createFromParcel(Parcel source) {
            Builder b = new Builder();
            String pattern = source.readString();
            if (pattern != null) {
                b.setNamePattern(Pattern.compile(pattern));
            }
            String prefix = source.readString();
            String suffix = source.readString();
            if (prefix != null && suffix != null) {
                b.setRenameFromName(prefix, suffix, 0, 0);
            }
            return b.build();
        }

        @Override public BluetoothLeDeviceFilter[] newArray(int size) {
            return new BluetoothLeDeviceFilter[size];
        }
    };

    public static final class Builder {
        private Pattern mNamePattern;
        private ScanFilter mScanFilter;
        private byte[] mRawDataFilter;
        private byte[] mRawDataFilterMask;
        private String mRenamePrefix;
        private String mRenameSuffix;
        private int mRenameBytesFrom = -1;
        private int mRenameBytesLength;
        private int mRenameNameFrom = -1;
        private int mRenameNameLength;
        private boolean mRenameBytesReverseOrder = false;

        public Builder() {}

        public Builder setNamePattern(Pattern re) {
            mNamePattern = re;
            return this;
        }

        public Builder setScanFilter(ScanFilter scanFilter) {
            mScanFilter = scanFilter;
            return this;
        }

        public Builder setRawDataFilter(byte[] rawDataFilter, byte[] rawDataFilterMask) {
            java.util.Objects.requireNonNull(rawDataFilter);
            if (rawDataFilterMask != null
                    && rawDataFilter.length != rawDataFilterMask.length) {
                throw new IllegalArgumentException(
                        "Mask and filter should be the same length");
            }
            mRawDataFilter = rawDataFilter.clone();
            mRawDataFilterMask = rawDataFilterMask == null ? null : rawDataFilterMask.clone();
            return this;
        }

        public Builder setRenameFromBytes(String prefix, String suffix, int bytesFrom,
                int bytesLength, ByteOrder byteOrder) {
            setRename(prefix, suffix);
            mRenameBytesFrom = bytesFrom;
            mRenameBytesLength = bytesLength;
            mRenameBytesReverseOrder = byteOrder == ByteOrder.LITTLE_ENDIAN;
            return this;
        }

        public Builder setRenameFromName(String prefix, String suffix, int nameFrom,
                int nameLength) {
            setRename(prefix, suffix);
            mRenameNameFrom = nameFrom;
            mRenameNameLength = nameLength;
            mRenameBytesReverseOrder = false;
            return this;
        }

        private void setRename(String prefix, String suffix) {
            java.util.Objects.requireNonNull(prefix);
            java.util.Objects.requireNonNull(suffix);
            if (prefix.length() > getRenamePrefixLengthLimit()) {
                throw new IllegalArgumentException("Prefix is too long: " + prefix.length()
                        + " > " + getRenamePrefixLengthLimit());
            }
            mRenamePrefix = prefix;
            mRenameSuffix = suffix;
        }

        public BluetoothLeDeviceFilter build() {
            return new BluetoothLeDeviceFilter(this);
        }
    }
}
