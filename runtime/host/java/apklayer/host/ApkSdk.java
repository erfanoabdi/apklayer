package apklayer.host;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The target SDK an APK declares (<uses-sdk android:targetSdkVersion>), read from its binary manifest by Android's own parser. Robolectric's
 * AndroidManifest has no manifest file here (the APK is parsed instead) and answers its default, 23, for every app: ApplicationInfo and the
 * per-SDK compatibility switches (HostCompat) then treated every app as targeting Android 6.
 */
final class ApkSdk {
    private ApkSdk() {}

    private static final ConcurrentHashMap<String, Integer> targets = new ConcurrentHashMap<>();

    /** The declared target SDK, or -1 when the APK cannot be read or declares none. */
    static int target(File apk) {
        return targets.computeIfAbsent(apk.getAbsolutePath(), path -> {
            try {
                @SuppressWarnings("deprecation") android.content.pm.PackageParser.ApkLite lite = android.content.pm.PackageParser.parseApkLite(apk, 0);
                return lite.targetSdkVersion > 0 ? lite.targetSdkVersion : -1;
            } catch (Exception | LinkageError unreadable) {
                System.out.println("[apklayer] target SDK of " + apk + ": " + unreadable);
                return -1;
            }
        });
    }
}
