package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowLoadedApk;

/** apklayer patch: the loaded package's class loader is the app's own (AppClassLoader), as on Android; Robolectric's is the sandbox's. */
@Implements(className = "android.app.LoadedApk", isInAndroidSdk = false)
public class ShadowHostLoadedApk extends ShadowLoadedApk {
    @Implementation @Override public ClassLoader getClassLoader() {
        ClassLoader sandbox = super.getClassLoader();  // also creates the manifest's AppComponentFactory
        return AppClassLoader.app != null ? AppClassLoader.app : sandbox;
    }

    @Implementation @Override public ClassLoader getSplitClassLoader(String splitName) throws android.content.pm.PackageManager.NameNotFoundException {
        return getClassLoader();
    }

    @org.robolectric.annotation.RealObject private android.app.LoadedApk loaded;

    /** The installed splits' resources join the base's (getResources), as PackageManagerService's split paths make them on Android; the app sees them in its ApplicationInfo. */
    @Implementation protected String[] getSplitPaths(String splitName) {
        android.content.pm.ApplicationInfo info = loaded.getApplicationInfo();
        ShadowHostPackageManager.installedPaths(info);
        return info.splitSourceDirs;
    }
}
