package apklayer.host;

import android.content.pm.PackageParser;
import android.content.pm.PermissionInfo;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The system's permission screen (PermissionController's GrantPermissionsActivity and RequestRoleActivity) as the hub's dialog (HubInstallDialog):
 * one question per permission group, "Allow" / "Don't allow", the answer kept in HostRuntimePermissions (the manager shows it). Granted,
 * revoked in the manager or refused twice: answered without a question, as Android's "don't ask again". Location (Android 12's screen): "Precise" or
 * "Approximate" (an approximate answer grants ACCESS_COARSE_LOCATION only) and "While using the app" / "Only this time" (a grant that ends with the
 * process that asked) / "Don't allow"; the background location is its own question, asked only when the app holds the foreground location.
 */
final class HubPermissionPrompt {
    private final HubPackages packages;
    private final ExecutorService dialogs = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "hub-permission-dialogs"); t.setDaemon(true); return t; });  // one question at a time

    HubPermissionPrompt(HubPackages packages) { this.packages = packages; HostRuntimePermissions.endOnce(null, null); }  // a hub that died left "only this time" grants

    /**
     * The groups of the platform's runtime permissions (PermissionController's PermissionMapping.PLATFORM_PERMISSIONS) and what each request
     * asks (its permgrouprequest_* strings, after "Allow <app> to ").
     */
    private static final String LOCATION = "access this device’s location", BACKGROUND_LOCATION = "access this device’s location in the background",
            FINE = "android.permission.ACCESS_FINE_LOCATION", COARSE = "android.permission.ACCESS_COARSE_LOCATION";
    private static final String[][] GROUPS = {
        {"access your contacts", "READ_CONTACTS WRITE_CONTACTS GET_ACCOUNTS"},
        {"access your calendar", "READ_CALENDAR WRITE_CALENDAR"},
        {"send and view SMS messages", "SEND_SMS RECEIVE_SMS READ_SMS RECEIVE_MMS RECEIVE_WAP_PUSH READ_CELL_BROADCASTS"},
        {"access photos, media, and files on your device", "READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE"},
        {"access music and audio on this device", "READ_MEDIA_AUDIO"},
        {"access photos and videos on this device", "READ_MEDIA_IMAGES READ_MEDIA_VIDEO ACCESS_MEDIA_LOCATION READ_MEDIA_VISUAL_USER_SELECTED"},
        {LOCATION, "ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION"},
        {BACKGROUND_LOCATION, "ACCESS_BACKGROUND_LOCATION"},
        {"find, connect to, and determine the relative position of nearby devices", "BLUETOOTH_ADVERTISE BLUETOOTH_CONNECT BLUETOOTH_SCAN UWB_RANGING NEARBY_WIFI_DEVICES RANGING ACCESS_LOCAL_NETWORK"},
        {"access your phone call logs", "READ_CALL_LOG WRITE_CALL_LOG PROCESS_OUTGOING_CALLS"},
        {"make and manage phone calls", "READ_PHONE_STATE READ_PHONE_NUMBERS CALL_PHONE ADD_VOICEMAIL USE_SIP ANSWER_PHONE_CALLS ACCEPT_HANDOVER"},
        {"record audio", "RECORD_AUDIO RECORD_BACKGROUND_AUDIO"},
        {"access your physical activity", "ACTIVITY_RECOGNITION"},
        {"take pictures and record video", "CAMERA BACKGROUND_CAMERA"},
        {"send you notifications", "POST_NOTIFICATIONS"},
        {"access sensor data about your vital signs", "BODY_SENSORS BODY_SENSORS_BACKGROUND"},
    };
    private static final Map<String, String> GROUP_OF = new HashMap<>();
    static {
        for (String[] group : GROUPS) for (String name : group[1].split(" ")) GROUP_OF.put("android.permission." + name, group[0]);
    }

    /** The group's question text; a permission outside the platform's groups is its own group. */
    private static String group(String permission) {
        return GROUP_OF.getOrDefault(permission, "use " + permission.substring(permission.lastIndexOf('.') + 1).replace('_', ' ').toLowerCase());
    }

    /** Activity.requestPermissions of {@code pkg}: per name GRANTED, DENIED_ONCE (it may ask again) or DENIED (HostRuntimePermissions); waits for the person's answers. */
    int[] request(String pkg, int pid, String[] names) {
        PackageParser.Package app = packages.get(pkg);
        int[] out = new int[names.length];
        Map<String, Boolean> answered = new HashMap<>();  // group -> this request's answer: one question per group
        String label = HubInstaller.labelOf(pkg, pkg);
        boolean foreground = packages.holds(pkg, FINE) || packages.holds(pkg, COARSE);  // background location is asked only after this (Android 11+)
        int[] location = null;  // this request's answer to the location screen: {button, radio}
        for (int i = 0; i < names.length; i++) {
            String permission = names[i];
            if (packages.holds(pkg, permission)) { out[i] = HostRuntimePermissions.GRANTED; continue; }
            PermissionInfo definition = permission == null ? null : packages.definition(permission);
            boolean runtime = definition != null && (definition.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS;
            // not in the manifest, not a runtime permission, or refused for good: no question
            if (app == null || !runtime || !app.requestedPermissions.contains(permission) || HostRuntimePermissions.state(pkg, permission) == HostRuntimePermissions.DENIED) {
                out[i] = HostRuntimePermissions.DENIED;
                continue;
            }
            String group = group(permission);
            if (group.equals(BACKGROUND_LOCATION) && !foreground) { out[i] = HostRuntimePermissions.DENIED; continue; }  // ignored, as the system does
            boolean place = group.equals(LOCATION), fine = permission.equals(FINE), once = false, approximate = false;
            Boolean yes = place ? null : answered.get(group);
            if (place && location == null && !fine && packages.holds(pkg, FINE)) yes = true;  // precise includes approximate
            else if (place) {
                if (location == null) location = askLocation(label, Arrays.asList(names).contains(FINE));
                yes = location[0] != 2 && (!fine || location[1] == 0);
                once = location[0] == 1;
                approximate = location[0] != 2 && fine && !yes;
            }
            else if (yes == null && app.requestedPermissions.stream().anyMatch(other -> group.equals(group(other)) && packages.holds(pkg, other))) yes = true;  // the group is allowed already
            else if (yes == null) {
                String question = "Allow " + label + " to " + group + "?";
                String body = group.equals(BACKGROUND_LOCATION) ? label + " can use your location while you use it. Allow it also when you do not?" : "You can change this later in the apklayer manager.";
                boolean background = group.equals(BACKGROUND_LOCATION);
                try { yes = dialogs.submit(() -> HubInstallDialog.ask(question, body, background ? "Allow all the time" : "Allow", background ? "Only while using the app" : "Don't allow")).get(); }
                catch (Exception interrupted) { yes = false; }
            }
            answered.put(group, yes);
            if (once && yes) {
                HostRuntimePermissions.grantOnce(pkg, permission);
                out[i] = HostRuntimePermissions.GRANTED;
                String key = permission;
                ProcessHandle.of(pid).ifPresent(handle -> handle.onExit().thenRun(() -> HostRuntimePermissions.endOnce(pkg, key)));
            }
            else if (approximate) out[i] = HostRuntimePermissions.DENIED_ONCE;  // precise was not chosen: the app may ask again, no strike against it
            else out[i] = HostRuntimePermissions.answer(pkg, permission, yes);
            System.out.println("[apklayer-hub] permission " + permission + " of " + pkg + (yes ? ": allowed" : out[i] == HostRuntimePermissions.DENIED ? ": denied, not asked again" : ": denied"));
        }
        return out;
    }

    /** The location screen: {button, radio}: button 0 "While using the app", 1 "Only this time", 2 "Don't allow"; radio 0 "Precise", 1 "Approximate". */
    private int[] askLocation(String label, boolean preciseAsked) {
        int[] picked = {0};
        String[] radios = preciseAsked ? new String[] {"Precise", "Approximate"} : new String[] {"Approximate"};
        try {
            int button = dialogs.submit(() -> HubInstallDialog.choose("Allow " + label + " to " + LOCATION + "?", "", radios, picked,
                    new String[] {"While using the app", "Only this time", "Don't allow"}, new String[] {"yes", "once", "no"})).get();
            return new int[] {button, preciseAsked ? picked[0] : 1};
        }
        catch (Exception interrupted) { return new int[] {2, 0}; }
    }

    /**
     * Settings.ACTION_APP_NOTIFICATION_SETTINGS: the app's notification page (Settings' AppNotificationSettings, minimal): whether its notifications are on, its channels
     * with their importance, one button to switch them (the app's POST_NOTIFICATIONS, as the manager does) and one to close. Returns when it is closed.
     */
    void notificationSettings(String pkg) {
        String label = HubInstaller.labelOf(pkg, pkg), post = android.Manifest.permission.POST_NOTIFICATIONS;
        boolean off = HubNotifications.muted(pkg);
        String[] levels = {"none", "min", "low", "default", "high"};
        String channels = HubNotificationChannels.list(pkg).stream().map(c -> c.getName() + " (" + levels[Math.max(0, Math.min(c.getImportance(), 4))] + ")").collect(java.util.stream.Collectors.joining(", "));
        String body = "Notifications are " + (off ? "off" : "on") + "." + (channels.isEmpty() ? "" : "\nChannels: " + channels);  // one switch per visit: the app asks again to see the page
        try {
            if (!dialogs.submit(() -> HubInstallDialog.ask("Notifications of " + label, body, off ? "Turn on" : "Turn off", "Close")).get()) return;
        }
        catch (Exception interrupted) { return; }
        if (off) HostRuntimePermissions.answer(pkg, post, true);
        else { HostRuntimePermissions.answer(pkg, post, false); HostRuntimePermissions.answer(pkg, post, false); }  // twice: off for good, as the person's switch is
    }

    /** RoleManager.createRequestRoleIntent: whether the person makes the app the role's holder ("Set <app> as your default browser app?"). */
    boolean role(String pkg, String role) {
        String name = role.substring(role.lastIndexOf('.') + 1).replace('_', ' ').toLowerCase();
        String question = "Set " + HubInstaller.labelOf(pkg, pkg) + " as your default " + name + " app?";
        try {
            boolean yes = dialogs.submit(() -> HubInstallDialog.ask(question, "", "Set as default", "Cancel")).get();
            if (yes) HostRoles.record(pkg, role);
            return yes;
        }
        catch (Exception interrupted) { return false; }
    }
}
