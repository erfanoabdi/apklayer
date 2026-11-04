package apklayer.host;

import android.app.usage.StorageStats;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.FileUtils;
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

    // shortcut: other packages' stats without the PACKAGE_USAGE_STATS check StorageStatsService makes; enforce it with the hub's app ops
    @Implementation protected StorageStats queryStatsForPackage(UUID uuid, String packageName, UserHandle user) throws PackageManager.NameNotFoundException, IOException {
        volume(uuid);
        return stats(List.of(packages().getApplicationInfo(packageName, 0)));
    }

    @Implementation protected StorageStats queryStatsForUid(UUID uuid, int uid) throws IOException {
        volume(uuid);
        String[] names = packages().getPackagesForUid(uid);
        List<ApplicationInfo> apps = new java.util.ArrayList<>();
        for (String name : names == null ? new String[0] : names) {
            try { apps.add(packages().getApplicationInfo(name, 0)); } catch (PackageManager.NameNotFoundException gone) { /* uninstalled meanwhile */ }
        }
        return stats(apps);
    }

    @Implementation protected StorageStats queryStatsForUser(UUID uuid, UserHandle user) throws IOException {
        volume(uuid);
        return stats(packages().getInstalledApplications(0));
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
