package apklayer.host;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowLoadedApk;
import org.robolectric.util.ReflectionHelpers;

/** Supplies the process app loader and a separate loader for authorized foreign package contexts. */
@Implements(className = "android.app.LoadedApk", isInAndroidSdk = false)
public class ShadowHostLoadedApk extends ShadowLoadedApk {
    @Implementation @Override public ClassLoader getClassLoader() {
        if (isForeign(loaded)) return getClass().getClassLoader();
        HostProcessName.fill(loaded.getApplicationInfo());  // before the Application is made (makeApplication asks for the loader first): attachBaseContext sees it
        ClassLoader sandbox = super.getClassLoader();  // also creates the manifest's AppComponentFactory
        return AppClassLoader.app != null ? AppClassLoader.app : sandbox;
    }

    @Implementation @Override public ClassLoader getSplitClassLoader(String splitName) throws android.content.pm.PackageManager.NameNotFoundException {
        return getClassLoader();
    }

    @org.robolectric.annotation.RealObject private android.app.LoadedApk loaded;

    private ClassLoader foreignLoader;

    static boolean isForeign(android.app.LoadedApk apk) {
        String processPackage = System.getProperty("apklayer.package");
        return apk != null && processPackage != null && !processPackage.equals(apk.getPackageName());
    }

    /** Only a framework-created ContextImpl supplies the flags for this lookup. */
    synchronized ClassLoader classLoaderForContext(int flags) {
        if ((flags & Context.CONTEXT_INCLUDE_CODE) == 0
                || !ReflectionHelpers.<Boolean>getField(loaded, "mIncludeCode")
                || (loaded.isSecurityViolation() && (flags & Context.CONTEXT_IGNORE_SECURITY) == 0)) {
            return null;
        }
        ApplicationInfo info = loaded.getApplicationInfo();
        if (info == null || (info.flags & ApplicationInfo.FLAG_HAS_CODE) == 0) return null;
        if (foreignLoader != null) return foreignLoader;

        String apps = System.getProperty("apklayer.apps");
        String name = loaded.getPackageName();
        if (apps == null || name == null || !name.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")) return null;
        Path dir = Path.of(apps, name);
        Path base = dir.resolve("base.apk");
        Path classes = dir.resolve("classes.jar");
        if (!Files.isRegularFile(base) || !Files.isRegularFile(classes)) return null;

        // The installer converts the base and installed split DEX files into classes.jar.
        ClassLoader framework = getClass().getClassLoader();
        ClassLoader process = AppClassLoader.app;
        ClassLoader parent = new ClassLoader(framework) {
            @Override protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
                Class<?> type = super.loadClass(className, resolve);
                // The sandbox can fall back to the process APK. Keep its classes out of foreign lookups.
                if (process != null && type.getClassLoader() == process) throw new ClassNotFoundException(className);
                return type;
            }
        };
        try {
            foreignLoader = new URLClassLoader(name, new URL[]{classes.toUri().toURL()}, parent);
            return foreignLoader;
        } catch (MalformedURLException invalidPath) {
            throw new IllegalStateException("Invalid installed class path for " + name, invalidPath);
        }
    }

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
