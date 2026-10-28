package apklayer.host;

import android.content.Context;
import android.content.pm.PackageManager;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowSettings;

/** "Draw over other apps" follows the app's SYSTEM_ALERT_WINDOW grant (given at install on the desktop), not Robolectric's constant false. */
@Implements(value = android.provider.Settings.class, isInAndroidSdk = false)
public class ShadowHostSettings extends ShadowSettings {
    @Implementation
    protected static boolean canDrawOverlays(Context context) {
        return context.checkSelfPermission("android.permission.SYSTEM_ALERT_WINDOW") == PackageManager.PERMISSION_GRANTED;
    }
}
