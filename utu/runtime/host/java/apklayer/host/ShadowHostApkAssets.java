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
            super(null, ShadowHostApkAssets.class, ShadowHostApkAssets.class, ShadowHostApkAssets.class, ShadowNativeApkAssets.class);
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
