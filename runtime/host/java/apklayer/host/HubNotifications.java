package apklayer.host;

import android.app.Notification;
import android.app.PendingIntent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Notifications of every app, owned by the hub (what NotificationManagerService is): per package, tag and id; shown as desktop notifications unless they are ongoing or of
 * low importance; clicks on the popup or its actions send the notification's PendingIntents (as their creator, through HubPendingIntents), a dismissed popup its delete intent.
 */
final class HubNotifications {
    private static final int IMPORTANCE_LOW = 2, IMPORTANCE_HIGH = 4, DISMISSED_BY_USER = 2;
    private record Key(String pkg, String tag, int id) {}
    private static final class Posted { Notification notification; int desktopId; long clickedAt; }

    private final HubPackages packages;
    private final HubPendingIntents pendingIntents;
    private final DesktopNotifications desktop = new DesktopNotifications(new DesktopNotifications.Listener() {
        @Override public void action(int id, String key) { clicked(id, key); }
        @Override public void closed(int id, int reason) { dismissed(id, reason); }
    });
    private final Map<Key, Posted> posted = new HashMap<>();

    HubNotifications(HubPackages packages, HubPendingIntents pendingIntents) { this.packages = packages; this.pendingIntents = pendingIntents; }

    void post(HubDirectory.Peer caller, String pkg, String tag, int id, Notification notification, int importance) {
        if (!caller.name().equals(pkg)) throw new SecurityException(caller.name() + " cannot post notifications for " + pkg);
        if (muted(pkg)) { System.out.println("[apklayer-hub] notification " + pkg + " " + tag + "/" + id + " dropped: notifications of " + pkg + " are off"); return; }
        Key key = new Key(pkg, tag, id);
        Posted record;
        synchronized (posted) { record = posted.computeIfAbsent(key, k -> new Posted()); record.notification = notification; }
        boolean ongoing = (notification.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0;
        boolean quiet = ongoing || importance <= IMPORTANCE_LOW || (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        String shell = DesktopNotifications.shell();
        System.out.println("[apklayer-hub] notification " + pkg + " " + tag + "/" + id + ": " + notification.extras.getCharSequence(Notification.EXTRA_TITLE) + (quiet ? " (quiet)" : ""));
        // Sailfish lists a quiet one in its events view without a banner; the other shells have no list, so a quiet one is not shown
        if (quiet && !shell.equals("lipstick")) return;
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE), text = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (text == null) text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        String summary = title == null ? pkg : title.toString(), body = text == null ? "" : text.toString();
        List<String> actions = new ArrayList<>();
        if (notification.contentIntent != null) { actions.add("default"); actions.add("Open"); }
        if (notification.actions != null) for (int i = 0; i < notification.actions.length; i++) { actions.add("action" + i); actions.add(String.valueOf(notification.actions[i].title)); }
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("urgency", (byte) (importance >= IMPORTANCE_HIGH ? 2 : importance <= IMPORTANCE_LOW ? 0 : 1));
        boolean again = record.desktopId != 0 && (notification.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0;  // an update of a progress bar does not ring or pop up again
        if (again || quiet || silentChannel(pkg, notification.getChannelId())) hints.put("suppress-sound", true);
        int max = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX), progress = notification.extras.getInt(Notification.EXTRA_PROGRESS);
        if (max > 0 && !notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)) {
            hints.put("value", progress * 100 / max);  // Lomiri, GNOME
            hints.put("progress", progress / (double) max);  // Lipstick
        }
        if (shell.equals("lipstick")) {
            if (!quiet && !again) { hints.put("x-nemo-preview-summary", summary); hints.put("x-nemo-preview-body", body); }  // the banner
            if (ongoing) hints.put("resident", true);
        }
        String icon = largeIcon(notification, pkg, id);
        if (!icon.isEmpty()) hints.put("image-path", icon);
        int popup = desktop.show(pkg, record.desktopId, icon, summary, body, actions, hints);
        synchronized (posted) { record.desktopId = popup; }
        System.out.println("[apklayer-hub] notification popup=" + popup + " " + shell);
    }

    private static boolean silentChannel(String pkg, String channelId) {
        if (channelId == null) return false;
        for (android.app.NotificationChannel channel : HubNotificationChannels.list(pkg)) if (channel.getId().equals(channelId)) return channel.getSound() == null;
        return false;
    }

    /** The large icon as a PNG file in the runtime dir (the shell shows it as the notification's picture); "" when there is none. */
    private static String largeIcon(Notification notification, String pkg, int id) {
        try {
            android.graphics.drawable.Icon icon = notification.getLargeIcon();
            String dir = System.getenv("XDG_RUNTIME_DIR");
            if (icon == null || dir == null || icon.getType() != android.graphics.drawable.Icon.TYPE_BITMAP) return "";
            java.io.File file = new java.io.File(dir, "apklayer-notification-" + pkg + "-" + id + ".png");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) { icon.getBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); }
            return file.length() > 0 ? file.getPath() : "";
        } catch (java.io.IOException | RuntimeException failure) { System.out.println("[apklayer-hub] notification icon: " + failure); return ""; }
    }

    /** The user turned the app's notifications off (NotificationManagerService.areNotificationsEnabledForPackage): its POST_NOTIFICATIONS revoked. */
    static boolean muted(String pkg) { return HostRuntimePermissions.revoked(pkg, android.Manifest.permission.POST_NOTIFICATIONS); }

    void cancel(HubDirectory.Peer caller, String tag, int id, boolean all) {
        List<Posted> gone = new ArrayList<>();
        synchronized (posted) {
            posted.entrySet().removeIf(entry -> {
                boolean match = entry.getKey().pkg().equals(caller.name()) && (all || (entry.getKey().id() == id && java.util.Objects.equals(entry.getKey().tag(), tag)));
                if (match) gone.add(entry.getValue());
                return match;
            });
        }
        for (Posted record : gone) if (record.desktopId != 0) { System.out.println("[apklayer-hub] notification cancelled, popup " + record.desktopId + " closed"); desktop.close(record.desktopId); }
    }

    private Posted byDesktop(int id, Key[] key) {
        synchronized (posted) {
            for (Map.Entry<Key, Posted> entry : posted.entrySet()) if (entry.getValue().desktopId == id) { key[0] = entry.getKey(); return entry.getValue(); }
        }
        return null;
    }

    private void fire(PendingIntent pending) {
        if (pending != null) pendingIntents.send(pending.getTarget().asBinder(), 0, null, null, null);
    }

    private void clicked(int id, String actionKey) {
        Key[] key = new Key[1];
        Posted record = byDesktop(id, key);
        if (record == null) return;
        Notification notification = record.notification;
        record.clickedAt = System.currentTimeMillis();  // the shell closes the popup after a tap (reason 2): that is not a dismissal
        System.out.println("[apklayer-hub] notification click " + key[0] + " " + actionKey);
        if (actionKey.equals("default")) fire(notification.contentIntent);
        else if (actionKey.startsWith("action") && notification.actions != null) {
            int index = Integer.parseInt(actionKey.substring(6));
            if (index < notification.actions.length) fire(notification.actions[index].actionIntent);
        }
        if ((notification.flags & Notification.FLAG_AUTO_CANCEL) != 0) {
            synchronized (posted) { posted.remove(key[0]); }
            desktop.close(id);
        }
    }

    private void dismissed(int id, int reason) {
        System.out.println("[apklayer-hub] popup " + id + " closed by the desktop, reason " + reason);
        Key[] key = new Key[1];
        Posted record = byDesktop(id, key);
        if (record == null || reason != DISMISSED_BY_USER || System.currentTimeMillis() - record.clickedAt < 3000) return;
        synchronized (posted) { posted.remove(key[0]); }
        System.out.println("[apklayer-hub] notification dismissed " + key[0]);
        fire(record.notification.deleteIntent);
    }
}
