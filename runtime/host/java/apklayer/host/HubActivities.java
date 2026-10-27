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

    HubActivities(HubDirectory directory, HubPackages packages) { this.directory = directory; this.packages = packages; }

    /** False when no activity can handle the intent. */
    boolean start(HubDirectory.Peer caller, Intent intent, ResultReceiver resultTo) {
        ActivityInfo info = packages.activity(intent, caller.name());
        if (info == null) return false;
        Intent resolved = new Intent(intent).setComponent(new ComponentName(info.packageName, info.name));
        HubDirectory.Peer target = directory.appPeer(info.packageName);
        if (target != null) {
            HubProtocol.call(target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_START_ACTIVITY, request -> { request.writeTypedObject(resolved, 0); request.writeTypedObject(resultTo, 0); }, reply -> null);
        } else {
            synchronized (pending) { pending.computeIfAbsent(info.packageName, key -> new ArrayDeque<>()).add(new Launch(resolved, resultTo)); }
            directory.launchApp(info.packageName);
        }
        System.out.println("[apklayer-hub] " + caller.name() + " starts " + info.packageName + "/" + info.name);
        return true;
    }

    /** The launch waiting for a freshly started app process, if any. */
    Launch take(String name) {
        synchronized (pending) {
            Deque<Launch> queue = pending.get(name);
            return queue == null ? null : queue.poll();
        }
    }
}
