package apklayer.host;

import android.os.storage.VolumeInfo;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The storage volumes of the instance, as the storage service lists them: Internal storage (the emulated primary volume, {@code <storage>/emulated/0}, what
 * Environment.getExternalStorageDirectory() returns), Home (the Linux user's home) and Linux root ({@code /}, which apklayer can mostly only read).
 * {@code -Dapklayer.storage} is the instance's storage directory (cli/layout).
 */
final class HostVolumes {
    private HostVolumes() {}

    static File internal() {
        String storage = System.getProperty("apklayer.storage");
        return storage == null ? null : new File(storage, "emulated/" + android.os.Process.myUserHandle().getIdentifier());
    }

    static List<VolumeInfo> list() {
        int user = android.os.Process.myUserHandle().getIdentifier();
        List<VolumeInfo> volumes = new ArrayList<>();
        File internal = internal();
        if (internal != null) {
            VolumeInfo emulated = new VolumeInfo("emulated;" + user, VolumeInfo.TYPE_EMULATED, null, null);
            emulated.path = internal.getParentFile().getPath();  // VolumeInfo appends the user id
            mounted(emulated, user, true);
            volumes.add(emulated);
        }
        volumes.add(stub("linux-home", "Home", System.getProperty("user.home"), user));  // not "home": DocumentsUI treats that id as the stock Documents folder
        volumes.add(stub("linux-root", "Linux root", "/", user));
        return volumes;
    }

    /** A stub volume keeps its path as it is (an emulated one gets "/<user>" appended). */
    private static VolumeInfo stub(String uuid, String label, String path, int user) {
        VolumeInfo volume = new VolumeInfo("stub:" + uuid, VolumeInfo.TYPE_STUB, null, null);
        volume.fsUuid = uuid;
        volume.fsLabel = label;
        volume.path = path;
        mounted(volume, user, false);
        return volume;
    }

    private static void mounted(VolumeInfo volume, int user, boolean primary) {
        volume.state = VolumeInfo.STATE_MOUNTED;
        volume.mountFlags = (primary ? VolumeInfo.MOUNT_FLAG_PRIMARY : 0) | VolumeInfo.MOUNT_FLAG_VISIBLE_FOR_READ | VolumeInfo.MOUNT_FLAG_VISIBLE_FOR_WRITE;
        volume.mountUserId = user;
        volume.internalPath = volume.path;
    }
}
