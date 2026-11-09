package apklayer.host;

import android.app.usage.StorageStats;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.FileUtils;
import android.os.Process;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Volume and app sizes as StorageStatsService works them out (the volume's file system, the apps' code and data directories): Robolectric's
 * threw IOException for every volume and NameNotFoundException for every package (a file manager's storage page). One volume, as
 * ShadowHostStorageManager.getUuidForPath reports.
 */
@Implements(className = "android.app.usage.StorageStatsManager", isInAndroidSdk = false)
public class ShadowHostStorageStatsManager {
    private static File volume(UUID uuid) throws IOException {
        if (!StorageManager.UUID_DEFAULT.equals(uuid)) throw new IOException("Failed to find storage device for UUID " + uuid);  // StorageStatsService's answer
        return RuntimeEnvironment.getApplication().getDataDir();
    }

    @Implementation protected long getTotalBytes(UUID uuid) throws IOException { return FileUtils.roundStorageSize(volume(uuid).getTotalSpace()); }

    // shortcut: usable bytes without the clearable cache StorageStatsService adds (no cache quotas here)
    @Implementation protected long getFreeBytes(UUID uuid) throws IOException { return volume(uuid).getUsableSpace(); }

    @Implementation protected StorageStats queryStatsForPackage(UUID uuid, String packageName, UserHandle user) throws PackageManager.NameNotFoundException, IOException {
        volume(uuid);
        ApplicationInfo app = packages().getApplicationInfo(packageName, 0);
        if (app.uid != Process.myUid()) enforceStatsPermission();  // no permission needed about itself
        return stats(List.of(app));
    }

    @Implementation protected StorageStats queryStatsForUid(UUID uuid, int uid) throws IOException {
        volume(uuid);
        if (uid != Process.myUid()) enforceStatsPermission();
        String[] names = packages().getPackagesForUid(uid);
        List<ApplicationInfo> apps = new java.util.ArrayList<>();
        for (String name : names == null ? new String[0] : names) {
            try { apps.add(packages().getApplicationInfo(name, 0)); } catch (PackageManager.NameNotFoundException gone) { /* uninstalled meanwhile */ }
        }
        return stats(apps);
    }

    @Implementation protected StorageStats queryStatsForUser(UUID uuid, UserHandle user) throws IOException {
        volume(uuid);
        enforceStatsPermission();
        return stats(packages().getInstalledApplications(0));
    }

    /**
     * The user's shared storage in use (Settings' storage page; the fake service said null). shortcut: the total only; StorageStatsService splits
     * audio, video, images and apps by quota accounting that does not exist here (walk by MediaStore type if a page needs the split).
     */
    @Implementation protected android.app.usage.ExternalStorageStats queryExternalStatsForUser(UUID uuid, UserHandle user) throws IOException {
        volume(uuid);
        enforceStatsPermission();
        android.app.usage.ExternalStorageStats stats = new android.app.usage.ExternalStorageStats();
        stats.totalBytes = size(android.os.Environment.getExternalStorageDirectory().getPath());
        return stats;
    }

    /**
     * StorageStatsService.checkStatsPermission with the GET_USAGE_STATS app op at its default (MODE_DEFAULT): the PACKAGE_USAGE_STATS permission decides.
     * shortcut: no app op store here (Robolectric's AppOpsManager answers MODE_ALLOWED for every op), so a user cannot grant usage access; a hub app op store would.
     */
    private static void enforceStatsPermission() {
        Context context = RuntimeEnvironment.getApplication();
        if (context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED) return;
        throw new SecurityException("Caller does not have " + android.Manifest.permission.PACKAGE_USAGE_STATS + "; callingPackage=" + context.getOpPackageName() + ", callingUid=" + Process.myUid());
    }

    private static PackageManager packages() { return RuntimeEnvironment.getApplication().getPackageManager(); }

    /** installd's split: code = the APKs, data = the data directory (cache included), cache = its cache and code_cache. */
    private static StorageStats stats(List<ApplicationInfo> apps) {
        StorageStats stats = new StorageStats();
        for (ApplicationInfo app : apps) {
            stats.codeBytes += size(app.sourceDir);
            if (app.splitSourceDirs != null) for (String split : app.splitSourceDirs) stats.codeBytes += size(split);
            if (app.dataDir == null) continue;
            stats.dataBytes += size(app.dataDir);
            stats.cacheBytes += size(app.dataDir + "/cache") + size(app.dataDir + "/code_cache");
        }
        return stats;
    }

    private static long size(String path) {
        if (path == null) return 0;
        try (Stream<java.nio.file.Path> files = Files.walk(new File(path).toPath().toRealPath())) {  // a data dir may be a link to the instance's
            return files.mapToLong(f -> f.toFile().isFile() ? f.toFile().length() : 0).sum();
        } catch (IOException | java.io.UncheckedIOException unreadable) { return 0; }
    }
}
