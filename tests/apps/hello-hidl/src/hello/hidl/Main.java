package hello.hidl;

import android.app.Activity;
import android.hidl.base.V1_0.IBase;
import android.os.Bundle;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Calls the instance's hwservicemanager through the framework's HwBinder (android.hidl.manager@1.0 IServiceManager, the framework's own Java classes,
 * by reflection: they are hidden API): getService, getTransport of its own entry (hwbinder = 1) and list(), which names that entry;
 * then registers a service of its own (IBase.Stub, a compile-only stand-in in compile-only/) and finds it again.
 */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String tag = "[hidl] " + getPackageName();
        try {
            Class<?> manager = Class.forName("android.hidl.manager.V1_0.IServiceManager");
            try {  // the exception IServiceManager.getService() swallows (NoSuchElementException: not found)
                Object raw = Class.forName("android.os.HwBinder").getMethod("getService", String.class, String.class).invoke(null, "android.hidl.manager@1.0::IServiceManager", "default");
                System.out.println(tag + " raw=" + raw);
            } catch (java.lang.reflect.InvocationTargetException e) { System.out.println(tag + " raw failed: " + e.getCause()); }
            Object sm = manager.getMethod("getService").invoke(null);
            if (sm == null) throw new IllegalStateException("IServiceManager.getService() is null");
            String self = "android.hidl.manager@1.0::IServiceManager";
            Method getTransport = manager.getMethod("getTransport", String.class, String.class);
            byte transport = (Byte) getTransport.invoke(sm, self, "default");
            @SuppressWarnings("unchecked") List<String> names = (List<String>) manager.getMethod("list").invoke(sm);
            boolean listed = names.contains(self + "/default");
            System.out.println(tag + " transport=" + transport + " listed=" + listed + " names=" + names);
            System.out.println(tag + (transport == 1 && listed ? " PASS" : " FAIL"));
            // the app's own HIDL service: it has no VINTF entry, the instance's manager takes it (docs/HIDL.md); IBase.Stub is the generated root stub.
            // Not fetched back here (the app's own process: the proxy's check calls into itself); the manager's list shows it.
            new IBase.Stub() {}.registerAsService("local-java");
            @SuppressWarnings("unchecked") List<String> after = (List<String>) manager.getMethod("list").invoke(sm);
            boolean own = after.contains("android.hidl.base@1.0::IBase/local-java");
            System.out.println(tag + " own service listed=" + own + (own ? " own PASS" : " own FAIL"));
        } catch (Throwable failure) {
            System.out.println(tag + " FAIL " + failure);
            failure.printStackTrace(System.out);
        }
        finish();
    }
}
