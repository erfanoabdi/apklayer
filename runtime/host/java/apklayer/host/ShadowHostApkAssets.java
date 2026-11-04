package apklayer.host;

import android.content.res.ApkAssets;
import java.io.IOException;
import org.robolectric.annotation.ClassName;
import org.robolectric.shadows.ResourceModeShadowPicker;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowApkAssets;
import org.robolectric.shadows.ShadowArscApkAssets9;
import org.robolectric.shadows.ShadowNativeApkAssets;

/**
 * LineageOS's AssetManager also loads a hardcoded /system/framework/org.lineageos.platform-res.apk. There is no such
 * system directory here, so the path is redirected to the copy in the native runtime ({@code -Dapklayer.platformres},
 * set by bin/apklayer when {@code android-root/system/framework/org.lineageos.platform-res.apk} exists).
 */
@Implements(value = ApkAssets.class, shadowPicker = ShadowHostApkAssets.Picker.class, isInAndroidSdk = false)
public class ShadowHostApkAssets extends ShadowArscApkAssets9 {
    /** Robolectric's own picker (ShadowApkAssets.Picker) with this shadow in place of the legacy-mode one. */
    public static class Picker extends ResourceModeShadowPicker<ShadowApkAssets> {
        public Picker() {
            super(null, ShadowHostApkAssets.class, ShadowHostApkAssets.class, ShadowHostApkAssets.class, Native.class);
        }
    }

    /**
     * The native-resources ApkAssets with Robolectric's per-path cache kept only while the file is unchanged: an installed package that was updated
     * (HubInstaller, `apklayer install`) has new contents at the same path, and the package manager parses it again (HubPackages) as Android's does.
     */
    @Implements(value = ApkAssets.class, minSdk = android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM, callNativeMethodsByDefault = true, shadowPicker = Picker.class, isInAndroidSdk = false)
    public static class Native extends ShadowNativeApkAssets {
        private static final java.util.Map<String, long[]> loaded = new java.util.HashMap<>();  // flags:path -> {modified, size, native pointer}

        @Implementation
        protected static long nativeLoad(int format, String path, int flags, android.content.res.loader.AssetsProvider asset) {
            java.io.File file = new java.io.File(path);
            if (!file.isFile()) return ShadowNativeApkAssets.nativeLoad(format, path, flags, asset);  // the framework's (a name, not a file) and its cache
            // the flags belong to the key: an APK read as a plain package (a manifest) and loaded as a shared library (PROPERTY_DYNAMIC, the WebView provider)
            // are two assets; Robolectric's path-only cache handed back the plain one, whose package id then clashed with the app's
            String key = flags + ":" + path;
            synchronized (loaded) {
                long[] old = loaded.get(key);
                if (old != null && old[0] == file.lastModified() && old[1] == file.length()) return old[2];
                // shortcut: the replaced file's assets are never destroyed (Robolectric keeps every one); fine for the few updates of a session
                long ptr = org.robolectric.util.reflector.Reflector.reflector(Load.class).nativeLoad(format, path, flags, asset);
                if (ptr > 0) loaded.put(key, new long[] {file.lastModified(), file.length(), ptr});
                return ptr;
            }
        }

        @org.robolectric.util.reflector.ForType(ApkAssets.class)
        interface Load {
            @org.robolectric.util.reflector.Static
            @org.robolectric.util.reflector.Direct(format = org.robolectric.util.reflector.Direct.DirectFormat.NATIVE)
            long nativeLoad(int format, String path, int flags, android.content.res.loader.AssetsProvider asset);
        }
    }

    private static final String LINEAGE_APK_PATH = "/system/framework/org.lineageos.platform-res.apk";

    @Implementation(minSdk = android.os.Build.VERSION_CODES.R)
    protected static long nativeLoad(int format, String path, int flags,
            @ClassName("android.content.res.loader.AssetsProvider") Object asset) throws IOException {
        String redirect = System.getProperty("apklayer.platformres");
        if (redirect != null && LINEAGE_APK_PATH.equals(path)) path = redirect;
        return ShadowArscApkAssets9.nativeLoad(format, path, flags, asset);
    }
}
