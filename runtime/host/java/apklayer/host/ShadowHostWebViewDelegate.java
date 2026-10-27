package apklayer.host;

import android.content.Context;
import android.content.res.ApkAssets;
import android.content.res.AssetManager;
import java.io.IOException;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * The provider's resources become part of the app's: on a device WebViewDelegate.addWebViewAssetPath tells the ResourcesManager, which appends the provider APK as a shared
 * library to every Resources of the app. Here the provider APK is appended to the context's AssetManager (and recorded in the ApplicationInfo for those made later).
 */
@Implements(className = "android.webkit.WebViewDelegate", isInAndroidSdk = false)
public class ShadowHostWebViewDelegate {
    private static void forgetCached(String path) {
        java.util.Map<Object, Long> cache = org.robolectric.util.ReflectionHelpers.getStaticField(org.robolectric.res.android.ApkAssetsCache.class, "cachedApkAssetsPtrs");
        synchronized (cache) {
            cache.keySet().removeIf(key -> {
                for (java.lang.reflect.Field field : key.getClass().getDeclaredFields()) {
                    if (field.getType() != String.class) continue;
                    field.setAccessible(true);
                    try { if (path.equals(field.get(key))) return true; } catch (IllegalAccessException ignored) { }
                }
                return false;
            });
        }
    }

    @Implementation public void addWebViewAssetPath(Context context) {
        String path = android.webkit.WebViewFactory.getLoadedPackageInfo().applicationInfo.sourceDir;
        AssetManager assets = context.getAssets();
        ApkAssets[] current = assets.getApkAssets();
        for (ApkAssets loaded : current) if (path.equals(loaded.getAssetPath())) return;
        forgetCached(path); // Robolectric caches an ApkAssets by path: the plain one made when the provider's manifest was read would be returned instead of a dynamic one
        try {
            ApkAssets[] next = java.util.Arrays.copyOf(current, current.length + 1);
            next[current.length] = ApkAssets.loadFromPath(path, ApkAssets.PROPERTY_DYNAMIC);
            assets.setApkAssets(next, true);
        } catch (IOException failure) { throw new IllegalStateException("Cannot add the WebView provider's resources: " + path, failure); }
        android.content.pm.ApplicationInfo info = context.getApplicationInfo();
        String[] libs = info.sharedLibraryFiles == null ? new String[0] : info.sharedLibraryFiles;
        String[] more = java.util.Arrays.copyOf(libs, libs.length + 1);
        more[libs.length] = path;
        info.sharedLibraryFiles = more;
        System.out.println("[apklayer] WebView resources added: " + assets.getAssignedPackageIdentifiers());
    }
}
