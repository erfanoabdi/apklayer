package hello.settings;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.widget.TextView;
import java.util.Locale;
import java.util.TimeZone;

/** The host settings as an app sees them: one "[set]" line per configuration it is created or changed with (tests/vm/settings.sh compares them). */
public final class Main extends Activity {
    private static BroadcastReceiver receiver;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setTextSize(24);
        text.setText(describe(getResources().getConfiguration()));
        setContentView(text);
        System.out.println("[set] create " + describe(getResources().getConfiguration()));
        if (receiver == null) {  // once per process: the activity is recreated on a change
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    System.out.println("[set] broadcast " + intent.getAction() + " tz=" + intent.getStringExtra(Intent.EXTRA_TIMEZONE)
                            + " 24h=" + intent.getIntExtra("android.intent.extra.TIME_PREF_24_HOUR_FORMAT", -1));
                }
            };
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            getApplicationContext().registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        }
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        System.out.println("[set] changed " + describe(config));
    }

    private String describe(Configuration c) {
        boolean night = (c.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        UiModeManager modes = getSystemService(UiModeManager.class);
        return "night=" + night + " nightMode=" + modes.getNightMode() + " fontScale=" + c.fontScale + " locale=" + c.getLocales().get(0).toLanguageTag()
                + " default=" + Locale.getDefault().toLanguageTag() + " cancel=" + getString(android.R.string.cancel) + " tz=" + TimeZone.getDefault().getID()
                + " 24h=" + DateFormat.is24HourFormat(this) + " animator=" + ValueAnimator.getDurationScale()
                + " contrast=" + (android.os.Build.VERSION.SDK_INT >= 34 ? modes.getContrast() : 0f)
                + " palette=" + Settings.Secure.getString(getContentResolver(), "theme_customization_overlay_packages");
    }
}
