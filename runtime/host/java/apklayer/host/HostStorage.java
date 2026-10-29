package apklayer.host;

import android.app.Application;
import android.os.Environment;
import android.os.Process;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowStorageManager;

/** What every Android device has and the sandbox leaves empty: the primary (emulated, mounted) storage volume, in the instance's storage directory. */
final class HostStorage {
    private HostStorage() {}

    static void install(Application app) {
        java.io.File internal = HostVolumes.internal();  // the instance's own sdcard, not a temp directory
        if (internal != null) { internal.mkdirs(); org.robolectric.shadows.ShadowEnvironment.setExternalStorageDirectory(internal.toPath()); org.robolectric.shadows.ShadowEnvironment.setExternalStoragePublicDirectory(internal.toPath()); }  // getExternalFilesDir and the public directories are below it
        StorageManager manager = app.getSystemService(StorageManager.class);
        if (manager != null && manager.getStorageVolumes().isEmpty()) {
            StorageVolume primary = primaryVolume();
            if (primary != null) ((ShadowStorageManager) Shadow.extract(manager)).addStorageVolume(primary);
        }
    }

    /** StorageVolume's constructor is hidden and varies by release: parameters are filled by type and order (id, description, state; primary, removable, emulated). */
    private static StorageVolume primaryVolume() {
        Constructor<?> best = null;
        for (Constructor<?> c : StorageVolume.class.getDeclaredConstructors()) if (best == null || c.getParameterCount() > best.getParameterCount()) best = c;
        if (best == null) return null;
        File root = Environment.getExternalStorageDirectory();
        String[] strings = {"emulated", "Internal storage", null, Environment.MEDIA_MOUNTED};
        boolean[] flags = {true, false, true};  // primary, removable, emulated
        Object[] args = new Object[best.getParameterCount()];
        int i = 0, s = 0, b = 0;
        for (Parameter p : best.getParameters()) {
            Class<?> t = p.getType();
            if (t == String.class) args[i] = s < strings.length ? strings[s++] : null;
            else if (t == File.class) args[i] = root;
            else if (t == boolean.class) args[i] = b < flags.length && flags[b++];
            else if (t == long.class) args[i] = 0L;
            else if (t == int.class) args[i] = 0;
            else if (t == android.os.UserHandle.class) args[i] = Process.myUserHandle();
            else args[i] = null;
            i++;
        }
        try { best.setAccessible(true); return (StorageVolume) best.newInstance(args); }
        catch (ReflectiveOperationException failure) { System.out.println("[apklayer] storage: cannot build the primary volume: " + (failure.getCause() != null ? failure.getCause() : failure)); return null; }
    }
}
