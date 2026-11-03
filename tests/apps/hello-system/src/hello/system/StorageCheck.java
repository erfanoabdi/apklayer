package hello.system;

import android.content.Context;
import android.os.storage.StorageManager;

/** The cache quota (Chromium's disk cache sizes itself by it): more than nothing, at most the volume the app's data is on. */
final class StorageCheck implements Check {
    @Override public String run(Context context) throws Exception {
        StorageManager storage = context.getSystemService(StorageManager.class);
        long quota = storage.getCacheQuotaBytes(storage.getUuidForPath(context.getCacheDir())), total = context.getDataDir().getTotalSpace();
        String seen = "quota " + quota / (1 << 20) + " MiB of " + total / (1 << 20) + " MiB";
        Check.require(quota > 0 && quota <= total, seen);
        return seen;
    }
}
