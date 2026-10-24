// android.os.storage.OnObbStateChangeListener -- callback for OBB (Opaque
// Binary Blob) mount/unmount results. Referenced by openttd, which ships
// its game data as an OBB expansion file.
//
// Shape verified against AOSP (fetched 2026-08-12, android13-release):
// frameworks/base/core/java/android/os/storage/OnObbStateChangeListener.java
//
// See StorageManager.java in this package for why Mac.apk never actually
// mounts an OBB (no loop-mount / OBB filesystem backend on macOS) and for
// which of these status codes mountObb() hands back.
package android.os.storage;

public abstract class OnObbStateChangeListener {
    /** The OBB container is now mounted and ready for use. */
    public static final int MOUNTED = 1;
    /** The OBB container is now unmounted and not usable. */
    public static final int UNMOUNTED = 2;
    /** Internal system error while trying to mount the OBB. */
    public static final int ERROR_INTERNAL = 20;
    /** The OBB could not be mounted by the system. */
    public static final int ERROR_COULD_NOT_MOUNT = 21;
    /** The OBB could not be unmounted (most likely a file is in use). */
    public static final int ERROR_COULD_NOT_UNMOUNT = 22;
    /** unmountObb() was called on an OBB that was not mounted. */
    public static final int ERROR_NOT_MOUNTED = 23;
    /** mountObb() was called on an OBB that was already mounted. */
    public static final int ERROR_ALREADY_MOUNTED = 24;
    /** The current application does not own this OBB. */
    public static final int ERROR_PERMISSION_DENIED = 25;

    /** Called when an OBB has changed states. AOSP's default body is empty;
     *  subclasses override to react. */
    public void onObbStateChange(String path, int state) {}
}
