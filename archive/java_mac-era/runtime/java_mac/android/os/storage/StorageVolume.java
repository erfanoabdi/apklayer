// android.os.storage.StorageVolume -- one shared/external storage device.
//
// Mac.apk models exactly ONE volume: the primary, emulated, non-removable
// one whose directory is Environment.getExternalStorageDirectory()
// (<android_root>/sdcard, the same place bionic_shim.cpp remaps a native
// /sdcard/... open to). StorageManager holds a single shared instance of it
// (PRIMARY_VOLUME) so getPrimaryStorageVolume() and getStorageVolumes().get(0)
// are the same object, as they are on a device.
//
// AOSP ref: frameworks/base/core/java/android/os/storage/StorageVolume.java
// (android16-release). AOSP marks the class final; so is this one.
package android.os.storage;

public final class StorageVolume implements android.os.Parcelable {
    public StorageVolume() {}

    /** AOSP core/java/android/os/storage/StorageVolume.java:117 -- the extra
     *  createOpenDocumentTreeIntent()/createAccessIntent() attach the volume
     *  under. */
    public static final String EXTRA_STORAGE_VOLUME = "android.os.storage.extra.STORAGE_VOLUME";

    public String getUuid() { return null; }

    public String getState() { return android.os.Environment.MEDIA_MOUNTED; }

    public boolean isPrimary() { return true; }
    public boolean isRemovable() { return false; }
    public boolean isEmulated() { return true; }
    public java.io.File getDirectory() { return android.os.Environment.getExternalStorageDirectory(); }
    public java.util.UUID getStorageUuid() { return null; }
    public String getDescription(android.content.Context context) { return "Internal shared storage"; }
    public android.content.Intent createAccessIntent(String directoryName) { return new android.content.Intent(); }
    public android.content.Intent createOpenDocumentTreeIntent() { return new android.content.Intent(); }

    /** AOSP :312 -- "the user that owns this volume", @NonNull. Mac.apk is
     *  single-user, so the one volume belongs to UserHandle.SYSTEM (user 0),
     *  which is what UserManager.getUserHandle() answers as well. */
    public android.os.UserHandle getOwner() { return android.os.UserHandle.SYSTEM; }

    /** AOSP :347 -- verbatim:
     *      if (isPrimary()) return MediaStore.VOLUME_EXTERNAL_PRIMARY;
     *      else             return getNormalizedUuid();
     *  isPrimary() is true above, so this is the primary branch; the else
     *  branch is unreachable here (a lower-cased fs uuid, and getUuid() is
     *  null on this volume anyway). */
    public String getMediaStoreVolumeName() {
        if (isPrimary()) {
            return android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY;
        }
        String u = getUuid();
        return u != null ? u.toLowerCase(java.util.Locale.US) : null;
    }

    // AOSP :158 flattens 13 fields, of which 8 are @hide and do not exist on
    // this class. Mac.apk models exactly ONE volume (StorageManager's shared
    // PRIMARY_VOLUME) whose every property is a constant, so there is no
    // per-instance state to carry: the parcel is empty and CREATOR rebuilds an
    // equivalent volume. That is only safe because it is true here -- if this
    // class ever gains real per-volume fields, they must be written and read
    // in the same order (debug/parcel_roundtrip.sh gates that).
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) { }

    public static final Creator<StorageVolume> CREATOR = new Creator<StorageVolume>() {
        @Override public StorageVolume createFromParcel(android.os.Parcel in) {
            return new StorageVolume();
        }
        @Override public StorageVolume[] newArray(int size) { return new StorageVolume[size]; }
    };
}



