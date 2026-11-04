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

    /** Every path is on the one default volume; the file systems here do not preallocate through the storage service (DownloadProvider writes the file as it comes). */
    @Implementation
    protected java.util.UUID getUuidForPath(java.io.File path) { return StorageManager.UUID_DEFAULT; }

    @Implementation
    protected boolean isAllocationSupported(java.io.FileDescriptor fd) { return false; }

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
