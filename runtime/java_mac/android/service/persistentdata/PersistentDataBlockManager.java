package android.service.persistentdata;

/**
 * API 35 handle to the persistent data block -- the small partition that
 * survives a factory reset and carries Factory Reset Protection (FRP) state.
 *
 * Reached through Context.getSystemService(PersistentDataBlockManager.class).
 * Mac.apk has no such partition and enforces no FRP, so protection is not
 * active. That is a truthful answer, not a bypass: FRP is a lock a DEVICE
 * applies to itself after a reset, and there is nothing here for it to lock.
 *
 * Source: frameworks/base/core/java/android/service/persistentdata/PersistentDataBlockManager.java
 */
public class PersistentDataBlockManager {

    // AOSP's constructor takes the service binder and is @hide; instances come
    // from getSystemService.
    public PersistentDataBlockManager() { }

    /** False: no persistent data block exists to hold an FRP credential. */
    public boolean isFactoryResetProtectionActive() { return false; }
}
