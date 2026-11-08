package hello.files;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import java.io.File;

/** What an ordinary app sees of storage through the file APIs (hello.volumes): the external directories, the storage volumes, the all-files state, one file's SELinux label. */
final class Volumes {
    private Volumes() {}

    static void print(Context context) throws Exception {
        say("external " + Environment.getExternalStorageDirectory() + " state=" + Environment.getExternalStorageState() + " manager=" + Environment.isExternalStorageManager()
                + " legacy=" + Environment.isExternalStorageLegacy() + " emulated=" + Environment.isExternalStorageEmulated() + " removable=" + Environment.isExternalStorageRemovable());
        File files = context.getExternalFilesDir(null), cache = context.getExternalCacheDir(), obb = context.getObbDir();
        say("files " + files + " exists=" + (files != null && files.isDirectory()) + " cache=" + cache + " obb=" + obb + " obbExists=" + obb.isDirectory());
        say("filesDirs " + java.util.Arrays.toString(context.getExternalFilesDirs(null)) + " media=" + java.util.Arrays.toString(context.getExternalMediaDirs()));
        StorageManager storage = context.getSystemService(StorageManager.class);
        for (StorageVolume v : storage.getStorageVolumes()) {
            say("volume " + v.getDirectory() + " desc=" + v.getDescription(context) + " primary=" + v.isPrimary() + " removable=" + v.isRemovable() + " emulated=" + v.isEmulated()
                    + " state=" + v.getState() + " uuid=" + v.getUuid() + " media=" + v.getMediaStoreVolumeName());
        }
        say("primary " + storage.getPrimaryStorageVolume().getDirectory() + " ofDownload=" + describe(storage.getStorageVolume(new File(Environment.getExternalStorageDirectory(), "Download"))));
        say("mediastore " + MediaStore.getExternalVolumeNames(context));
        File root = Environment.getExternalStorageDirectory();
        String[] top = root.list();
        say("list " + root + " " + (top == null ? "null" : String.join(",", new java.util.TreeSet<>(java.util.Arrays.asList(top)))));
        say("list /storage " + String.join(",", listed(new File("/storage"))) + " android/data=" + String.join(",", listed(new File(root, "Android/data"))));
        try { say("label " + root + " " + new String(android.system.Os.getxattr(root.getPath(), "security.selinux")).trim()); }
        catch (android.system.ErrnoException failure) { say("label " + root + " " + failure.getMessage()); }
        say("DONE");
    }

    private static String describe(StorageVolume v) { return v == null ? "null" : v.getDirectory() + "/" + v.isPrimary(); }

    private static java.util.Set<String> listed(File dir) {
        String[] names = dir.list();
        return names == null ? java.util.Set.of("null") : new java.util.TreeSet<>(java.util.Arrays.asList(names));
    }

    private static void say(String line) { System.out.println("[volumes] " + line); }
}
