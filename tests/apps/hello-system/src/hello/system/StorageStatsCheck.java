package hello.system;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.os.Process;
import android.os.storage.StorageManager;
import java.io.File;
import java.nio.file.Files;

/** A file manager's storage page: the volume's size and free space, and this app's own code, data and cache sizes; another app's are refused without PACKAGE_USAGE_STATS. */
final class StorageStatsCheck implements Check {
    @Override public String run(Context context) throws Exception {
        StorageStatsManager stats = context.getSystemService(StorageStatsManager.class);
        long total = stats.getTotalBytes(StorageManager.UUID_DEFAULT), free = stats.getFreeBytes(StorageManager.UUID_DEFAULT);
        Check.require(total > 0 && free > 0 && free <= context.getDataDir().getTotalSpace(), "volume " + free + " free of " + total + " (file system " + context.getDataDir().getTotalSpace() + ")");
        File cached = new File(context.getCacheDir(), "storagestats.bin");
        Files.write(cached.toPath(), new byte[200_000]);
        try {
            StorageStats own = stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, context.getPackageName(), Process.myUserHandle());
            Check.require(own.getAppBytes() > 0 && own.getCacheBytes() >= 200_000 && own.getDataBytes() >= own.getCacheBytes(),
                    "package " + own.getAppBytes() + "/" + own.getDataBytes() + "/" + own.getCacheBytes());
            try { stats.getTotalBytes(java.util.UUID.randomUUID()); throw new AssertionError("an unknown volume has a size"); }
            catch (java.io.IOException expected) { /* as on Android */ }
            // another app's sizes and the user's need PACKAGE_USAGE_STATS (StorageStatsService.enforceStatsPermission)
            try { stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, "hello.accounts", Process.myUserHandle()); throw new AssertionError("another app's stats without PACKAGE_USAGE_STATS (its uid " + context.getPackageManager().getApplicationInfo("hello.accounts", 0).uid + ", own " + Process.myUid() + ", permission " + context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) + ")"); }
            catch (SecurityException expected) { /* as on Android */ }
            try { stats.queryStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle()); throw new AssertionError("the user's stats without PACKAGE_USAGE_STATS"); }
            catch (SecurityException expected) { /* as on Android */ }
            return "volume " + total / (1 << 30) + " GB, " + free / (1 << 20) + " MiB free; app " + own.getAppBytes() / 1024 + " KiB code, "
                    + own.getDataBytes() / 1024 + " KiB data, " + own.getCacheBytes() / 1024 + " KiB cache";
        } finally { cached.delete(); }
    }
}
