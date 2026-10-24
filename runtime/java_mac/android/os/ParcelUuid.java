// android.os.ParcelUuid -- a Parcelable wrapper around java.util.UUID. Used by
// Bluetooth service UUIDs and many SDKs that pass a UUID through a Bundle/Intent.
// Fully functional (real UUID storage, value equality, faithful Parcel I/O).
//
// AOSP ref: frameworks/base/core/java/android/os/ParcelUuid.java
package android.os;

import java.util.UUID;

public final class ParcelUuid implements Parcelable {
    private final UUID mUuid;

    public ParcelUuid(UUID uuid) { mUuid = uuid; }

    public static ParcelUuid fromString(String uuid) {
        return new ParcelUuid(UUID.fromString(uuid));
    }

    public UUID getUuid() { return mUuid; }

    @Override public String toString() { return mUuid.toString(); }

    @Override public int hashCode() { return mUuid.hashCode(); }

    @Override public boolean equals(Object object) {
        if (object == this) return true;
        if (!(object instanceof ParcelUuid)) return false;
        ParcelUuid that = (ParcelUuid) object;
        return mUuid.equals(that.mUuid);
    }

    @Override public int describeContents() { return 0; }

    // AOSP writes the two longs of the UUID.
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mUuid.getMostSignificantBits());
        dest.writeLong(mUuid.getLeastSignificantBits());
    }

    public static final Parcelable.Creator<ParcelUuid> CREATOR =
        new Parcelable.Creator<ParcelUuid>() {
            @Override public ParcelUuid createFromParcel(Parcel source) {
                long mostSig = source.readLong();
                long leastSig = source.readLong();
                return new ParcelUuid(new UUID(mostSig, leastSig));
            }
            @Override public ParcelUuid[] newArray(int size) {
                return new ParcelUuid[size];
            }
        };
}
