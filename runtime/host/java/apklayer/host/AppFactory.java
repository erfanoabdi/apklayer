package apklayer.host;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Service;
import android.content.ContentProvider;
import android.content.Intent;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;

/**
 * The manifest's AppComponentFactory instantiates the app's activities, services and providers, as ActivityThread does through
 * LoadedApk.getAppFactory() (Robolectric calls the constructors itself). SystemUI's injects them through its Dagger graph: SystemUIService has no
 * constructor without arguments. Each method answers null when the app has no factory of its own: the caller constructs as before.
 */
final class AppFactory {
    private AppFactory() {}

    private static AppComponentFactory own() {
        android.app.LoadedApk loaded = ReflectionHelpers.getField(RuntimeEnvironment.getApplication(), "mLoadedApk");
        AppComponentFactory factory = loaded == null ? null : loaded.getAppFactory();
        return factory == null || factory.getClass() == AppComponentFactory.class ? null : factory;
    }

    static Activity activity(Class<? extends Activity> cls, Intent intent) throws ReflectiveOperationException {
        AppComponentFactory factory = own();
        return factory == null ? null : factory.instantiateActivity(cls.getClassLoader(), cls.getName(), intent);
    }

    static Service service(Class<? extends Service> cls, Intent intent) throws ReflectiveOperationException {
        AppComponentFactory factory = own();
        return factory == null ? null : factory.instantiateService(cls.getClassLoader(), cls.getName(), intent);
    }

    static ContentProvider provider(Class<? extends ContentProvider> cls) throws ReflectiveOperationException {
        AppComponentFactory factory = own();
        return factory == null ? null : factory.instantiateProvider(cls.getClassLoader(), cls.getName());
    }
}
