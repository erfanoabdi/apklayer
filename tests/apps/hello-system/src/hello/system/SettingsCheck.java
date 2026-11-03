package hello.system;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

/** Settings (the real SettingsProvider): the three tables read as an app reads them, a public System value written and read back (when the app may write settings), an unknown key refused, android_id stable. */
final class SettingsCheck implements Check {
    @Override public String run(Context context) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        String androidId = Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID);
        Check.require(androidId != null && androidId.length() >= 8, "android_id " + androidId);
        Check.require(androidId.equals(Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID)), "android_id changes");
        int airplane = Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0);
        Check.require(airplane == 0, "airplane_mode_on " + airplane);
        int timeout = Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 60000);
        Check.require(timeout > 0, "screen_off_timeout " + timeout);
        String wrote;
        if (Settings.System.canWrite(context)) {
            // as on a device: an app with WRITE_SETTINGS may change a public System setting, an unknown key is refused (targetSdk 23+)
            Check.require(Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, timeout + 1000), "putInt refused");
            Check.require(Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 0) == timeout + 1000, "written value not read back");
            Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, timeout);
            boolean refused;
            try { Settings.System.putString(resolver, "apklayer_check", "value-1"); refused = false; } catch (IllegalArgumentException expected) { refused = true; }
            Check.require(refused, "an unknown System key was accepted");
            wrote = "wrote screen_off_timeout, unknown key refused";
        } else wrote = "write not allowed (canWrite false)";
        return "android_id " + androidId + ", screen_off_timeout " + timeout + ", " + wrote;
    }
}
