package apklayer.host;

import android.app.AlarmManager;
import android.content.Context;
import android.content.pm.PackageManager;
import java.util.Arrays;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowAlarmManager;

/** canScheduleExactAlarms by AlarmManagerService's rule instead of Robolectric's constant false: allowed below target 31 and for an app whose
 *  manifest asks for USE_EXACT_ALARM or SCHEDULE_EXACT_ALARM (the latter's app op as Android 12 and 13 default it: allowed; there is no settings
 *  screen here to grant it). Etar opened android.settings.REQUEST_SCHEDULE_EXACT_ALARM on every resume and died without a handler. */
@Implements(value = AlarmManager.class, isInAndroidSdk = false)
public class ShadowHostAlarmManager extends ShadowAlarmManager {
    @Override @Implementation protected boolean canScheduleExactAlarms() {
        if (super.canScheduleExactAlarms()) return true;
        Context app = RuntimeEnvironment.getApplication();
        if (app.getApplicationInfo().targetSdkVersion < 31) return true;
        try {
            String[] asked = app.getPackageManager().getPackageInfo(app.getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
            return asked != null && (Arrays.asList(asked).contains("android.permission.USE_EXACT_ALARM")
                    || Arrays.asList(asked).contains("android.permission.SCHEDULE_EXACT_ALARM"));
        } catch (PackageManager.NameNotFoundException unknown) {
            return false;
        }
    }
}
