package hello.javaprobe;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.*;
import android.os.*;
import android.os.Process;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Dumps what the Java side answers an anti-cheat's JNI queries with: "[jp] key=value" per line, for diffing two hosts (translated vs native). */
public class Main extends Activity {
    static void put(String k, Object v) { System.out.println("[jp] " + k + "=" + String.valueOf(v).replace("\n", "\\n")); }

    interface Q { Object get() throws Throwable; }
    static void q(String k, Q q) { try { put(k, q.get()); } catch (Throwable t) { put(k, "THROWS " + t.getClass().getSimpleName() + ": " + t.getMessage()); } }

    static void file(String key, String path, int max) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String l; int n = 0; while ((l = r.readLine()) != null && n++ < max) put(key, l);
        } catch (Throwable t) { put(key, "THROWS " + t.getClass().getSimpleName()); }
    }

    static void exec(String key, String... cmd) {
        try {
            java.lang.Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) { String l; while ((l = r.readLine()) != null) put(key, l); }
        } catch (Throwable t) { put(key, "THROWS " + t); }
    }

    static void statics(String pre, Class<?> c) {
        Field[] fs = c.getFields(); Arrays.sort(fs, (a, b) -> a.getName().compareTo(b.getName()));
        for (Field f : fs) { try { Object v = f.get(null); put(pre + f.getName(), v instanceof Object[] ? Arrays.toString((Object[]) v) : v); } catch (Throwable t) { put(pre + f.getName(), "THROWS " + t); } }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { System.loadLibrary("hello"); put("loadLibrary", "ok"); } catch (Throwable t) { put("loadLibrary", "THROWS " + t); }
        statics("Build.", Build.class); statics("Build.VERSION.", Build.VERSION.class);
        q("Build.getRadioVersion", Build::getRadioVersion);
        q("Build.getSerial", Build::getSerial);
        for (String k : new String[] {"ro.product.cpu.abi", "ro.product.cpu.abilist", "ro.product.cpu.abilist64", "ro.product.cpu.abilist32", "ro.dalvik.vm.native.bridge", "ro.hardware", "ro.kernel.qemu", "ro.boot.hardware", "ro.build.characteristics", "ro.product.board", "gsm.version.baseband", "ro.debuggable", "ro.secure", "ro.build.type", "ro.boot.verifiedbootstate", "ro.boot.qemu", "init.svc.adbd"})
            q("SystemProperties." + k, () -> { Method m = Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class); return m.invoke(null, k, "<unset>"); });
        exec("getprop", "getprop");
        for (String k : new String[] {"device_name", "development_settings_enabled", "adb_enabled", "android_id", "bluetooth_name", "airplane_mode_on", "install_non_market_apps"}) {
            q("Settings.Global." + k, () -> android.provider.Settings.Global.getString(getContentResolver(), k));
            q("Settings.Secure." + k, () -> android.provider.Settings.Secure.getString(getContentResolver(), k));
            q("Settings.System." + k, () -> android.provider.Settings.System.getString(getContentResolver(), k));
        }
        PackageManager pm = getPackageManager();
        String[] pkgs = {getPackageName(), "com.kurogame.gplay.punishing.grayraven.en"};
        for (String p : pkgs) {
            q("pm.pi." + p, () -> {
                PackageInfo pi = pm.getPackageInfo(p, PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_SIGNATURES | PackageManager.GET_PERMISSIONS | PackageManager.GET_CONFIGURATIONS);
                ApplicationInfo a = pi.applicationInfo;
                StringBuilder sb = new StringBuilder("ver=" + pi.versionName + "/" + pi.getLongVersionCode() + " first=" + pi.firstInstallTime + " last=" + pi.lastUpdateTime
                    + " installer=" + pm.getInstallSourceInfo(p).getInstallingPackageName()
                    + " uid=" + a.uid + " target=" + a.targetSdkVersion + " min=" + a.minSdkVersion + " flags=0x" + Integer.toHexString(a.flags)
                    + " sourceDir=" + a.sourceDir + " publicSourceDir=" + a.publicSourceDir + " dataDir=" + a.dataDir + " deDataDir=" + a.deviceProtectedDataDir
                    + " nativeLibDir=" + a.nativeLibraryDir + " splitNames=" + Arrays.toString(pi.splitNames) + " splitSource=" + Arrays.toString(a.splitSourceDirs)
                    + " sharedLibs=" + Arrays.toString(a.sharedLibraryFiles) + " process=" + a.processName + " enabled=" + a.enabled);
                for (String f : new String[] {"primaryCpuAbi", "secondaryCpuAbi", "nativeLibraryRootDir", "seInfo", "scanSourceDir", "privateFlags", "credentialProtectedDataDir"}) { try { sb.append(" ").append(f).append("=").append(ApplicationInfo.class.getField(f).get(a)); } catch (Throwable t) { sb.append(" ").append(f).append("=?"); } }
                if (pi.signatures != null) for (Signature s : pi.signatures) sb.append(" sig=").append(s.hashCode()).append("/").append(s.toByteArray().length);
                if (pi.signingInfo != null) sb.append(" multi=").append(pi.signingInfo.hasMultipleSigners()).append(" pastRot=").append(pi.signingInfo.hasPastSigningCertificates());
                sb.append(" perms=").append(pi.requestedPermissions == null ? 0 : pi.requestedPermissions.length);
                return sb;
            });
        }
        q("pm.installed.count", () -> pm.getInstalledPackages(0).size());
        q("pm.installed", () -> { List<String> l = new ArrayList<>(); for (PackageInfo p : pm.getInstalledPackages(0)) l.add(p.packageName); Collections.sort(l); return l; });
        q("pm.apps", () -> { List<String> l = new ArrayList<>(); for (ApplicationInfo p : pm.getInstalledApplications(0)) l.add(p.packageName + ":" + p.uid); Collections.sort(l); return l; });
        q("pm.features", () -> { List<String> l = new ArrayList<>(); for (FeatureInfo f : pm.getSystemAvailableFeatures()) l.add(String.valueOf(f.name)); Collections.sort(l); return l; });
        q("pm.sharedLibs", () -> Arrays.toString(pm.getSystemSharedLibraryNames()));
        q("pm.uidPkgs", () -> Arrays.toString(pm.getPackagesForUid(Process.myUid())));
        q("pm.nameForUid", () -> pm.getNameForUid(Process.myUid()));
        q("ctx.appInfo.nativeLibraryDir", () -> getApplicationInfo().nativeLibraryDir);
        q("ctx.appInfo.sourceDir", () -> getApplicationInfo().sourceDir);
        q("ctx.appInfo.dataDir", () -> getApplicationInfo().dataDir);
        q("ctx.filesDir", () -> getFilesDir()); q("ctx.cacheDir", () -> getCacheDir()); q("ctx.codeCacheDir", () -> getCodeCacheDir());
        q("ctx.noBackupFilesDir", () -> getNoBackupFilesDir());
        q("ctx.extFilesDir", () -> getExternalFilesDir(null)); q("ctx.extCacheDir", () -> getExternalCacheDir());
        q("ctx.obbDir", () -> getObbDir()); q("ctx.packageCodePath", () -> getPackageCodePath()); q("ctx.packageResourcePath", () -> getPackageResourcePath());
        q("ctx.dbPath", () -> getDatabasePath("x"));
        q("Environment.extStorage", () -> Environment.getExternalStorageDirectory()); q("Environment.extState", () -> Environment.getExternalStorageState());
        q("Environment.emulated", () -> Environment.isExternalStorageEmulated()); q("Environment.removable", () -> Environment.isExternalStorageRemovable());
        q("Environment.dataDir", () -> Environment.getDataDirectory()); q("Environment.root", () -> Environment.getRootDirectory()); q("Environment.cache", () -> Environment.getDownloadCacheDirectory());
        q("Process.myUid", Process::myUid); q("Process.myPid", Process::myPid); q("Process.myTid", Process::myTid); q("Process.is64Bit", Process::is64Bit);
        q("Process.myUserHandle", Process::myUserHandle); q("Process.isApplicationUid", () -> Process.isApplicationUid(Process.myUid()));
        q("Process.getUidForName.system", () -> Process.getUidForName("system"));
        q("Process.supportsProcesses", Process::supportsProcesses);
        file("proc.self.status", "/proc/self/status", 60);
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        q("am.memoryClass", am::getMemoryClass); q("am.largeMemoryClass", am::getLargeMemoryClass); q("am.isLowRam", am::isLowRamDevice);
        q("am.memInfo", () -> { ActivityManager.MemoryInfo m = new ActivityManager.MemoryInfo(); am.getMemoryInfo(m); return "total=" + m.totalMem + " threshold=" + m.threshold + " low=" + m.lowMemory + " availOK=" + (m.availMem > 0); });
        q("am.running", () -> { List<String> l = new ArrayList<>(); for (ActivityManager.RunningAppProcessInfo p : am.getRunningAppProcesses()) l.add(p.processName + ":" + p.uid + ":" + p.importance + ":" + Arrays.toString(p.pkgList)); return l; });
        q("am.services", () -> am.getRunningServices(50).size());
        q("am.monkey", ActivityManager::isUserAMonkey); q("am.runningInTest", ActivityManager::isRunningInTestHarness);
        q("am.deviceConfig", () -> am.getDeviceConfigurationInfo());
        q("am.exitReasons", () -> am.getHistoricalProcessExitReasons(getPackageName(), 0, 5).size());
        Runtime rt = Runtime.getRuntime();
        q("rt.cpus", rt::availableProcessors); q("rt.max", rt::maxMemory); q("rt.total", rt::totalMemory);
        Properties sp = System.getProperties(); List<String> keys = new ArrayList<>(); for (Object k : sp.keySet()) keys.add((String) k); Collections.sort(keys);
        for (String k : keys) put("prop." + k, sp.getProperty(k));
        q("env", () -> new TreeMap<>(System.getenv()));
        ClassLoader cl = getClassLoader(); int d = 0;
        for (ClassLoader x = cl; x != null; x = x.getParent(), d++) put("cl." + d, x.getClass().getName() + " " + x);
        q("cl.pathList", () -> { Field f = Class.forName("dalvik.system.BaseDexClassLoader").getDeclaredField("pathList"); f.setAccessible(true); return f.get(cl); });
        q("cl.findLibrary.hello", () -> ((dalvik.system.BaseDexClassLoader) cl).findLibrary("hello"));
        q("cl.findLibrary.anogs", () -> ((dalvik.system.BaseDexClassLoader) cl).findLibrary("anogs"));
        q("this.class.getProtectionDomain", () -> getClass().getProtectionDomain().getCodeSource());
        q("Activity.class.loader", () -> Activity.class.getClassLoader());
        q("Context.impl", () -> getBaseContext().getClass().getName());
        q("app.class", () -> getApplication().getClass().getName());
        TreeMap<String, Integer> tn = new TreeMap<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) tn.merge(t.getName().replaceAll("\\d+", "N") + "/" + t.getThreadGroup().getName() + "/d" + t.isDaemon(), 1, Integer::sum);
        put("threads", tn);
        q("stack", () -> { StringBuilder sb = new StringBuilder(); for (StackTraceElement e : new Throwable().getStackTrace()) sb.append(e.getClassName()).append('.').append(e.getMethodName()).append(' '); return sb; });
        q("mainStack", () -> { StringBuilder sb = new StringBuilder(); for (StackTraceElement e : Looper.getMainLooper().getThread().getStackTrace()) sb.append(e.getClassName()).append('.').append(e.getMethodName()).append(' '); return sb; });
        q("mainThread", () -> Looper.getMainLooper().getThread());
        DisplayMetrics dm = getResources().getDisplayMetrics();
        put("dm", dm); q("display", () -> getDisplay()); q("cfg", () -> getResources().getConfiguration());
        q("wm.bounds", () -> getWindowManager().getCurrentWindowMetrics().getBounds());
        q("display.refresh", () -> getDisplay().getRefreshRate());
        q("sensors", () -> { List<String> l = new ArrayList<>(); for (android.hardware.Sensor s : ((android.hardware.SensorManager) getSystemService(SENSOR_SERVICE)).getSensorList(android.hardware.Sensor.TYPE_ALL)) l.add(s.getName() + "|" + s.getVendor() + "|" + s.getType()); return l; });
        q("input", () -> { List<String> l = new ArrayList<>(); for (int id : InputDevice.getDeviceIds()) { InputDevice i = InputDevice.getDevice(id); l.add(id + ":" + i.getName() + ":" + i.getSources() + ":" + i.getVendorId() + ":" + i.getProductId() + ":" + i.isVirtual()); } return l; });
        for (String s : new String[] {CAMERA_SERVICE, BATTERY_SERVICE, TELEPHONY_SERVICE, WIFI_SERVICE, BLUETOOTH_SERVICE, CONNECTIVITY_SERVICE, USB_SERVICE, SENSOR_SERVICE, LOCATION_SERVICE, AUDIO_SERVICE, USER_SERVICE, POWER_SERVICE, "thermalservice", "device_policy", "accessibility", "input_method", "clipboard", "vibrator", "storage", "media_projection", "display"})
            q("svc." + s, () -> { Object o = getSystemService(s); return o == null ? null : o.getClass().getName(); });
        q("battery.status", () -> { Intent i = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)); return i == null ? null : i.getExtras().keySet().size() + " plugged=" + i.getIntExtra("plugged", -1) + " level=" + i.getIntExtra("level", -1) + " tech=" + i.getStringExtra("technology") + " temp=" + i.getIntExtra("temperature", -1) + " status=" + i.getIntExtra("status", -1); });
        q("tm.operator", () -> { android.telephony.TelephonyManager t = (android.telephony.TelephonyManager) getSystemService(TELEPHONY_SERVICE); return t.getNetworkOperatorName() + "|" + t.getSimOperator() + "|" + t.getSimState() + "|" + t.getPhoneType() + "|" + t.getNetworkType() + "|" + t.getNetworkCountryIso() + "|" + t.getSimCountryIso(); });
        q("cm.active", () -> { android.net.ConnectivityManager m = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE); android.net.NetworkCapabilities nc = m.getNetworkCapabilities(m.getActiveNetwork()); return nc == null ? null : nc.toString(); });
        q("wifi.info", () -> { android.net.wifi.WifiInfo w = ((android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE)).getConnectionInfo(); return w == null ? null : w.getSSID() + "|" + w.getBSSID() + "|" + w.getMacAddress() + "|" + w.getNetworkId(); });
        q("bt.adapter", () -> { android.bluetooth.BluetoothAdapter b = android.bluetooth.BluetoothAdapter.getDefaultAdapter(); return b == null ? null : b.getName() + "|" + b.getState(); });
        q("features", () -> pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) + "/" + pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) + "/" + pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) + "/" + pm.hasSystemFeature(PackageManager.FEATURE_WIFI) + "/" + pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER) + "/" + pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) + "/" + pm.hasSystemFeature("android.hardware.vulkan.version") + "/" + pm.hasSystemFeature("android.hardware.fingerprint"));
        for (String cn : new String[] {"de.robv.android.xposed.XposedBridge", "de.robv.android.xposed.XposedHelpers", "com.saurik.substrate.MS", "org.lsposed.lspd.core.Main", "io.github.libxposed.api.XposedInterface", "com.topjohnwu.magisk.core.App", "com.genymotion.genyd.Main", "com.android.internal.os.ZygoteInit", "org.robolectric.Robolectric", "org.robolectric.RuntimeEnvironment", "org.robolectric.android.internal.AndroidTestEnvironment", "apklayer.host.Main", "dalvik.system.VMRuntime", "dalvik.system.VMDebug", "libcore.io.Libcore", "com.android.internal.os.RuntimeInit", "android.os.Debug", "java.lang.management.ManagementFactory", "sun.misc.Unsafe", "jdk.internal.misc.Unsafe", "android.app.ActivityThread", "android.app.ApplicationPackageManager", "android.app.ContextImpl"}) {
            final ClassLoader l = cl;
            q("class." + cn, () -> { Class<?> k = Class.forName(cn, false, l); return "FOUND loader=" + k.getClassLoader(); });
        }
        q("debug.isDebuggerConnected", () -> Debug.isDebuggerConnected()); q("debug.waitingForDebugger", () -> Debug.waitingForDebugger());
        q("appFlags.debuggable", () -> (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        q("vmRuntime", () -> { Class<?> k = Class.forName("dalvik.system.VMRuntime"); Object r = k.getMethod("getRuntime").invoke(null); return k.getMethod("vmInstructionSet").invoke(r) + " " + k.getMethod("is64Bit").invoke(r) + " " + k.getMethod("vmVersion").invoke(r) + " " + k.getMethod("vmLibrary").invoke(r); });
        for (String f : new String[] {"/proc/self/cmdline", "/proc/self/limits", "/proc/self/cgroup", "/proc/self/mountinfo", "/proc/self/stat", "/proc/cpuinfo", "/proc/version", "/proc/meminfo", "/proc/cmdline", "/proc/self/attr/current"}) file("f" + f, f, 80);
        for (String dir : new String[] {"/proc/self/task", "/proc/self/fd", "/data/app", "/data/data", "/data/local/tmp", "/system", "/system/lib64", "/system/bin", "/system/xbin", "/vendor", "/sdcard", "/storage", "/storage/emulated/0", "/sys/class", "/dev", "/apex", "/system/framework"}) {
            File[] ls = new File(dir).listFiles(); List<String> n = new ArrayList<>(); if (ls != null) for (File x : ls) n.add(x.getName()); Collections.sort(n);
            put("ls" + dir, ls == null ? "null/denied exists=" + new File(dir).exists() : n.size() > 60 ? n.size() + " entries " + n.subList(0, 60) : n);
        }
        exec("id", "id"); exec("uname", "uname", "-a"); exec("ps", "ps", "-A"); exec("mount", "mount");
        put("DONE", 1);
        finish();
    }
}
