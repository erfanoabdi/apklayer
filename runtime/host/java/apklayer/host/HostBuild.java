package apklayer.host;

import android.os.Build;
import org.robolectric.util.ReflectionHelpers;

/** The device ABIs of this route: Android's libraries are arm64, so apps are told arm64-v8a first (SoLoader, ReLinker and abi splits read them). */
final class HostBuild {
    private HostBuild() {}

    static void install() {
        set("SUPPORTED_ABIS", new String[] {"arm64-v8a", "armeabi-v7a", "armeabi"});
        set("SUPPORTED_64_BIT_ABIS", new String[] {"arm64-v8a"});
        set("SUPPORTED_32_BIT_ABIS", new String[] {"armeabi-v7a", "armeabi"});
        set("CPU_ABI", "arm64-v8a");
        set("CPU_ABI2", "armeabi-v7a");
    }

    private static void set(String field, Object value) {
        try { ReflectionHelpers.setStaticField(Build.class, field, value); }
        catch (RuntimeException unavailable) { System.out.println("[apklayer] Build." + field + ": " + unavailable.getMessage()); }
    }
}
