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

    /** A tap starts Top over this activity, which stops it (tests/vm/settings.sh: a change while stopped relaunches it at its restart). */
    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        if (e.getAction() == android.view.MotionEvent.ACTION_UP) startActivity(new Intent(this, Top.class));
        return true;
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
                + " palette=" + secure("theme_customization_overlay_packages") + " contrast=" + modes.getContrast()
                + " accent1_600=" + Integer.toHexString(getColor(android.R.color.system_accent1_600));
    }

    /** A Settings.Secure value; hidden keys not annotated @Readable are refused to apps (S+). */
    private String secure(String key) {
        try { return Settings.Secure.getString(getContentResolver(), key); }
        catch (SecurityException refused) { return "unreadable"; }
    }
}
