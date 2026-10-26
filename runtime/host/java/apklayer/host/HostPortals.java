package apklayer.host;

import android.content.Intent;
import android.net.Uri;
import android.app.Notification;
import android.app.NotificationManager;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowNotificationManager;
import java.util.Set;

/** Intents the app cannot handle itself go to the desktop (PLAN P5.3, OpenURI): links open in the host browser. */
final class HostPortals {
    private HostPortals() {}

    private static final Set<String> SCHEMES = Set.of("http", "https", "mailto", "tel", "geo");

    /** Opens an ACTION_VIEW of a web-like URI with the desktop's default handler; false if it is not one. */
    static boolean open(Intent intent) {
        Uri data = intent.getData();
        if (!Intent.ACTION_VIEW.equals(intent.getAction()) || data == null || !SCHEMES.contains(data.getScheme())) return false;
        try {
            new ProcessBuilder("xdg-open", data.toString()).inheritIO().start();
            System.out.println("[apklayer] opened " + data + " on the desktop");
            return true;
        } catch (IOException e) {
            System.out.println("[apklayer] cannot open " + data + ": " + e.getMessage());
            return false;
        }
    }

    private static final Map<Notification, Boolean> shown = new IdentityHashMap<>();

    /** Shows each new notification the app posted (Robolectric records them) as a desktop notification. */
    static void followNotifications() {
        NotificationManager nm = RuntimeEnvironment.getApplication().getSystemService(NotificationManager.class);
        for (Notification n : ((ShadowNotificationManager) Shadow.extract(nm)).getAllNotifications()) {
            if (shown.put(n, true) != null) continue;
            CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE), text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            try {
                new ProcessBuilder("notify-send", "--app-name=" + RuntimeEnvironment.getApplication().getPackageName(),
                        String.valueOf(title), String.valueOf(text == null ? "" : text)).inheritIO().start();
                System.out.println("[apklayer] notification: " + title + " / " + text);
            } catch (IOException e) {
                System.out.println("[apklayer] cannot show notification: " + e.getMessage());
            }
        }
    }
}
