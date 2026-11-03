package hello.system;

import android.content.Context;
import android.os.Build;

/** The device identity as detection code reads it: each Build field equals its ro.* property (android.os.SystemProperties, by reflection as apps do), and nothing names a test harness or emulator. */
final class BuildCheck implements Check {
    @Override public String run(Context context) throws Exception {
        java.lang.reflect.Method get = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
        Object[][] pairs = {
            {"MANUFACTURER", Build.MANUFACTURER, "ro.product.manufacturer"}, {"BRAND", Build.BRAND, "ro.product.brand"}, {"MODEL", Build.MODEL, "ro.product.model"},
            {"DEVICE", Build.DEVICE, "ro.product.device"}, {"PRODUCT", Build.PRODUCT, "ro.product.name"}, {"FINGERPRINT", Build.FINGERPRINT, "ro.build.fingerprint"},
            {"ID", Build.ID, "ro.build.id"}, {"DISPLAY", Build.DISPLAY, "ro.build.display.id"}, {"TAGS", Build.TAGS, "ro.build.tags"}, {"TYPE", Build.TYPE, "ro.build.type"},
            {"VERSION.RELEASE", Build.VERSION.RELEASE, "ro.build.version.release"}, {"VERSION.INCREMENTAL", Build.VERSION.INCREMENTAL, "ro.build.version.incremental"},
            {"VERSION.SECURITY_PATCH", Build.VERSION.SECURITY_PATCH, "ro.build.version.security_patch"}, {"VERSION.CODENAME", Build.VERSION.CODENAME, "ro.build.version.codename"},
            {"VERSION.SDK_INT", String.valueOf(Build.VERSION.SDK_INT), "ro.build.version.sdk"}, {"SUPPORTED_ABIS", String.join(",", Build.SUPPORTED_ABIS), "ro.product.cpu.abilist"}};
        StringBuilder wrong = new StringBuilder();
        for (Object[] p : pairs) {
            String field = (String) p[1], prop = (String) get.invoke(null, p[2]);
            System.out.println("[sys] build " + p[0] + "='" + field + "' " + p[2] + "='" + prop + "'");
            if (field == null || field.isEmpty() || !field.equals(prop)) wrong.append(p[0]).append(" differs from ").append(p[2]).append("; ");
            String lower = String.valueOf(field).toLowerCase(java.util.Locale.ROOT);
            for (String bad : new String[] {"robolectric", "unknown", "generic", "emulator", "sdk_gphone", "goldfish", "ranchu", "test-harness"})
                if (lower.contains(bad)) wrong.append(p[0]).append(" names '").append(bad).append("'; ");
        }
        Check.require(wrong.length() == 0, wrong.toString());
        return Build.FINGERPRINT;
    }
}
