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
}
