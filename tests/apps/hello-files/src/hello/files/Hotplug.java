package hello.files;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A drive plugged in while the app runs (hello.hotplug; tests/vm/volumes.py mounts and removes it): the volume broadcasts and callbacks, the volume in
 * getStorageVolumes and MediaStore, the drive's files indexed at mount (DCIM/pre.png, put there before), files written afterwards on the drive and on
 * internal storage indexed (and a deleted one dropped), then after the removal the eject, unmounted and removed broadcasts and the volume gone.
 * Prints `[hotplug] PASS|FAIL <step>`, `[hotplug] ready` (mount the drive now), `[hotplug] unmount` (remove it now), `[hotplug] DONE`.
 */
final class Hotplug {
    private Hotplug() {}
    private static final LinkedBlockingQueue<String> EVENTS = new LinkedBlockingQueue<>(), CALLBACKS = new LinkedBlockingQueue<>();

    private static final java.util.Set<String> SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void start(Activity activity) {
        IntentFilter filter = new IntentFilter();
        for (String action : new String[] {Intent.ACTION_MEDIA_MOUNTED, Intent.ACTION_MEDIA_EJECT, Intent.ACTION_MEDIA_UNMOUNTED, Intent.ACTION_MEDIA_REMOVED}) filter.addAction(action);
        filter.addDataScheme("file");
        activity.getApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                StorageVolume v = i.getParcelableExtra(StorageVolume.EXTRA_STORAGE_VOLUME, StorageVolume.class);
                say("event " + i.getAction() + " " + i.getData() + " volume=" + (v == null ? null : v.getUuid() + "/" + v.getState() + "/removable=" + v.isRemovable()));
                EVENTS.add(i.getAction() + " " + i.getData().getPath() + " " + (v == null ? "" : v.getState()));
            }
        }, filter, Context.RECEIVER_EXPORTED);
        IntentFilter extra = new IntentFilter(Intent.ACTION_MEDIA_CHECKING);  // apart: the strict order of EVENTS stays
        extra.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        extra.addDataScheme("file");
        activity.getApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { say("event " + i.getAction() + " " + i.getData()); SEEN.add(i.getAction() + " " + i.getData().getPath()); }
        }, extra, Context.RECEIVER_EXPORTED);
        StorageManager storage = activity.getSystemService(StorageManager.class);
        storage.registerStorageVolumeCallback(activity.getMainExecutor(), new StorageManager.StorageVolumeCallback() {
            @Override public void onStateChanged(StorageVolume v) { say("callback " + v.getUuid() + " " + v.getState()); CALLBACKS.add(v.getUuid() + " " + v.getState()); }
        });
        new Thread(() -> {
            try { if ("catchup".equals(System.getProperty("hello.hotplug"))) catchUp(activity, storage); else run(activity, storage); } catch (Throwable failure) { say("FAIL exception " + failure); failure.printStackTrace(System.out); }
            say("DONE");
            activity.runOnUiThread(activity::finish);
        }, "hotplug").start();
    }

    private static void run(Context context, StorageManager storage) throws Exception {
        say("ready");
        // past what the hub's start announced (a drive recorded by an earlier run that is gone now: its eject and removal)
        String mounted;
        do mounted = EVENTS.poll(60, TimeUnit.SECONDS); while (mounted != null && !mounted.startsWith(Intent.ACTION_MEDIA_MOUNTED + " "));
        // under the instance's /storage (the sandbox shows it at its host path: no path layer there)
        String storageRoot = android.os.Environment.getExternalStorageDirectory().getParentFile().getParent();
        check("mounted broadcast", mounted != null && mounted.startsWith(Intent.ACTION_MEDIA_MOUNTED + " " + storageRoot + "/") && mounted.endsWith(" mounted"));
        if (mounted == null) return;
        String path = mounted.split(" ")[1], uuid = new File(path).getName();
        String callback;
        do callback = CALLBACKS.poll(10, TimeUnit.SECONDS); while (callback != null && !callback.equals(uuid + " mounted"));  // past its "checking"
        check("volume callback", (uuid + " mounted").equals(callback));
        StorageVolume volume = storage.getStorageVolumes().stream().filter(v -> v != null && uuid.equals(v.getUuid())).findFirst().orElse(null);
        check("volume listed", volume != null && volume.isRemovable() && "mounted".equals(volume.getState()) && path.equals(volume.getDirectory().getPath()));
        for (int i = 0; i < 30 && !SEEN.contains(Intent.ACTION_MEDIA_CHECKING + " " + path); i++) Thread.sleep(100);
        check("MEDIA_CHECKING broadcast", SEEN.contains(Intent.ACTION_MEDIA_CHECKING + " " + path));
        File[] dirs = context.getExternalFilesDirs(null);  // the primary's, then the drive's
        check("secondary external files dir", dirs.length == 2 && dirs[1] != null && dirs[1].getPath().equals(path + "/Android/data/" + context.getPackageName() + "/files") && dirs[1].isDirectory());
        String media = uuid.toLowerCase();
        check("mediastore volume", MediaStore.getExternalVolumeNames(context).contains(media));
        boolean scanned = appears(context, media, "pre.png", true);
        check("mount scan", scanned);
        if (!scanned) try (Cursor c = context.getContentResolver().query(MediaStore.Files.getContentUri(media), new String[] {MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.MIME_TYPE}, null, null, null)) {
            while (c != null && c.moveToNext()) say("row " + c.getString(0) + " " + c.getString(1));
        }
        File dcim = new File(path, "DCIM");
        png(new File(dcim, "new.png"));
        check("drive file indexed", appears(context, media, "new.png", true));
        File pictures = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "hotplug-" + android.os.Process.myPid() + ".png");
        png(pictures);
        check("internal file indexed", appears(context, MediaStore.VOLUME_EXTERNAL_PRIMARY, pictures.getName(), true));
        pictures.delete();
        check("internal file dropped", appears(context, MediaStore.VOLUME_EXTERNAL_PRIMARY, pictures.getName(), false));
        CALLBACKS.clear();
        say("unmount");
        for (String state : new String[] {Intent.ACTION_MEDIA_EJECT + " " + path + " ejecting", Intent.ACTION_MEDIA_UNMOUNTED + " " + path + " unmounted", Intent.ACTION_MEDIA_REMOVED + " " + path + " removed"})
            check(state.split(" ")[0].replace("android.intent.action.", "") + " broadcast", state.equals(EVENTS.poll(60, TimeUnit.SECONDS)));
        check("volume gone", storage.getStorageVolumes().stream().noneMatch(v -> uuid.equals(v.getUuid())) && !MediaStore.getExternalVolumeNames(context).contains(media));
        check("callback on removal", CALLBACKS.contains(uuid + " removed"));
        check("link removed", !new File(path).exists());
    }

    /**
     * What changed while the instance was stopped (hello.hotplug=catchup; HubVolumes and HubMediaWatch at hub start): with hello.catchup.drive=1 a drive
     * mounted meanwhile is listed and its DCIM/pre.png indexed, with 0 one removed meanwhile is gone; Pictures/<hello.catchup.file> on internal storage
     * is indexed (written meanwhile) or, with hello.catchup.drive=0, no longer (deleted meanwhile).
     */
    private static void catchUp(Context context, StorageManager storage) throws Exception {
        boolean present = "1".equals(System.getProperty("hello.catchup.drive"));
        String tag = present ? "catchup present " : "catchup gone ";
        StorageVolume drive = null;
        for (long end = System.currentTimeMillis() + 30_000; System.currentTimeMillis() < end && (drive != null) != present; Thread.sleep(300))
            drive = storage.getStorageVolumes().stream().filter(StorageVolume::isRemovable).findFirst().orElse(null);
        check(tag + "volume", (drive != null) == present);
        say("status");  // the runner reads `apklayer status` now
        // MediaStore.Images.Media is the first MediaStore use here (AppEnvironment preloads MediaStore; the nested class first crashed the JVM)
        if (present) check(tag + "drive scanned", appears(context, drive.getUuid().toLowerCase(), "pre.png", true));
        else check(tag + "mediastore volume", MediaStore.getExternalVolumeNames(context).stream().noneMatch(v -> !v.equals(MediaStore.VOLUME_EXTERNAL_PRIMARY)));
        check(tag + "internal file", appears(context, MediaStore.VOLUME_EXTERNAL_PRIMARY, System.getProperty("hello.catchup.file"), present));
    }

    /** Whether MediaStore.Images on that volume lists (or no longer lists) the file within 30 s. */
    private static boolean appears(Context context, String volume, String name, boolean present) throws InterruptedException {
        for (long end = System.currentTimeMillis() + 30_000; System.currentTimeMillis() < end; Thread.sleep(300)) {
            try (Cursor c = context.getContentResolver().query(MediaStore.Images.Media.getContentUri(volume), new String[] {MediaStore.MediaColumns._ID},
                    MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[] {name}, null)) {
                if (c != null && (c.getCount() > 0) == present) return true;
            } catch (RuntimeException notYet) { say("query " + volume + ": " + notYet); }
        }
        return false;
    }

    private static void png(File file) throws Exception {
        file.getParentFile().mkdirs();
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xff336699);
        try (FileOutputStream out = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
    }

    private static void check(String step, boolean ok) { say((ok ? "PASS " : "FAIL ") + step); }

    private static void say(String line) { System.out.println("[hotplug] " + line); }
}
