package apklayer.host;

import android.os.Build;
import java.util.Map;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * The device identity apps see: Build.* and android.os.SystemProperties come from the instance's property store, which the natives read too (the
 * tree's build.prop, bridge/native/apklayer.prop, and what cli/stack.cpp derives: fingerprint, CPU ABI lists). Robolectric answers SystemProperties from
 * its own build.prop ("robolectric" everywhere): apps detect a test environment by it and skip their native code (AnkiDroid's Rust backend).
 */
final class HostBuild {
    private HostBuild() {}

    /** arm64-v8a on an aarch64 machine, x86_64 on a PC, armeabi-v7a in the 32-bit app process (the libraries apps run are the ones of that ABI in their APK). */
    static final String ABI = switch (System.getProperty("os.arch")) {
        case "aarch64" -> "arm64-v8a";
        case "arm" -> "armeabi-v7a";
        default -> "x86_64";
    };

    /** The ABI of the app's own libraries: arm64-v8a for an app that runs through the native bridge (as ART reports the app's ISA on a device with one), else ABI. */
    static String appAbi() { return "arm64".equals(System.getProperty("apklayer.nativebridge")) ? "arm64-v8a" : ABI; }  // a spare-born process gets the property with its launch

    /** Build field -> its property, as android.os.Build reads them. */
    private static final Map<String, String> BUILD = Map.ofEntries(Map.entry("ID", "ro.build.id"), Map.entry("DISPLAY", "ro.build.display.id"),
            Map.entry("PRODUCT", "ro.product.name"), Map.entry("DEVICE", "ro.product.device"), Map.entry("BOARD", "ro.product.board"),
            Map.entry("MANUFACTURER", "ro.product.manufacturer"), Map.entry("BRAND", "ro.product.brand"), Map.entry("MODEL", "ro.product.model"),
            Map.entry("BOOTLOADER", "ro.bootloader"), Map.entry("HARDWARE", "ro.hardware"), Map.entry("TYPE", "ro.build.type"), Map.entry("TAGS", "ro.build.tags"),
            Map.entry("FINGERPRINT", "ro.build.fingerprint"), Map.entry("HOST", "ro.build.host"), Map.entry("USER", "ro.build.user"));
    private static final Map<String, String> VERSION = Map.of("INCREMENTAL", "ro.build.version.incremental", "RELEASE", "ro.build.version.release",
            "RELEASE_OR_CODENAME", "ro.build.version.release_or_codename", "RELEASE_OR_PREVIEW_DISPLAY", "ro.build.version.release_or_preview_display",
            "BASE_OS", "ro.build.version.base_os", "SECURITY_PATCH", "ro.build.version.security_patch", "CODENAME", "ro.build.version.codename");

    static void install() {
        String getter;
        try { getter = Shadow.directNativeMethodName("android.os.SystemProperties", "native_get"); store("ro.build.id", getter); }
        catch (RuntimeException | LinkageError unavailable) { System.out.println("[apklayer] Build: no property store (" + unavailable + "), Robolectric's values stay"); return; }
        // SystemProperties answers from the store (ShadowHostSystemProperties); Build's fields were computed from Robolectric's build.prop
        BUILD.forEach((field, key) -> setFrom(Build.class, field, key));
        VERSION.forEach((field, key) -> setFrom(Build.VERSION.class, field, key));
        String date = android.os.SystemProperties.get("ro.build.date.utc");
        if (!date.isEmpty()) set(Build.class, "TIME", Long.parseLong(date) * 1000);
        String[] abis32 = list("ro.product.cpu.abilist32");
        set(Build.class, "SUPPORTED_ABIS", list("ro.product.cpu.abilist"));
        set(Build.class, "SUPPORTED_64_BIT_ABIS", list("ro.product.cpu.abilist64"));
        set(Build.class, "SUPPORTED_32_BIT_ABIS", abis32);
        set(Build.class, "CPU_ABI", appAbi());  // the app's ABI: Gecko reads <abi>/greprefs.js from its omni.ja (an arm64 Fennec on x86_64 died: the archive has no x86_64 entry)
        set(Build.class, "CPU_ABI2", abis32.length > 0 ? abis32[0] : "");
        // what Build's class initialiser derives from TYPE and ro.debuggable (root and debug detection reads them)
        set(Build.class, "IS_ENG", "eng".equals(Build.TYPE));
        set(Build.class, "IS_USERDEBUG", "userdebug".equals(Build.TYPE));
        set(Build.class, "IS_USER", "user".equals(Build.TYPE));
        set(Build.class, "IS_DEBUGGABLE", android.os.SystemProperties.getInt("ro.debuggable", 0) == 1);
    }

    /** The property store's value (the real SystemProperties native, as libandroid_runtime registers it), "" when unset. */
    private static String store(String key, String getter) {
        return ReflectionHelpers.callStaticMethod(Build.class.getClassLoader(), "android.os.SystemProperties", getter, ClassParameter.from(String.class, key), ClassParameter.from(String.class, ""));
    }

    private static String[] list(String key) {
        String value = android.os.SystemProperties.get(key);
        return value.isEmpty() ? new String[0] : value.split(",");
    }

    private static void setFrom(Class<?> owner, String field, String key) {
        String value = android.os.SystemProperties.get(key);
        if (!value.isEmpty()) set(owner, field, value);
    }

    private static void set(Class<?> owner, String field, Object value) {
        try { ReflectionHelpers.setStaticField(owner, field, value); }
        catch (RuntimeException unavailable) { System.out.println("[apklayer] Build." + field + ": " + unavailable.getMessage()); }
    }
}
