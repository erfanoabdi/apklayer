package apklayer.host;

import android.app.Notification;
import android.app.PendingIntent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notifications of every app, owned by the hub (what NotificationManagerService is): per package, tag and id; shown as desktop notifications unless they are ongoing or of
 * low importance; clicks on the popup or its actions send the notification's PendingIntents (as their creator, through HubPendingIntents), a dismissed popup its delete intent.
 */
final class HubNotifications {
    private static final int IMPORTANCE_LOW = 2, DISMISSED_BY_USER = 2;
    private record Key(String pkg, String tag, int id) {}
    private static final class Posted { Notification notification; int desktopId; }

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
        Key key = new Key(pkg, tag, id);
        Posted record;
        synchronized (posted) { record = posted.computeIfAbsent(key, k -> new Posted()); record.notification = notification; }
        boolean quiet = (notification.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0 || importance <= IMPORTANCE_LOW;
        System.out.println("[apklayer-hub] notification " + pkg + " " + tag + "/" + id + ": " + notification.extras.getCharSequence(Notification.EXTRA_TITLE) + (quiet ? " (quiet)" : ""));
        if (quiet) return;
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE), text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        List<String> actions = new ArrayList<>();
        if (notification.contentIntent != null) { actions.add("default"); actions.add("Open"); }
        if (notification.actions != null) for (int i = 0; i < notification.actions.length; i++) { actions.add("action" + i); actions.add(String.valueOf(notification.actions[i].title)); }
        int popup = desktop.show(pkg, record.desktopId, title == null ? pkg : title.toString(), text == null ? "" : text.toString(), actions);
        synchronized (posted) { record.desktopId = popup; }
        System.out.println("[apklayer-hub] notification popup=" + popup);
    }

    void cancel(HubDirectory.Peer caller, String tag, int id, boolean all) {
        List<Posted> gone = new ArrayList<>();
        synchronized (posted) {
            posted.entrySet().removeIf(entry -> {
                boolean match = entry.getKey().pkg().equals(caller.name()) && (all || (entry.getKey().id() == id && java.util.Objects.equals(entry.getKey().tag(), tag)));
                if (match) gone.add(entry.getValue());
                return match;
            });
        }
        for (Posted record : gone) if (record.desktopId != 0) desktop.close(record.desktopId);
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
        Key[] key = new Key[1];
        Posted record = byDesktop(id, key);
        if (record == null || reason != DISMISSED_BY_USER) return;
        synchronized (posted) { posted.remove(key[0]); }
        System.out.println("[apklayer-hub] notification dismissed " + key[0]);
        fire(record.notification.deleteIntent);
    }
}
