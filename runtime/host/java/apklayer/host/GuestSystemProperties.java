package apklayer.host;

import java.lang.management.ManagementFactory;
import java.util.Properties;
import org.robolectric.annotation.internal.DoNotInstrument;

/** The application's libcore property bag. Host JVM configuration remains private and unchanged. */
@DoNotInstrument
public final class GuestSystemProperties {
    private GuestSystemProperties() {}

    private static final class State {
        static final Properties fixed = fixedProperties();
        static volatile Properties properties = newProperties(null);
    }

    /** ART's boot class path on a device (Android 13 measured with tests/identity; ANDROID BOOTCLASSPATH). */
    private static final String BOOT_CLASS_PATH = "/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:"
            + "/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:"
            + "/system/framework/framework.jar:/system/framework/framework-graphics.jar:/system/framework/ext.jar:/system/framework/telephony-common.jar:"
            + "/system/framework/voip-common.jar:/system/framework/ims-common.jar:/apex/com.android.i18n/javalib/core-icu4j.jar:"
            + "/apex/com.android.conscrypt/javalib/conscrypt.jar:/apex/com.android.wifi/javalib/framework-wifi.jar";

    private static Properties fixedProperties() {
        Properties p = new Properties();
        // ART receives application -D options, not the hosting JVM's implementation options.
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (!arg.startsWith("-D")) continue;
            int equals = arg.indexOf('=', 2);
            String key = arg.substring(2, equals < 0 ? arg.length() : equals);
            if (key.isEmpty() || backendProperty(key)) continue;
            p.setProperty(key, equals < 0 ? "" : arg.substring(equals + 1));
        }
        // Values defined by ART Runtime::GetVersion and libcore sun.misc.Version.
        p.setProperty("java.vm.version", "2.1.0");
        p.setProperty("java.runtime.name", "Android Runtime");
        p.setProperty("java.runtime.version", "0.9");
        p.setProperty("java.home", "/apex/com.android.art");
        p.setProperty("java.class.path", ".");
        String abi = HostBuild.appAbi();
        String lib = abi.equals("armeabi-v7a") ? "lib" : "lib64";
        p.setProperty("java.library.path", "/system/" + lib + ":/system_ext/" + lib + ":/vendor/" + lib);
        p.setProperty("os.name", "Linux");
        p.setProperty("os.version", System.getProperty("os.version", ""));
        p.setProperty("os.arch", switch (abi) {
            case "arm64-v8a" -> "aarch64";
            case "armeabi-v7a" -> "armv7l";
            default -> "x86_64";
        });
        int uid = android.os.Process.myUid();
        p.setProperty("user.name", "u" + (uid / 100000) + "_a" + (uid % 100000 - 10000));
        p.setProperty("user.dir", "/");
        // libcore's System.initUnchangeableSystemProperties / RuntimeInit.initialize and the ICU, zlib and OpenSSL versions the runtime reports
        p.setProperty("java.library.path", "/system/" + lib + ":/system/system_ext/" + lib + ":/system/product/" + lib);
        p.setProperty("java.boot.class.path", BOOT_CLASS_PATH);
        java.util.Locale locale = java.util.Locale.getDefault();
        p.setProperty("user.language", locale.getLanguage().isEmpty() ? "en" : locale.getLanguage());
        p.setProperty("user.region", locale.getCountry().isEmpty() ? "US" : locale.getCountry());
        p.setProperty("http.agent", "Dalvik/2.1.0 (Linux; U; Android " + android.os.Build.VERSION.RELEASE + "; " + android.os.Build.MODEL + " Build/" + android.os.Build.ID + ")");
        try {
            p.setProperty("android.icu.library.version", android.icu.util.VersionInfo.ICU_VERSION.getVersionString(2, 2));
            p.setProperty("android.icu.unicode.version", android.icu.lang.UCharacter.getUnicodeVersion().getVersionString(2, 2));
        } catch (LinkageError | RuntimeException noIcu) { /* absent */ }
        p.setProperty("android.openssl.version", "OpenSSL 1.1.1 (compatible; BoringSSL)");
        p.setProperty("android.zlib.version", "1.3.1");
        for (String[] pair : AndroidHardcodedSystemProperties.STATIC_PROPERTIES) {
            if (pair[1] == null) p.remove(pair[0]);
            else p.setProperty(pair[0], pair[1]);
        }
        return p;
    }

    private static boolean backendProperty(String key) {
        // cli/env.cpp and cli/apps.cpp supply these hosting options. Other -D options belong
        // to the caller, including java.net.*, javax.* and user.*; AOSP's table still wins.
        return key.startsWith("apklayer.") || key.startsWith("robolectric.")
                || key.startsWith("org.robolectric.")
                || key.equals("java.io.tmpdir") || key.equals("java.awt.headless");
    }

    private static Properties newProperties(Properties supplied) {
        Properties p = new PropertiesWithNonOverrideableDefaults(State.fixed);
        if (supplied != null) p.putAll(supplied);
        else {
            p.setProperty("user.home", "");
            String pkg = System.getProperty("apklayer.package");
            p.setProperty("java.io.tmpdir", pkg == null ? "/tmp"
                    : "/data/user/" + (android.os.Process.myUid() / 100000) + "/" + pkg + "/cache");
        }
        return p;
    }

    public static Properties getProperties() { return State.properties; }
    public static void setProperties(Properties properties) { State.properties = newProperties(properties); }
    public static String getProperty(String key) { return getProperty(key, null); }
    public static String getProperty(String key, String fallback) {
        checkKey(key);
        return State.properties.getProperty(key, fallback);
    }
    public static String setProperty(String key, String value) {
        checkKey(key);
        return (String) State.properties.setProperty(key, value);
    }
    public static String clearProperty(String key) {
        checkKey(key);
        return (String) State.properties.remove(key);
    }
    private static void checkKey(String key) {
        if (key == null) throw new NullPointerException("key == null");
        if (key.isEmpty()) throw new IllegalArgumentException("key is empty");
    }

    // These JDK helpers read System internally, so calls from app code also cross the seam.
    public static boolean getBoolean(String name) {
        try { return Boolean.parseBoolean(getProperty(name)); }
        catch (IllegalArgumentException | NullPointerException e) { return false; }
    }
    public static Integer getInteger(String name) { return getInteger(name, (Integer) null); }
    public static Integer getInteger(String name, int fallback) { return getInteger(name, Integer.valueOf(fallback)); }
    public static Integer getInteger(String name, Integer fallback) {
        try {
            String value = getProperty(name);
            if (value != null) return Integer.decode(value);
        } catch (IllegalArgumentException | NullPointerException e) { }
        return fallback;
    }
    public static Long getLong(String name) { return getLong(name, (Long) null); }
    public static Long getLong(String name, long fallback) { return getLong(name, Long.valueOf(fallback)); }
    public static Long getLong(String name, Long fallback) {
        try {
            String value = getProperty(name);
            if (value != null) return Long.decode(value);
        } catch (IllegalArgumentException | NullPointerException e) { }
        return fallback;
    }

    /** Exact public static signatures only; shared by reflection and method-handle lookup. */
    static java.lang.reflect.Method replacement(java.lang.reflect.Method method) {
        Class<?> owner = method.getDeclaringClass();
        String name = method.getName();
        boolean propertyMethod = owner == System.class && (name.equals("getProperty")
                || name.equals("setProperty") || name.equals("clearProperty")
                || name.equals("getProperties") || name.equals("setProperties"));
        if (!propertyMethod && !(owner == Boolean.class && name.equals("getBoolean"))
                && !(owner == Integer.class && name.equals("getInteger"))
                && !(owner == Long.class && name.equals("getLong"))) return method;
        try { return GuestSystemProperties.class.getMethod(name, method.getParameterTypes()); }
        catch (NoSuchMethodException e) { throw new AssertionError(e); }
    }
}
