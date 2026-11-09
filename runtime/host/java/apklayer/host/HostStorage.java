package apklayer.host;

import android.app.Application;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import java.io.File;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowStorageManager;

/** What every Android device has and the sandbox leaves empty: the primary (emulated, mounted) storage volume, in the instance's storage directory. */
final class HostStorage {
    private HostStorage() {}

    static void install(Application app) {
        java.io.File internal = HostVolumes.internal();  // the instance's own sdcard, not a temp directory
        if (internal != null) {
            internal.mkdirs();
            org.robolectric.shadows.ShadowEnvironment.setExternalStorageDirectory(internal.toPath());  // getExternalFilesDir and the public directories are below it
            org.robolectric.shadows.ShadowEnvironment.setExternalStoragePublicDirectory(internal.toPath());
            org.robolectric.shadows.ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);  // Robolectric says "removed"; MediaProvider waits for "mounted"
            org.robolectric.shadows.ShadowEnvironment.setExternalStorageState(internal, Environment.MEDIA_MOUNTED);
            org.robolectric.shadows.ShadowEnvironment.setExternalStorageEmulated(internal, true);
            org.robolectric.shadows.ShadowEnvironment.setIsExternalStorageEmulated(true);
            moveOldAppDirs(internal, app.getPackageName());
        }
        // Robolectric allows every app op; Android's default for "all files access" defers to the permission (Environment.isExternalStorageManager), which an
        // app holds when it declares it (HostSystemConfig grants app-op permissions at install): an app without it is not a storage manager
        android.app.AppOpsManager ops = app.getSystemService(android.app.AppOpsManager.class);
        if (ops != null) ((org.robolectric.shadows.ShadowAppOpsManager) Shadow.extract(ops)).setMode(android.app.AppOpsManager.OP_MANAGE_EXTERNAL_STORAGE,
                app.getApplicationInfo().uid, app.getPackageName(), android.app.AppOpsManager.MODE_DEFAULT);
        StorageManager manager = app.getSystemService(StorageManager.class);
        if (manager != null && manager.getStorageVolumes().isEmpty()) storageVolumes(app, manager);
    }

    /**
     * StorageManagerService.getVolumeList: the user's emulated volume first, then the visible public ones, each built as the service builds it.
     * Not the stub volumes (Home, Linux root): they are the desktop's folders, DocumentsUI's roots, and reach apps through the picker.
     */
    static void storageVolumes(android.content.Context app, StorageManager manager) {
        ShadowStorageManager shadow = Shadow.extract(manager);
        shadow.resetStorageVolumeList();
        int user = android.os.Process.myUserHandle().getIdentifier();
        for (VolumeInfo volume : manager.getVolumes()) {
            if (volume.getType() != VolumeInfo.TYPE_EMULATED && volume.getType() != VolumeInfo.TYPE_PUBLIC || !volume.isVisibleForUser(user)) continue;
            shadow.addStorageVolume(volume.buildStorageVolume(app, user, false));
        }
    }

    /**
     * The app's external files directory is Android/data/<pkg>/files and its external cache Android/data/<pkg>/cache (ShadowHostContextImpl); until
     * 2026-11-09 they were Android/data/<pkg> itself and <external>/<pkg>-cache (Robolectric's): what an app kept there moves into files/ once
     * (offline maps, downloads), the old cache goes.
     */
    private static void moveOldAppDirs(File external, String pkg) {
        File own = new File(external, "Android/data/" + pkg), files = new File(own, "files");
        String[] old = own.list();
        if (old != null && old.length > 0 && !files.exists() && files.mkdir()) {
            for (String name : old) {
                if (!new File(own, name).renameTo(new File(files, name))) System.out.println("[apklayer] storage: could not move " + own + "/" + name + " into files/");
            }
            System.out.println("[apklayer] storage: moved " + old.length + " entries of " + own + " into files/ (Android's external files directory)");
        }
        File cache = new File(external, pkg + "-cache");
        if (cache.isDirectory()) deleteTree(cache);
    }

    private static void deleteTree(File dir) {
        File[] children = dir.listFiles();
        if (children != null) for (File child : children) { if (child.isDirectory() && !java.nio.file.Files.isSymbolicLink(child.toPath())) deleteTree(child); else child.delete(); }
        dir.delete();
    }
}
