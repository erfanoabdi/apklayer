package apklayer.host;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.ResultReceiver;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * startActivity across apps, hub side: the intent is resolved against the installed manifests, then handed to the target package's app process (started when it is
 * not running, with the intent waiting for it). The caller's ResultReceiver travels with it, so the target answers the caller directly when the activity finishes.
 */
final class HubActivities {
    record Launch(Intent intent, ResultReceiver resultTo) {}

    private final HubDirectory directory;
    private final HubPackages packages;
    private final Map<String, Deque<Launch>> pending = new HashMap<>();

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
        HubDirectory.Peer target = directory.appPeer(info.packageName);
        if (target != null) {
            try { deliver(target, resolved, resultTo); }
            catch (IllegalStateException exiting) { target = null; }  // its last activity finished and it is exiting: a new process takes the launch
        }
        if (target == null) {
            synchronized (pending) { pending.computeIfAbsent(info.packageName, key -> new ArrayDeque<>()).add(new Launch(resolved, resultTo)); }
            directory.launchApp(info.packageName);
        }
        System.out.println("[apklayer-hub] " + caller.name() + " starts " + info.packageName + "/" + info.name);
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
        HubDirectory.Peer target = directory.appPeer(name);
        if (target != null) deliver(target, intent, null);
        else if (intentFile != null) synchronized (pending) { pending.computeIfAbsent(name, key -> new ArrayDeque<>()).add(new Launch(intent, null)); }
        // (a launcher start of an app that is starting is that start: nothing to queue)
        System.out.println("[apklayer-hub] relaunch " + name + ": " + intent);
        return true;
    }

    private static void deliver(HubDirectory.Peer target, Intent intent, ResultReceiver resultTo) {
        HubProtocol.call(target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_START_ACTIVITY, request -> { request.writeTypedObject(intent, 0); request.writeTypedObject(resultTo, 0); }, reply -> null);
    }

    /** Whether launches wait for the package's app process (a process that was exiting when they came is replaced once it is gone). */
    boolean waiting(String name) { synchronized (pending) { Deque<Launch> queue = pending.get(name); return queue != null && !queue.isEmpty(); } }

    /** The launch waiting for a freshly started app process, if any; later ones that came while it started go to it as to a running app. */
    Launch take(HubDirectory.Peer app) {
        Deque<Launch> queue;
        synchronized (pending) { queue = pending.remove(app.name()); }
        if (queue == null) return null;
        Launch first = queue.poll();
        for (Launch later : queue) deliver(app, later.intent(), later.resultTo());  // the app keeps them until its first activity is up (HubActivityClient.setStarter)
        return first;
    }
}
