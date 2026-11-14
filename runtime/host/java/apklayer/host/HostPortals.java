package apklayer.host;

import android.content.Intent;
import android.net.Uri;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.role.RoleManager;
import android.content.pm.PackageManager;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowNotificationManager;
import java.util.Set;

/** Intents the app cannot handle itself go to the desktop (PLAN archive P5.3, OpenURI): links open in the host browser. */
final class HostPortals {
    private HostPortals() {}

    /** In the sandbox (cli/sandbox.cpp) the app reaches neither the session bus nor the user's files: dialogs and links go through apklayerd ({@code apklayer portal}). */
    private static final boolean SANDBOXED = "1".equals(System.getenv("APKLAYER_SANDBOX"));

    private static final Set<String> SCHEMES = Set.of("http", "https", "mailto", "tel", "geo");

    /** Opens an ACTION_VIEW of a web-like URI with the desktop's default handler; false if it is not one. */
    static boolean open(Intent intent) {
        Uri data = intent.getData();
        if (!Intent.ACTION_VIEW.equals(intent.getAction()) || data == null || !SCHEMES.contains(data.getScheme())) return false;
        try {
            new ProcessBuilder(SANDBOXED ? List.of(System.getProperty("apklayer.cli"), "portal", "open", data.toString()) : List.of("xdg-open", data.toString())).inheritIO().start();
            System.out.println("[apklayer] opened " + data + " on the desktop");
            return true;
        } catch (IOException e) {
            System.out.println("[apklayer] cannot open " + data + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Runtime permission requests (Activity.requestPermissions, the ActivityResult API's RequestPermission contracts): the hub asks the person
     * (HubPermissionPrompt) on a thread of their own, the app's looper keeps running; the answers come back as the PermissionController's result
     * on the main thread (Activity.dispatchRequestPermissionsResult: onRequestPermissionsResult, the next request allowed). Without a hub: granted
     * unless the person said no (HostRuntimePermissions).
     */
    static boolean requestPermissions(Intent intent, int requestCode, android.app.Activity requester) {
        if (!PackageManager.ACTION_REQUEST_PERMISSIONS.equals(intent.getAction())) return false;
        String[] asked = intent.getStringArrayExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES);
        String[] names = asked == null ? new String[0] : asked;
        String pkg = RuntimeEnvironment.getApplication().getPackageName();
        Thread asking = new Thread(() -> {
            int[] states = new int[names.length];
            if (HubClient.connected()) {
                try { states = HubClient.current().requestPermissions(names); }
                catch (RuntimeException noHub) { java.util.Arrays.fill(states, HostRuntimePermissions.DENIED); System.out.println("[apklayer] permission request failed: " + noHub); }
            }
            else for (int i = 0; i < names.length; i++) states[i] = HostRuntimePermissions.revoked(pkg, names[i]) ? HostRuntimePermissions.DENIED : HostRuntimePermissions.GRANTED;
            int[] answers = states;
            requester.runOnUiThread(() -> {
                ShadowApplication app = Shadow.extract(RuntimeEnvironment.getApplication());
                int[] results = new int[names.length];
                List<String> allowed = new java.util.ArrayList<>(), denied = new java.util.ArrayList<>();
                for (int i = 0; i < names.length; i++) {
                    boolean yes = answers[i] == HostRuntimePermissions.GRANTED;
                    results[i] = yes ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
                    (yes ? allowed : denied).add(names[i]);
                    if (yes) app.grantPermissions(names[i]); else app.denyPermissions(names[i]);
                    Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).setShouldShowRequestPermissionRationale(names[i], answers[i] == HostRuntimePermissions.DENIED_ONCE);
                }
                System.out.println("[apklayer] permissions granted: " + String.join(", ", allowed) + (denied.isEmpty() ? "" : "; denied: " + String.join(", ", denied)));
                if (requestCode >= 0 && !requester.isDestroyed())
                    ((ShadowActivity) Shadow.extract(requester)).receiveResult(intent, android.app.Activity.RESULT_OK, new Intent(PackageManager.ACTION_REQUEST_PERMISSIONS)
                            .putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES, names).putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS, results));
            });
        }, "permission-request");
        asking.setDaemon(true);
        asking.start();
        return true;
    }

    /** KeyguardManager.createConfirmDeviceCredentialIntent (Settings' ConfirmDeviceCredentialActivity): the desktop's login password in the hub's
     *  dialog (HubLockScreen.confirm); RESULT_OK once it verified the credential, so keys with an authentication timeout work. */
    static boolean confirmCredential(Intent intent, android.app.Activity requester, boolean wantsResult) {
        if (!android.app.KeyguardManager.ACTION_CONFIRM_DEVICE_CREDENTIAL.equals(intent.getAction())
                && !android.app.KeyguardManager.ACTION_CONFIRM_DEVICE_CREDENTIAL_WITH_USER.equals(intent.getAction())) return false;
        CharSequence title = intent.getCharSequenceExtra(android.app.KeyguardManager.EXTRA_TITLE), description = intent.getCharSequenceExtra(android.app.KeyguardManager.EXTRA_DESCRIPTION);
        Thread asking = new Thread(() -> {
            boolean confirmed;
            try { confirmed = HubClient.connected() && HubClient.current().confirmCredential(title == null ? null : title.toString(), description == null ? null : description.toString(), 0); }
            catch (RuntimeException noHub) { confirmed = false; }
            System.out.println("[apklayer] device credential " + (confirmed ? "confirmed" : "not confirmed"));
            boolean ok = confirmed;
            requester.runOnUiThread(() -> {
                if (wantsResult && !requester.isDestroyed())
                    ((ShadowActivity) Shadow.extract(requester)).receiveResult(intent, ok ? android.app.Activity.RESULT_OK : android.app.Activity.RESULT_CANCELED, null);
            });
        }, "confirm-credential");
        asking.setDaemon(true);
        asking.start();
        return true;
    }

    /** Roles a desktop without telephony cannot give (RoleController's availability): their request is refused at once, as RequestRoleActivity does. */
    private static final Set<String> PHONE_ROLES = Set.of(RoleManager.ROLE_SMS, RoleManager.ROLE_DIALER, RoleManager.ROLE_CALL_SCREENING, RoleManager.ROLE_CALL_REDIRECTION, RoleManager.ROLE_EMERGENCY);

    /** RoleManager.createRequestRoleIntent: the person decides in the hub's dialog (HubPermissionPrompt.role); RESULT_OK when the app holds the role then. */
    static boolean requestRole(Intent intent, android.app.Activity requester, boolean wantsResult) {
        if (!RoleManager.ACTION_REQUEST_ROLE.equals(intent.getAction())) return false;
        String role = intent.getStringExtra(Intent.EXTRA_ROLE_NAME);
        android.app.Application application = RuntimeEnvironment.getApplication();
        boolean noPhone = !application.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        Thread asking = new Thread(() -> {
            boolean held;
            try { held = role != null && !(noPhone && PHONE_ROLES.contains(role)) && HubClient.connected() && HubClient.current().requestRole(role); }
            catch (RuntimeException noHub) { held = false; }
            System.out.println("[apklayer] role " + role + (held ? " held" : " not given"));
            boolean given = held;
            requester.runOnUiThread(() -> {
                // the hub recorded it (HostRoles.record): the app's next start holds it again
                if (given) Shadows.shadowOf(application.getSystemService(RoleManager.class)).addHeldRole(role);
                if (wantsResult && !requester.isDestroyed())
                    ((ShadowActivity) Shadow.extract(requester)).receiveResult(intent, given ? android.app.Activity.RESULT_OK : android.app.Activity.RESULT_CANCELED, null);
            });
        }, "role-request");
        asking.setDaemon(true);
        asking.start();
        return true;
    }

    private static final Set<String> PICKERS = Set.of(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT,
            Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT_TREE);

    /**
     * Intents the host answers itself when no installed app does (ActivityTask.launch's fallbacks: desktop links and file dialogs, permission and role requests, the
     * installer's intents): startActivity does not throw ActivityNotFoundException for them.
     */
    static boolean hostAnswers(Intent intent) {
        String action = intent.getAction();
        return action == null || Intent.ACTION_VIEW.equals(action) || Intent.ACTION_CHOOSER.equals(action) || isPicker(intent)
                || Set.of(PackageManager.ACTION_REQUEST_PERMISSIONS, RoleManager.ACTION_REQUEST_ROLE, Intent.ACTION_INSTALL_PACKAGE, Intent.ACTION_DELETE, Intent.ACTION_UNINSTALL_PACKAGE,
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.app.KeyguardManager.ACTION_CONFIRM_DEVICE_CREDENTIAL,
                        android.app.KeyguardManager.ACTION_CONFIRM_DEVICE_CREDENTIAL_WITH_USER).contains(action);
    }

    static boolean isPicker(Intent intent) { return intent.getAction() != null && PICKERS.contains(intent.getAction()); }

    /**
     * The Storage Access Framework pickers become a desktop file dialog (the file chooser portal, zenity without one). Returns the result intent (file: URIs) or null if cancelled
     * or not a picker.
     */
    static Intent pick(Intent intent) {
        if (!isPicker(intent)) return null;
        String action = intent.getAction();
        if (System.getenv("APKLAYER_PICKER") == null && !SANDBOXED) {  // the desktop's file chooser portal first; zenity when there is no portal
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
        if (SANDBOXED) {  // the user's side picks and makes the chosen file (only it) visible here, at its own path
            cmd = new java.util.ArrayList<>(java.util.List.of(System.getProperty("apklayer.cli"), "portal", "pick",
                    Intent.ACTION_CREATE_DOCUMENT.equals(action) ? "save" : Intent.ACTION_OPEN_DOCUMENT_TREE.equals(action) ? "directory" : "open"));
            if (intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)) cmd.add("--multiple");
            String name = intent.getStringExtra(Intent.EXTRA_TITLE);
            if (Intent.ACTION_CREATE_DOCUMENT.equals(action) && name != null) { cmd.add("--name"); cmd.add(name); }
        }
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
