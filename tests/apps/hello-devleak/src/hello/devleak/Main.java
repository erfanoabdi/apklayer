package hello.devleak;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Prints what an app can read about the device that a native phone answers differently: "[devleak] key=value" per line, for diffing two hosts. */
public class Main extends Activity {
    static void put(String k, Object v) { System.out.println("[devleak] " + k + "=" + String.valueOf(v).replace("\n", "\\n")); }

    static void file(String key, String path) {
        try (BufferedReader r = new BufferedReader(new java.io.FileReader(path))) {
            String l; int n = 0; while ((l = r.readLine()) != null && n++ < 40) put(key, l);
        } catch (Throwable t) { put(key, "THROWS " + t.getClass().getSimpleName()); }
    }

    static void exec(String key, String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String l; while ((l = r.readLine()) != null) put(key, l);
            }
        } catch (Throwable t) { put(key, "THROWS " + t); }
    }

    void table(String name, Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, "name")) {
            if (c == null) { put(name, "null cursor"); return; }
            int n = c.getColumnIndex("name"), v = c.getColumnIndex("value");
            while (c.moveToNext()) put(name + "." + c.getString(n), c.getString(v));
        } catch (Throwable t) { put(name, "THROWS " + t); }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        for (String k : new String[] {"os.arch", "java.vm.name", "os.name", "os.version", "java.vendor", "java.vm.version", "java.home", "sun.arch.data.model", "user.name", "user.home", "java.io.tmpdir"})
            put("prop." + k, System.getProperty(k));
        put("build.SUPPORTED_ABIS", String.join(",", Build.SUPPORTED_ABIS));
        put("build.SUPPORTED_64", String.join(",", Build.SUPPORTED_64_BIT_ABIS));
        put("build.SUPPORTED_32", String.join(",", Build.SUPPORTED_32_BIT_ABIS));
        put("build.CPU_ABI", Build.CPU_ABI + " " + Build.CPU_ABI2);
        for (String f : new String[] {"HARDWARE", "BOARD", "DEVICE", "FINGERPRINT", "MODEL", "MANUFACTURER", "BRAND", "PRODUCT", "SOC_MODEL", "SOC_MANUFACTURER", "ID", "DISPLAY", "TAGS", "TYPE", "HOST", "USER", "BOOTLOADER", "RADIO"}) {
            try { put("build." + f, Build.class.getField(f).get(null)); } catch (Throwable t) { put("build." + f, "THROWS " + t); }
        }
        put("build.SDK", Build.VERSION.SDK_INT + " " + Build.VERSION.RELEASE + " " + Build.VERSION.SECURITY_PATCH + " " + Build.TIME);
        for (String f : new String[] {"/proc/version", "/proc/sys/kernel/osrelease", "/proc/sys/kernel/ostype", "/proc/sys/kernel/hostname", "/proc/sys/kernel/arch", "/proc/sys/kernel/version", "/proc/cpuinfo", "/proc/self/cgroup", "/proc/self/status"})
            file("file" + f, f);
        for (String k : new String[] {"device_name", "development_settings_enabled", "adb_enabled", "usb_mass_storage_enabled", "install_non_market_apps", "stay_on_while_plugged_in", "airplane_mode_on", "bluetooth_name", "android_id"}) {
            put("global." + k, Settings.Global.getString(getContentResolver(), k));
            put("secure." + k, Settings.Secure.getString(getContentResolver(), k));
            put("system." + k, Settings.System.getString(getContentResolver(), k));
        }
        table("global", Settings.Global.CONTENT_URI);
        table("secure", Settings.Secure.CONTENT_URI);
        table("system", Settings.System.CONTENT_URI);
        put("DONE", 1);
        finish();
    }
}
