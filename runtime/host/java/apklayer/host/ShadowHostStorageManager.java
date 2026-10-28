package apklayer.host;

import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import java.util.List;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowStorageManager;

/** The device's volumes as the storage service lists them: the one emulated, mounted, visible primary volume (ExternalStorageProvider builds its root from it). */
@Implements(value = StorageManager.class, isInAndroidSdk = false)
public class ShadowHostStorageManager extends ShadowStorageManager {
    private static List<VolumeInfo> volumes;

    @Implementation
    protected List<VolumeInfo> getVolumes() {
        synchronized (ShadowHostStorageManager.class) {
            if (volumes == null) {
                int user = android.os.Process.myUserHandle().getIdentifier();
                VolumeInfo volume = new VolumeInfo("emulated;" + user, VolumeInfo.TYPE_EMULATED, null, null);
                volume.state = VolumeInfo.STATE_MOUNTED;
                volume.mountFlags = VolumeInfo.MOUNT_FLAG_PRIMARY | VolumeInfo.MOUNT_FLAG_VISIBLE_FOR_READ | VolumeInfo.MOUNT_FLAG_VISIBLE_FOR_WRITE;
                volume.mountUserId = user;
                volume.path = Environment.getExternalStorageDirectory().getPath();
                volume.internalPath = volume.path;
                volumes = List.of(volume);
            }
            return volumes;
        }
    }
}
