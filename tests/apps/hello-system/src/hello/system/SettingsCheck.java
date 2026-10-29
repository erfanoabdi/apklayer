package hello.system;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

/** Settings: the three tables read as an app reads them, a value written to System read back (when the app may write settings), android_id stable across reads. */
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
            Check.require(Settings.System.putString(resolver, "apklayer_check", "value-1"), "putString refused");
            Check.require("value-1".equals(Settings.System.getString(resolver, "apklayer_check")), "written value not read back");
            wrote = "wrote";
        } else wrote = "write not allowed (canWrite false)";
        return "android_id " + androidId + ", screen_off_timeout " + timeout + ", " + wrote;
    }
}
