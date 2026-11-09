package apklayer.host;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.ResultReceiver;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * startActivity across apps and processes, hub side: the intent is resolved against the installed manifests, then handed to the window process of the
 * target activity (its package's, or the one of its android:process), started when it is not running, with the intent waiting for it. The caller's
 * ResultReceiver travels with it, so the target answers the caller directly when the activity finishes; a target process that dies first answers
 * RESULT_CANCELED from here, as ActivityManagerService finishes a dead process's activities.
 */
final class HubActivities {
    record Launch(Intent intent, ResultReceiver resultTo) {}

    private final HubDirectory directory;
    private final HubPackages packages;
    private final Map<String, Deque<Launch>> pending = new HashMap<>();
    /** Receivers handed to a running process (by pid): answered RESULT_CANCELED when it dies (the caller takes only the first answer). */
    // shortcut: kept until the process ends, answered or not; upgrade to dropping answered ones if a long-lived process collects many
    private final Map<Integer, List<ResultReceiver>> delivered = new HashMap<>();

    private final HubProviders providers;

    HubActivities(HubDirectory directory, HubPackages packages, HubProviders providers) { this.directory = directory; this.packages = packages; this.providers = providers; }

    /** Whether start would find an activity for the intent (the caller's startActivity throws ActivityNotFoundException otherwise). */
    boolean resolvable(HubDirectory.Peer caller, Intent intent) {
        return intent.hasExtra(HubInstaller.CONFIRM) || packages.activity(intent, caller.name()) != null;
    }

    /** False when no activity can handle the intent. */
    boolean start(HubDirectory.Peer caller, Intent intent, ResultReceiver resultTo) {
        if (directory.installer().confirm(caller, intent)) return true;  // a confirmation the installer starts (or sends as its PendingIntent) is the hub's own
        ActivityInfo info = packages.activity(intent, caller.name());
        if (info == null) return false;
        providers.grantFromIntent(caller, intent, info.packageName);
        Intent resolved = new Intent(intent).setComponent(new ComponentName(info.packageName, info.name));
        String key = HubDirectory.key(info.packageName, HostProcessName.of(info));  // android:process: a window process of its own
        HubDirectory.Peer target = directory.windowPeer(key);
        if (target != null) {
            try { deliver(target, resolved, resultTo); }
            catch (IllegalStateException exiting) { target = null; }  // its last activity finished and it is exiting: a new process takes the launch
        }
        if (target == null) {
            synchronized (pending) { pending.computeIfAbsent(key, k -> new ArrayDeque<>()).add(new Launch(resolved, resultTo)); }
            directory.launchApp(key);
        }
        System.out.println("[apklayer-hub] " + caller.name() + " starts " + info.packageName + "/" + info.name + (key.equals(info.packageName) ? "" : " in " + info.processName));
        return true;
    }

    /**
     * `apklayer run` of a package whose app process runs (Android: a launcher or `am start` reaches the running process): the intent from the file, or a
     * launcher start, goes to that process, which applies its task rules (ActivityTask.launch). The CLI asks only while the app's window JVM runs: one that
     * has not registered yet is still starting and takes it as its first launch (TAKE_LAUNCH). False when the intent file cannot be read.
     */
    boolean relaunch(String name, java.nio.file.Path intentFile) {
        Intent intent = intentFile != null ? HostApps.read(intentFile, name)
                : new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (intent == null) return false;
        HubDirectory.Peer target = directory.windowPeer(name);
        if (target != null) deliver(target, intent, null);
        else if (intentFile != null) synchronized (pending) { pending.computeIfAbsent(name, key -> new ArrayDeque<>()).add(new Launch(intent, null)); }
        // (a launcher start of an app that is starting is that start: nothing to queue)
        System.out.println("[apklayer-hub] relaunch " + name + (target == null ? " (still starting: queued)" : "") + ": " + intent);
        return true;
    }

    private void deliver(HubDirectory.Peer target, Intent intent, ResultReceiver resultTo) {
        HubProtocol.call(target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_START_ACTIVITY, request -> { request.writeTypedObject(intent, 0); request.writeTypedObject(resultTo, 0); }, reply -> null);
        if (resultTo != null) synchronized (delivered) { delivered.computeIfAbsent(target.pid(), pid -> new ArrayList<>()).add(resultTo); }
    }

    /** Whether launches wait for this window process (a process that was exiting when they came is replaced once it is gone). */
    boolean waiting(String key) { synchronized (pending) { Deque<Launch> queue = pending.get(key); return queue != null && !queue.isEmpty() && !restarts.contains(key); } }

    /** Window processes ended by Process.killProcess(myPid) with an activity below the top one: that activity waits in pending for a new process. */
    private final java.util.Set<String> restarts = new java.util.HashSet<>();

    void restartOnDeath(HubDirectory.Peer dying, Intent intent) {
        String key = HubDirectory.key(dying);
        synchronized (pending) { pending.computeIfAbsent(key, k -> new ArrayDeque<>()).addFirst(new Launch(intent, null)); restarts.add(key); }
        System.out.println("[apklayer-hub] " + key + " ends: " + intent.getComponent().getClassName() + " comes back once no window of " + dying.name() + " is up");
    }

    /**
     * As ActivityManagerService when a process dies under its top activity: the next activity of the task is started again in a new process. Here a task
     * spans the app's window processes only loosely, so it waits until none of them shows a window (FCL's crash screen in ":crash" stays on top of the
     * launcher until it is closed). shortcut: no saved instance state, only the activity right below the top; upgrade with a hub-side task record.
     */
    void restartIfAlone(String name) {
        List<String> due;
        synchronized (pending) { due = restarts.stream().filter(key -> key.equals(name) || key.startsWith(name + "#")).toList(); }
        if (due.isEmpty() || directory.windowsUp(name)) return;
        for (String key : due) {
            synchronized (pending) { if (!restarts.remove(key)) continue; }
            System.out.println("[apklayer-hub] restarting " + key + " for its activity below the killed one");
            directory.launchApp(key);
        }
    }

    /** The launch waiting for a freshly started app process, if any; later ones that came while it started go to it as to a running app. */
    Launch take(HubDirectory.Peer app) {
        Deque<Launch> queue;
        synchronized (pending) { queue = pending.remove(HubDirectory.key(app)); restarts.remove(HubDirectory.key(app)); }
        if (queue == null) return null;
        Launch first = queue.poll();
        if (first != null && first.resultTo() != null) synchronized (delivered) { delivered.computeIfAbsent(app.pid(), pid -> new ArrayList<>()).add(first.resultTo()); }
        for (Launch later : queue) deliver(app, later.intent(), later.resultTo());  // the app keeps them until its first activity is up (HubActivityClient.setStarter)
        return first;
    }

    /** A window process died (Process.killProcess, a crash): the activities it showed are gone; whoever started them gets RESULT_CANCELED. */
    void died(HubDirectory.Peer peer) {
        List<ResultReceiver> left;
        synchronized (delivered) { left = delivered.remove(peer.pid()); }
        if (left != null) for (ResultReceiver receiver : left) HubActivityClient.answer(receiver, android.app.Activity.RESULT_CANCELED, null);
    }
}
