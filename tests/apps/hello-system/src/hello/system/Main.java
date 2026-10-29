package hello.system;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The system apps and services an app depends on, each used from an app's point of view (-Dhello.sys=name, or "all"): contacts, calendar, downloads, settings,
 * media, documents, userdict, telephony, notifications, alarms, jobs. Each prints "[sys] PASS|FAIL name detail"; tests/vm/system-apps.sh runs them one by one.
 */
public final class Main extends Activity {
    private static final Map<String, Check> CHECKS = new LinkedHashMap<>();
    static {
        CHECKS.put("contacts", new ContactsCheck());
        CHECKS.put("calendar", new CalendarCheck());
        CHECKS.put("downloads", new DownloadsCheck());
        CHECKS.put("settings", new SettingsCheck());
        CHECKS.put("media", new MediaCheck());
        CHECKS.put("documents", new DocumentsCheck());
        CHECKS.put("userdict", new UserDictCheck());
        CHECKS.put("telephony", new TelephonyCheck());
        CHECKS.put("notifications", new NotificationsCheck());
        CHECKS.put("alarms", new AlarmsCheck());
        CHECKS.put("jobs", new JobsCheck());
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Context context = getApplicationContext();
        new Thread(() -> {
            String wanted = System.getProperty("hello.sys", "all");
            for (Map.Entry<String, Check> entry : CHECKS.entrySet()) {
                if (!wanted.equals("all") && !wanted.equals(entry.getKey())) continue;
                try { System.out.println("[sys] PASS " + entry.getKey() + " " + entry.getValue().run(context)); }
                catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[sys] FAIL " + entry.getKey() + " " + failure); }
            }
            System.out.println("[sys] DONE " + wanted);
        }, "system-check").start();
    }
}
