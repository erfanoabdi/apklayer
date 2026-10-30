package apklayer.host;

import android.os.Build;
import org.robolectric.util.ReflectionHelpers;

/** The device ABIs of this route: Android's libraries are those of this machine's CPU, so apps are told its ABI first (SoLoader, ReLinker and abi splits read them). */
final class HostBuild {
    private HostBuild() {}

    /** arm64-v8a on an aarch64 machine, x86_64 on a PC (the libraries apps run are the ones of that ABI in their APK). */
    static final String ABI = "aarch64".equals(System.getProperty("os.arch")) ? "arm64-v8a" : "x86_64";
    private static final boolean ARM64 = ABI.equals("arm64-v8a");

    static void install() {
        set("SUPPORTED_ABIS", ARM64 ? new String[] {"arm64-v8a", "armeabi-v7a", "armeabi"} : new String[] {"x86_64"});
        set("SUPPORTED_64_BIT_ABIS", new String[] {ABI});
        set("SUPPORTED_32_BIT_ABIS", ARM64 ? new String[] {"armeabi-v7a", "armeabi"} : new String[0]);
        set("CPU_ABI", ABI);
        set("CPU_ABI2", ARM64 ? "armeabi-v7a" : "");
        // not Robolectric's "robolectric": apps detect a test environment by it and skip their native code (AnkiDroid loads its Rust backend only when FINGERPRINT differs)
        set("MANUFACTURER", "LineageOS");
        set("BRAND", "lineage");
        set("MODEL", "Linux desktop");
        set("DEVICE", "gsi_arm64");
        set("PRODUCT", "gsi_arm64");
        set("HARDWARE", "apklayer");
        set("BOARD", "apklayer");
        set("FINGERPRINT", "lineage/gsi_arm64/gsi_arm64:16/BP2A.250805.005/apklayer:userdebug/test-keys");
        set("DISPLAY", "gsi_arm64-userdebug");
    }

    private static void set(String field, Object value) {
        try { ReflectionHelpers.setStaticField(Build.class, field, value); }
        catch (RuntimeException unavailable) { System.out.println("[apklayer] Build." + field + ": " + unavailable.getMessage()); }
    }
}
