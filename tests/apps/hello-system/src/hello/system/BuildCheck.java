package hello.system;

import android.content.Context;
import android.os.Build;

/** The device identity as detection code reads it: each Build field equals its ro.* property (android.os.SystemProperties, by reflection as apps do), and nothing names a test harness or emulator; a static initialiser (App) saw the same before Application.onCreate; a release build (user/release-keys). */
final class BuildCheck implements Check {
    @Override public String run(Context context) throws Exception {
        java.lang.reflect.Method get = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
        Object[][] pairs = {
            {"MANUFACTURER", Build.MANUFACTURER, "ro.product.manufacturer"}, {"BRAND", Build.BRAND, "ro.product.brand"}, {"MODEL", Build.MODEL, "ro.product.model"},
            {"DEVICE", Build.DEVICE, "ro.product.device"}, {"PRODUCT", Build.PRODUCT, "ro.product.name"}, {"FINGERPRINT", Build.FINGERPRINT, "ro.build.fingerprint"},
            {"ID", Build.ID, "ro.build.id"}, {"DISPLAY", Build.DISPLAY, "ro.build.display.id"}, {"TAGS", Build.TAGS, "ro.build.tags"}, {"TYPE", Build.TYPE, "ro.build.type"},
            {"VERSION.RELEASE", Build.VERSION.RELEASE, "ro.build.version.release"}, {"VERSION.INCREMENTAL", Build.VERSION.INCREMENTAL, "ro.build.version.incremental"},
            {"VERSION.SECURITY_PATCH", Build.VERSION.SECURITY_PATCH, "ro.build.version.security_patch"}, {"VERSION.CODENAME", Build.VERSION.CODENAME, "ro.build.version.codename"},
            {"VERSION.SDK_INT", String.valueOf(Build.VERSION.SDK_INT), "ro.build.version.sdk"}, {"SUPPORTED_ABIS", String.join(",", Build.SUPPORTED_ABIS), "ro.product.cpu.abilist"},
            {"USER", Build.USER, "ro.build.user"}, {"HOST", Build.HOST, "ro.build.host"}};
        StringBuilder wrong = new StringBuilder();
        for (Object[] p : pairs) {
            String field = (String) p[1], prop = (String) get.invoke(null, p[2]);
            System.out.println("[sys] build " + p[0] + "='" + field + "' " + p[2] + "='" + prop + "'");
            if (field == null || field.isEmpty() || !field.equals(prop)) wrong.append(p[0]).append(" differs from ").append(p[2]).append("; ");
            String lower = String.valueOf(field).toLowerCase(java.util.Locale.ROOT);
            for (String bad : new String[] {"robolectric", "unknown", "generic", "emulator", "sdk_gphone", "goldfish", "ranchu", "test-harness"})
                if (lower.contains(bad)) wrong.append(p[0]).append(" names '").append(bad).append("'; ");
        }
        // as a static initialiser saw them, before Application.onCreate; and a release build, as root detectors expect of a shipped device
        Object[][] early = {{"static FINGERPRINT", App.STATIC_FINGERPRINT, "ro.build.fingerprint"}, {"static TAGS", App.STATIC_TAGS, "ro.build.tags"},
            {"static TYPE", App.STATIC_TYPE, "ro.build.type"}, {"static MODEL", App.STATIC_MODEL, "ro.product.model"}};
        for (Object[] p : early) {
            String prop = (String) get.invoke(null, p[2]);
            System.out.println("[sys] build " + p[0] + "='" + p[1] + "' " + p[2] + "='" + prop + "'");
            if (!prop.equals(p[1])) wrong.append(p[0]).append(" differs from ").append(p[2]).append("; ");
        }
        if (!"release-keys".equals(Build.TAGS) || !"user".equals(Build.TYPE)) wrong.append("not a release build: ").append(Build.TYPE).append('/').append(Build.TAGS).append("; ");
        for (String key : new String[] {"ro.build.display.id", "ro.build.description", "ro.build.flavor", "ro.system.build.fingerprint"}) {
            String value = (String) get.invoke(null, key);
            if (value.contains("test-keys") || value.contains("userdebug")) wrong.append(key).append("='").append(value).append("'; ");
        }
        if (!"0".equals(get.invoke(null, "ro.debuggable"))) wrong.append("ro.debuggable is not 0; ");
        // a build number, not the builder's name a build without BUILD_NUMBER writes (eng.<user>)
        if (Build.VERSION.INCREMENTAL.startsWith("eng.") || Build.FINGERPRINT.contains("/eng.") || ((String) get.invoke(null, "ro.build.description")).contains(" eng."))
            wrong.append("a builder's name in the build number: ").append(Build.VERSION.INCREMENTAL).append("; ");
        // a release build's builder (a build-farm host), not the machine that built the tree (apklayer.prop)
        if (!"android-build".equals(Build.USER) || !Build.HOST.startsWith("abfarm-")) wrong.append("builder ").append(Build.USER).append('@').append(Build.HOST).append("; ");
        Check.require(wrong.length() == 0, wrong.toString());
        return Build.FINGERPRINT;
    }
}
