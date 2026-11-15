package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowLoadedApk;

/** apklayer patch: the loaded package's class loader is the app's own (AppClassLoader), as on Android; Robolectric's is the sandbox's. */
@Implements(className = "android.app.LoadedApk", isInAndroidSdk = false)
public class ShadowHostLoadedApk extends ShadowLoadedApk {
    @Implementation @Override public ClassLoader getClassLoader() {
        HostProcessName.fill(loaded.getApplicationInfo());  // before the Application is made (makeApplication asks for the loader first): attachBaseContext sees it
        ClassLoader sandbox = super.getClassLoader();  // also creates the manifest's AppComponentFactory
        return AppClassLoader.app != null ? AppClassLoader.app : sandbox;
    }

    @Implementation @Override public ClassLoader getSplitClassLoader(String splitName) throws android.content.pm.PackageManager.NameNotFoundException {
        return getClassLoader();
    }

    @org.robolectric.annotation.RealObject private android.app.LoadedApk loaded;

    /** No Android class loader (PathClassLoader over the dex files): the app's classes come from getClassLoader above. updateApplicationInfo
     *  calls this when getPackageInfo finds the cached package's resources out of date (an activity context's ActivityThread.getPackageInfo
     *  after an APK under the running app was rewritten); building one failed in BaseDexClassLoader.reportClassLoaderChain on the JVM. */
    @Implementation protected void createOrUpdateClassLoaderLocked(java.util.List<String> addedPaths) {}

    /** The installed splits' resources join the base's (getResources), as PackageManagerService's split paths make them on Android; the app sees them in its ApplicationInfo. */
    @Implementation protected String[] getSplitPaths(String splitName) {
        android.content.pm.ApplicationInfo info = loaded.getApplicationInfo();
        ShadowHostPackageManager.installedPaths(info);
        return info.splitSourceDirs;
    }

    /** The same split paths for an activity's context (ContextImpl.createActivityContext reads mSplitResDirs, set from the ApplicationInfo
     *  before installedPaths filled its split paths: an activity saw only the base's resources). */
    @Implementation protected String[] getSplitResDirs() { return getSplitPaths(null); }
}
