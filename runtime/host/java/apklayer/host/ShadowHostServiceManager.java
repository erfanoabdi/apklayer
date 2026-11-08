package apklayer.host;

import android.os.IBinder;
import android.os.IInterface;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowServiceManager;
import org.robolectric.util.ReflectionHelpers;

/**
 * Robolectric's services of type NULL_PROXY / DEEP_PROXY answer every call with null/0: apps then "run but do not work". The binder handed out
 * for such a service gets a wrapper attached that reports the first call per method (FakeLog) and then answers like the fake did.
 */
@Implements(className = "android.os.ServiceManager", isInAndroidSdk = false)
public class ShadowHostServiceManager extends ShadowServiceManager {
    private static final Set<IBinder> wrapped = ConcurrentHashMap.newKeySet();
    /** Services Robolectric has no fake for that apps take for granted (Aurora Store's onboarding reads its link handling: getSystemService returned null). */
    static { addBinderService(binderServices, "domain_verification", android.content.pm.verify.domain.IDomainVerificationManager.class); }

    @Implementation protected static IBinder getService(String name) { IBinder hub = hub(name); return hub != null ? hub : orNative(name, report(name, ShadowServiceManager.getService(name))); }
    @Implementation protected static IBinder checkService(String name) { IBinder hub = hub(name); return hub != null ? hub : orNative(name, report(name, ShadowServiceManager.checkService(name))); }
    @Implementation protected static IBinder waitForService(String name) throws InterruptedException { IBinder hub = hub(name); return hub != null ? hub : orNative(name, report(name, ShadowServiceManager.waitForService(name))); }

    /**
     * A name Robolectric has no service for: the one the instance's own service manager has under it (the native stack's "SurfaceFlinger", "media.audio_policy"...), as on a device.
     * Not HAL instances ("android.hardware.vibrator.IVibrator/default": SELinux keeps them from apps on a device) nor apklayer's own ("apklayer.instance": the hub's, looked up through RealBinder).
     */
    private static IBinder orNative(String name, IBinder fake) {
        if (fake != null || name == null || name.indexOf('/') >= 0 || name.startsWith("apklayer.")) return fake;
        try { return RealBinder.lookup(name); }
        catch (Throwable noBinder) { return null; }  // a JVM without the native runtime (no kernel binder) has only Robolectric's services
    }

    /** System services the instance hub serves for every app: "account" (HubAccounts). Null for the others, or when no hub is running. */
    private static IBinder hub(String name) {
        if (!android.content.Context.ACCOUNT_SERVICE.equals(name)) return null;
        HubClient.attachEarly();  // the accounts service knows its callers by their registration (an app may ask from attachBaseContext)
        return RealBinder.lookup(HubAccounts.SERVICE);
    }

    private static IBinder report(String name, IBinder binder) {
        if (binder == null || wrapped.contains(binder)) return binder;
        try {
            Object entry = binderServices.get(name);
            String type = entry == null ? null : String.valueOf((Object) ReflectionHelpers.getField(entry, "binderType"));
            if ("NULL_PROXY".equals(type) || "DEEP_PROXY".equals(type)) {
                String descriptor = binder.getInterfaceDescriptor();
                IInterface fake = binder.queryLocalInterface(descriptor);
                if (fake != null && binder instanceof android.os.Binder) ((android.os.Binder) binder).attachInterface(wrap(name, fake), descriptor);
            }
        } catch (Throwable e) {
            System.out.println("[apklayer] fake report for " + name + " failed: " + e);
        }
        wrapped.add(binder);
        return binder;
    }

    private static IInterface wrap(String name, IInterface fake) {
        Set<Method> seen = ConcurrentHashMap.newKeySet();
        Class<?>[] types = fake.getClass().getInterfaces();
        String iface = types.length > 0 ? types[0].getSimpleName() : fake.getClass().getSimpleName();
        return (IInterface) Proxy.newProxyInstance(fake.getClass().getClassLoader(), types, (proxy, method, args) -> {
            if (method.getDeclaringClass() != Object.class && !method.getName().equals("asBinder") && seen.add(method)) FakeLog.hit(name, iface + "." + method.getName());
            // WindowManagerService attaches a window context (an InputMethodService, a WindowProviderService) to its display area or window token and
            // answers its configuration: null means "not attached", and an IME's initializeInternal throws on it
            if (android.content.Context.WINDOW_SERVICE.equals(name) && method.getName().startsWith("attachWindowContextTo"))
                return new android.window.WindowContextInfo(new android.content.res.Configuration(org.robolectric.RuntimeEnvironment.getApplication().getResources().getConfiguration()),
                        android.view.Display.DEFAULT_DISPLAY);
            // an app's own link handling (Settings' "Open by default"): its web hosts, none verified
            if ("domain_verification".equals(name) && method.getName().equals("getDomainVerificationUserState")) return HostDomainVerification.userState((String) args[0], (Integer) args[1]);
            try {
                return method.invoke(fake, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}
