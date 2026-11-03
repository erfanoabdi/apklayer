package apklayer.host;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.robolectric.Shadows;

/**
 * InputMethodManager's lists as InputMethodManagerService answers them: the installed input methods (services for android.view.InputMethod
 * behind BIND_INPUT_METHOD) and the ones Settings.Secure.ENABLED_INPUT_METHODS enables (the hub writes it at boot and answers it, HubBootSettings).
 * IME setup screens check their own entry in the enabled list.
 */
final class HostInputMethods {
    private HostInputMethods() {}

    /** {@code setting}: ENABLED_INPUT_METHODS as the system answers it (the hub: an app targeting 34+ may not read the setting). */
    static void install(Context app, String setting) {
        List<InputMethodInfo> all = new ArrayList<>(), enabled = new ArrayList<>();
        List<String> ids = setting == null || setting.isEmpty() ? List.of() : Arrays.stream(setting.split(":")).map(entry -> entry.split(";")[0]).toList();
        for (ResolveInfo service : app.getPackageManager().queryIntentServices(new Intent(HubBootSettings.INPUT_METHOD_ACTION), PackageManager.GET_META_DATA)) {
            if (!"android.permission.BIND_INPUT_METHOD".equals(service.serviceInfo.permission)) continue;
            InputMethodInfo info;
            try { info = new InputMethodInfo(app, service); }  // reads the IME's metadata (subtypes, settings activity)
            catch (Exception | LinkageError unreadable) {  // another app's resources: the plain entry
                info = new InputMethodInfo(service.serviceInfo.packageName, service.serviceInfo.name, service.loadLabel(app.getPackageManager()), null);
            }
            all.add(info);
            if (ids.contains(info.getId())) enabled.add(info);
        }
        System.out.println("[apklayer] input methods: " + all.size() + " installed, enabled [" + setting + "]");
        Shadows.shadowOf(app.getSystemService(InputMethodManager.class)).setInputMethodInfoList(all);
        Shadows.shadowOf(app.getSystemService(InputMethodManager.class)).setEnabledInputMethodInfoList(enabled);
    }
}
