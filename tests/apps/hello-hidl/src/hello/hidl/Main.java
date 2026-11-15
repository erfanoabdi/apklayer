package hello.hidl;

import android.app.Activity;
import android.os.Bundle;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Calls the instance's hwservicemanager through the framework's HwBinder (android.hidl.manager@1.0 IServiceManager, the framework's own Java classes,
 * by reflection: they are hidden API): getService, getTransport of its own entry (hwbinder = 1) and list(), which names that entry.
 */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String tag = "[hidl] " + getPackageName();
        try {
            Class<?> manager = Class.forName("android.hidl.manager.V1_0.IServiceManager");
            Object sm = manager.getMethod("getService").invoke(null);
            if (sm == null) throw new IllegalStateException("IServiceManager.getService() is null");
            String self = "android.hidl.manager@1.0::IServiceManager";
            Method getTransport = manager.getMethod("getTransport", String.class, String.class);
            byte transport = (Byte) getTransport.invoke(sm, self, "default");
            @SuppressWarnings("unchecked") List<String> names = (List<String>) manager.getMethod("list").invoke(sm);
            boolean listed = names.contains(self + "/default");
            System.out.println(tag + " transport=" + transport + " listed=" + listed + " names=" + names);
            System.out.println(tag + (transport == 1 && listed ? " PASS" : " FAIL"));
        } catch (Throwable failure) {
            System.out.println(tag + " FAIL " + failure);
            failure.printStackTrace(System.out);
        }
        finish();
    }
}
