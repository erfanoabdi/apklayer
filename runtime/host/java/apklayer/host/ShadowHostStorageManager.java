package apklayer.host;

import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import java.util.List;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowStorageManager;

/** The device's volumes as the storage service lists them (HostVolumes): ExternalStorageProvider builds its roots from them. */
@Implements(value = StorageManager.class, isInAndroidSdk = false)
public class ShadowHostStorageManager extends ShadowStorageManager {
    private static List<VolumeInfo> volumes;
    @org.robolectric.annotation.RealObject private StorageManager real;
    /** Every StorageManager of the process (a context has its own, with its own listeners), for changed(). */
    private static final java.util.Set<ShadowHostStorageManager> ALL = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    { synchronized (ALL) { ALL.add(this); } }

    /** No volume has a user-given nickname. */
    @Implementation
    protected List<android.os.storage.VolumeRecord> getVolumeRecords() { return List.of(); }

    @Implementation
    protected List<VolumeInfo> getVolumes() {
        synchronized (ShadowHostStorageManager.class) {
            if (volumes == null) volumes = HostVolumes.list();
            return volumes;
        }
    }

    /**
     * A drive came or went (the hub, HubVolumes): the volumes are read again and every listener runs as StorageManagerService's callbacks would,
     * on the listener's executor: StorageEventListener.onVolumeStateChanged and onStorageStateChanged, StorageVolumeCallback.onStateChanged with the
     * volume in its new state (MediaProvider updates its volume cache on it). On the app's main thread.
     */
    static void changed(android.content.Context context, VolumeInfo volume, int oldState) {
        StorageManager manager = context.getSystemService(StorageManager.class);
        if (manager == null) return;
        synchronized (ShadowHostStorageManager.class) { volumes = null; }
        HostStorage.storageVolumes(context, manager);
        int user = android.os.Process.myUserHandle().getIdentifier();
        android.os.storage.StorageVolume userVolume = volume.buildStorageVolume(context, user, false);
        String oldEnv = VolumeInfo.getEnvironmentForState(oldState), newEnv = VolumeInfo.getEnvironmentForState(volume.state);
        java.util.List<Object> delegates = new java.util.ArrayList<>();
        java.util.List<ShadowHostStorageManager> all;
        synchronized (ALL) { all = new java.util.ArrayList<>(ALL); }
        for (ShadowHostStorageManager shadow : all) {
            if (shadow.real == null) continue;
            java.util.List<?> registered = org.robolectric.util.ReflectionHelpers.getField(shadow.real, "mDelegates");
            synchronized (registered) { delegates.addAll(registered); }
        }
        for (Object delegate : delegates) {
            java.util.concurrent.Executor executor = org.robolectric.util.ReflectionHelpers.getField(delegate, "mExecutor");
            android.os.storage.StorageEventListener listener = org.robolectric.util.ReflectionHelpers.getField(delegate, "mListener");
            StorageManager.StorageVolumeCallback callback = org.robolectric.util.ReflectionHelpers.getField(delegate, "mCallback");
            executor.execute(() -> {
                if (listener != null) { listener.onVolumeStateChanged(volume, oldState, volume.state); listener.onStorageStateChanged(userVolume.getPath(), oldEnv, newEnv); }
                if (callback != null) callback.onStateChanged(userVolume);
            });
        }
    }

    /** Every path is on the one default volume; the file systems here do not preallocate through the storage service (DownloadProvider writes the file as it comes). */
    @Implementation
    protected java.util.UUID getUuidForPath(java.io.File path) { return StorageManager.UUID_DEFAULT; }

    @Implementation
    protected boolean isAllocationSupported(java.io.FileDescriptor fd) { return false; }

    /**
     * What StorageManagerService.getAllocatableBytes answers when no app has cache to clear: the usable space of the volume the app's data is on, less
     * the low-storage reserve (getStorageLowBytes). Robolectric's fake service said 0 and Aurora Store refused every download ("Not enough storage space").
     */
    @Implementation
    protected long getAllocatableBytes(java.util.UUID storageUuid, int flags) {
        java.io.File data = org.robolectric.RuntimeEnvironment.getApplication().getDataDir();
        return Math.max(0, data.getUsableSpace() - real.getStorageLowBytes(data));
    }

    /** Nothing to clear: the request succeeds when that much is allocatable, as the service's own check. */
    @Implementation
    protected void allocateBytes(java.util.UUID storageUuid, long bytes, int flags) throws java.io.IOException {
        long allocatable = getAllocatableBytes(storageUuid, flags);
        if (bytes > allocatable) throw new java.io.IOException("Failed to allocate " + bytes + " bytes; only " + allocatable + " bytes available");
    }

    /**
     * What ExtServices' CacheQuotaService gives the app in the foreground when it is the only one with usage (its fair share is the whole
     * reserve): the framework's cache reserve of the volume the app's data is on, from that volume's real size and free space.
     * Limit: there is no usage history to share the reserve among apps, so every app is given the whole of it.
     */
    @Implementation
    protected long getCacheQuotaBytes(java.util.UUID storageUuid) {
        return real.computeStorageCacheBytes(org.robolectric.RuntimeEnvironment.getApplication().getDataDir());
    }
}
