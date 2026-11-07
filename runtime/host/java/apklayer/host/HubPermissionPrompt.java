package apklayer.host;

import android.content.pm.PackageParser;
import android.content.pm.PermissionInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The system's permission screen (PermissionController's GrantPermissionsActivity and RequestRoleActivity) as the hub's dialog (HubInstallDialog):
 * one question per permission group, "Allow" / "Don't allow", the answer kept in HostRuntimePermissions (the manager shows it). Granted,
 * revoked in the manager or refused twice: answered without a question, as Android's "don't ask again".
 */
final class HubPermissionPrompt {
    private final HubPackages packages;
    private final ExecutorService dialogs = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "hub-permission-dialogs"); t.setDaemon(true); return t; });  // one question at a time

    HubPermissionPrompt(HubPackages packages) { this.packages = packages; }

    /**
     * The groups of the platform's runtime permissions (PermissionController's PermissionMapping.PLATFORM_PERMISSIONS) and what each request
     * asks (its permgrouprequest_* strings, after "Allow <app> to ").
     */
    private static final String[][] GROUPS = {
        {"access your contacts", "READ_CONTACTS WRITE_CONTACTS GET_ACCOUNTS"},
        {"access your calendar", "READ_CALENDAR WRITE_CALENDAR"},
        {"send and view SMS messages", "SEND_SMS RECEIVE_SMS READ_SMS RECEIVE_MMS RECEIVE_WAP_PUSH READ_CELL_BROADCASTS"},
        {"access photos, media, and files on your device", "READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE"},
        {"access music and audio on this device", "READ_MEDIA_AUDIO"},
        {"access photos and videos on this device", "READ_MEDIA_IMAGES READ_MEDIA_VIDEO ACCESS_MEDIA_LOCATION READ_MEDIA_VISUAL_USER_SELECTED"},
        {"access this device’s location", "ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION"},
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
    int[] request(String pkg, String[] names) {
        PackageParser.Package app = packages.get(pkg);
        int[] out = new int[names.length];
        Map<String, Boolean> answered = new HashMap<>();  // group -> this request's answer: one question per group
        String label = HubInstaller.labelOf(pkg, pkg);
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
            Boolean yes = answered.get(group);
            if (yes == null && app.requestedPermissions.stream().anyMatch(other -> group.equals(group(other)) && packages.holds(pkg, other))) yes = true;  // the group is allowed already
            if (yes == null) {
                String question = "Allow " + label + " to " + group + "?";
                try { yes = dialogs.submit(() -> HubInstallDialog.ask(question, "You can change this later in the apklayer manager.", "Allow", "Don't allow")).get(); }
                catch (Exception interrupted) { yes = false; }
            }
            answered.put(group, yes);
            out[i] = HostRuntimePermissions.answer(pkg, permission, yes);
            System.out.println("[apklayer-hub] permission " + permission + " of " + pkg + (yes ? ": allowed" : out[i] == HostRuntimePermissions.DENIED ? ": denied, not asked again" : ": denied"));
        }
        return out;
    }

    /** RoleManager.createRequestRoleIntent: whether the person makes the app the role's holder ("Set <app> as your default browser app?"). */
    boolean role(String pkg, String role) {
        String name = role.substring(role.lastIndexOf('.') + 1).replace('_', ' ').toLowerCase();
        String question = "Set " + HubInstaller.labelOf(pkg, pkg) + " as your default " + name + " app?";
        try { return dialogs.submit(() -> HubInstallDialog.ask(question, "", "Set as default", "Cancel")).get(); }
        catch (Exception interrupted) { return false; }
    }
}
