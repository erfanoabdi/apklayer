package apklayer.host;

import android.os.ISystemConfig;
import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.Collections;

/**
 * The "system_config" service (SystemConfigManager): the lists system apps ask for (carrier apps, default VR components, trusted packages...). A device
 * without any of them answers with empty lists, which is what this does; the settings provider reads it while upgrading its settings.
 */
final class HostSystemConfigService {
    private HostSystemConfigService() {}

    static void install() {
        ISystemConfig empty = (ISystemConfig) Proxy.newProxyInstance(ISystemConfig.class.getClassLoader(), new Class<?>[] {ISystemConfig.class}, (proxy, method, args) -> {
            Class<?> type = method.getReturnType();
            if (method.getName().equals("asBinder")) return null;
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (java.util.List.class.isAssignableFrom(type)) return Collections.emptyList();
            if (java.util.Set.class.isAssignableFrom(type)) return Collections.emptySet();
            if (java.util.Map.class.isAssignableFrom(type)) return Collections.emptyMap();
            if (type.isArray()) return Array.newInstance(type.getComponentType(), 0);
            return null;
        });
        org.robolectric.shadows.ShadowServiceManager.addBinderService("system_config", ISystemConfig.class, empty);
        org.robolectric.shadows.ShadowServiceManager.setServiceAvailability("system_config", true);
        // services system apps look up and a device without the feature answers with "nothing": null proxies
        for (String[] service : new String[][] {{"textservices", "com.android.internal.textservice.ITextServicesManager"}}) {
            try {
                Class<? extends android.os.IInterface> type = Class.forName(service[1]).asSubclass(android.os.IInterface.class);
                org.robolectric.shadows.ShadowServiceManager.addBinderService(service[0], type, (android.os.IInterface) org.robolectric.util.ReflectionHelpers.createNullProxy(type));
                org.robolectric.shadows.ShadowServiceManager.setServiceAvailability(service[0], true);
            } catch (ClassNotFoundException | ClassCastException missing) { System.out.println("[apklayer] no service class " + service[1]); }
        }
    }
}
