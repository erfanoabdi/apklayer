package apklayer.host;

import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import java.util.List;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowStorageManager;

/** The device's volumes as the storage service lists them: one mounted, visible volume, "Home", at the Linux home (ExternalStorageProvider builds its root from it). */
@Implements(value = StorageManager.class, isInAndroidSdk = false)
public class ShadowHostStorageManager extends ShadowStorageManager {
    private static List<VolumeInfo> volumes;

    /** No volume has a user-given nickname. */
    @Implementation
    protected List<android.os.storage.VolumeRecord> getVolumeRecords() { return List.of(); }

    @Implementation
    protected List<VolumeInfo> getVolumes() {
        synchronized (ShadowHostStorageManager.class) {
            if (volumes == null) {
                int user = android.os.Process.myUserHandle().getIdentifier();
                VolumeInfo volume = new VolumeInfo("stub:home", VolumeInfo.TYPE_STUB, null, null);  // a stub volume keeps its path as it is (an emulated one gets "/<user>" appended)
                volume.fsUuid = "linux-home";  // not "home": DocumentsUI treats that id as the stock Documents folder
                volume.fsLabel = "Home";
                volume.state = VolumeInfo.STATE_MOUNTED;
                volume.mountFlags = VolumeInfo.MOUNT_FLAG_VISIBLE_FOR_READ | VolumeInfo.MOUNT_FLAG_VISIBLE_FOR_WRITE;
                volume.mountUserId = user;
                volume.path = System.getProperty("user.home");  // the Linux user's home: the files the user has, as DocumentsUI browses them
                volume.internalPath = volume.path;
                volumes = List.of(volume);
            }
            return volumes;
        }
    }
}
