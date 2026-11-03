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
}
