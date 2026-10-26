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

    private static final Set<String> PICKERS = Set.of(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT,
            Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT_TREE);

    static boolean isPicker(Intent intent) { return PICKERS.contains(intent.getAction()); }

    /**
     * The Storage Access Framework pickers become a desktop file dialog (zenity; the file chooser portal
     * is the later, sandbox-friendly form). Returns the result intent (file: URIs) or null if cancelled
     * or not a picker.
     */
    static Intent pick(Intent intent) {
        if (!isPicker(intent)) return null;
        String action = intent.getAction();
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of("zenity", "--file-selection", "--separator=\n"));
        if (Intent.ACTION_CREATE_DOCUMENT.equals(action)) {
            cmd.add("--save");
            String name = intent.getStringExtra(Intent.EXTRA_TITLE);
            if (name != null) cmd.add("--filename=" + name);
        }
        if (Intent.ACTION_OPEN_DOCUMENT_TREE.equals(action)) cmd.add("--directory");
        if (intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)) cmd.add("--multiple");
        String override = System.getenv("APKLAYER_PICKER"); // tests: a command printing the chosen path(s)
        if (override != null) cmd = java.util.List.of("sh", "-c", override);
        try {
            Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            if (p.waitFor() != 0 || out.isEmpty()) return null;
            String[] paths = out.split("\n");
            Intent result = new Intent().setData(Uri.fromFile(new java.io.File(paths[0])));
            if (paths.length > 1) {
                android.content.ClipData clip = new android.content.ClipData(null, new String[] {"*/*"},
                        new android.content.ClipData.Item(Uri.fromFile(new java.io.File(paths[0]))));
                for (int i = 1; i < paths.length; i++) clip.addItem(new android.content.ClipData.Item(Uri.fromFile(new java.io.File(paths[i]))));
                result.setClipData(clip);
            }
            System.out.println("[apklayer] picked " + out.replace('\n', ' '));
            return result;
        } catch (IOException | InterruptedException e) {
            System.out.println("[apklayer] file dialog failed: " + e.getMessage());
            return null;
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
