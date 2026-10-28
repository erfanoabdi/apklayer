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

    /**
     * Runtime permission requests (Activity.requestPermissions): the desktop has no permission prompts yet (a portal
     * dialog is the later form), so everything asked for is granted and the result delivered like the system would.
     */
    static boolean requestPermissions(Intent intent, int requestCode, android.app.Activity requester) {
        if (!"android.content.pm.action.REQUEST_PERMISSIONS".equals(intent.getAction())) return false;
        String[] names = intent.getStringArrayExtra("android.content.pm.extra.REQUEST_PERMISSIONS_NAMES");
        if (names == null) names = new String[0];
        ((org.robolectric.shadows.ShadowApplication) Shadow.extract(RuntimeEnvironment.getApplication())).grantPermissions(names);
        int[] granted = new int[names.length]; // PERMISSION_GRANTED == 0
        System.out.println("[apklayer] permissions granted: " + String.join(", ", names));
        if (requestCode >= 0) requester.onRequestPermissionsResult(requestCode, names, granted);
        return true;
    }

    private static final Set<String> PICKERS = Set.of(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT,
            Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT_TREE);

    static boolean isPicker(Intent intent) { return intent.getAction() != null && PICKERS.contains(intent.getAction()); }

    /**
     * The Storage Access Framework pickers become a desktop file dialog (the file chooser portal, zenity without one). Returns the result intent (file: URIs) or null if cancelled
     * or not a picker.
     */
    static Intent pick(Intent intent) {
        if (!isPicker(intent)) return null;
        String action = intent.getAction();
        if (System.getenv("APKLAYER_PICKER") == null) {  // the desktop's file chooser portal first; zenity when there is no portal
            try {
                Intent portal = HostFilePortal.pick(intent);
                return portal;
            } catch (IOException noPortal) { System.out.println("[apklayer] file chooser portal unavailable (" + noPortal.getMessage() + "): zenity"); }
        }
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
}
