package apklayer.host;

import android.app.IApplicationThread;
import android.content.ComponentName;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageParser;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The instance's broadcast queue, what the activity manager is on a phone: dynamic registrations from every app, manifest receivers of every installed package
 * (a package that is not running is started), permission and export checks, ordered delivery by priority with result propagation, sticky intents.
 * Receivers are reached through the framework's own IIntentReceiver (dynamic) and IApplicationThread.scheduleReceiver (manifest): the apps run Android's code.
 */
final class HubBroadcasts {
    private static final long ORDERED_TIMEOUT_MS = 10_000, START_TIMEOUT_MS = 20_000;

    private record Reg(HubDirectory.Peer peer, IBinder binder, IIntentReceiver receiver, IntentFilter filter, String permission, int flags) {}
    private record Target(IntentFilter filter, Reg dynamic, ActivityInfo manifest) {
        int priority() { return filter == null ? 0 : filter.getPriority(); }
    }
    private record Result(int code, String data, Bundle extras, boolean abort) {}

    private final HubDirectory directory;
    private final HubPackages packages;
    private final List<Reg> registrations = new ArrayList<>();
    private final List<Intent> sticky = new ArrayList<>();
    private final Map<IBinder, CompletableFuture<Result>> pending = new HashMap<>();
    private final Map<String, CompletableFuture<HubDirectory.Peer>> starting = new HashMap<>();
    // one queue, in send order: an ordered broadcast holds it while its receivers run, like the foreground queue
    private final ExecutorService queue = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "hub-broadcast-queue"); thread.setDaemon(true); return thread;
    });

    HubBroadcasts(HubDirectory directory, HubPackages packages) { this.directory = directory; this.packages = packages; }

    /** Registers a dynamic receiver; returns the first matching sticky intent (and queues all matching ones to the receiver). */
    Intent register(HubDirectory.Peer caller, IBinder binder, IntentFilter filter, String permission, int flags) {
        if (binder == null || filter == null) throw new IllegalArgumentException("Missing receiver registration");
        IIntentReceiver receiver = IIntentReceiver.Stub.asInterface(binder);
        Reg registration = new Reg(caller, binder, receiver, filter, permission, flags);
        try { binder.linkToDeath(() -> removeAll(binder), 0); }
        catch (RemoteException failure) { throw new IllegalStateException("Receiver already gone", failure); }
        List<Intent> matching = new ArrayList<>();
        synchronized (this) {
            registrations.add(registration);
            for (Intent intent : sticky) if (filter.match(null, intent, false, "apklayer-hub") >= 0) matching.add(new Intent(intent));
        }
        if (receiver != null && !matching.isEmpty()) {
            queue.execute(() -> {
                for (Intent intent : matching) {
                    try { receiver.performReceive(intent, 0, null, null, false, true, 0); } catch (RemoteException gone) { return; }
                }
            });
        }
        return matching.isEmpty() ? null : matching.get(0);
    }

    void unregister(HubDirectory.Peer caller, IBinder binder) {
        synchronized (this) {
            if (registrations.stream().noneMatch(reg -> reg.binder.equals(binder) && reg.peer.pid() == caller.pid()))
                throw new IllegalArgumentException("Receiver is not registered");
        }
        removeAll(binder);
    }

    private void removeAll(IBinder binder) { synchronized (this) { registrations.removeIf(reg -> reg.binder.equals(binder)); } }

    void died(HubDirectory.Peer peer) {
        synchronized (this) {
            registrations.removeIf(reg -> reg.peer.pid() == peer.pid());
            CompletableFuture<Result> waiting = pending.remove(peer.appThread());
            if (waiting != null) waiting.complete(new Result(0, null, null, false));
        }
    }

    void registered(HubDirectory.Peer peer) {
        CompletableFuture<HubDirectory.Peer> waiting;
        synchronized (this) { waiting = starting.remove(peer.name()); }
        if (waiting != null) waiting.complete(peer);
    }

    void removeSticky(HubDirectory.Peer caller, Intent intent) {
        synchronized (this) { sticky.removeIf(old -> old.filterEquals(intent)); }
    }

    /** What the receiver (a dynamic registration's binder, or a manifest receiver's application thread) answers when it is done. */
    void finish(HubDirectory.Peer caller, IBinder who, int code, String data, Bundle extras, boolean abort) {
        CompletableFuture<Result> waiting;
        synchronized (this) { waiting = pending.remove(who); }
        System.out.println("[apklayer-hub] finish from " + caller.name() + " code=" + code + " data=" + data + " abort=" + abort + " matched=" + (waiting != null));
        if (waiting != null) waiting.complete(new Result(code, data, extras, abort));
    }

    int send(HubDirectory.Peer sender, Intent intent, IBinder resultTo, int code, String data, Bundle extras, String[] required,
            boolean ordered, boolean isSticky, int userId) {
        if (intent == null) throw new IllegalArgumentException("Missing intent");
        System.out.println("[apklayer-hub] send " + intent.getAction() + " from " + sender.name());
        if (intent.getAction() == null && intent.getComponent() == null) throw new IllegalArgumentException("A broadcast needs an action or a component");
        if (isSticky) {
            if (!packages.holds(sender.name(), "android.permission.BROADCAST_STICKY")) throw new SecurityException("Requires BROADCAST_STICKY");
            if (required != null && required.length > 0) throw new SecurityException("Sticky broadcasts cannot require permissions");
            synchronized (this) { sticky.removeIf(old -> old.filterEquals(intent)); sticky.add(new Intent(intent)); }
        }
        Intent copy = new Intent(intent);
        queue.execute(() -> {
            try { run(sender, copy, resultTo, code, data, extras, required == null ? new String[0] : required, ordered, isSticky, userId); }
            catch (RuntimeException failure) { System.out.println("[apklayer-hub] broadcast " + copy.getAction() + " failed: " + failure); }
        });
        return 0;
    }

    private List<Target> receivers(HubDirectory.Peer sender, Intent intent, String[] required) {
        List<Target> targets = new ArrayList<>();
        ComponentName explicit = intent.getComponent();
        String onlyPackage = explicit != null ? explicit.getPackageName() : intent.getPackage();
        List<Reg> dynamic;
        synchronized (this) { dynamic = List.copyOf(registrations); }
        if (explicit == null) {
            for (Reg reg : dynamic) {
                if (onlyPackage != null && !onlyPackage.equals(reg.peer.name())) continue;
                if (reg.filter.match(null, intent, false, "apklayer-hub") < 0) continue;
                // RECEIVER_NOT_EXPORTED (flag 4): only its own app may reach it
                if ((reg.flags & 4) != 0 && !reg.peer.name().equals(sender.name())) continue;
                if (reg.permission != null && !reg.permission.isEmpty() && !reg.peer.name().equals(sender.name()) && !packages.holds(sender.name(), reg.permission)) {
                    System.out.println("[apklayer-hub] " + sender.name() + " lacks " + reg.permission + " for a receiver of " + reg.peer.name()); continue;
                }
                if (!holdsAll(reg.peer.name(), required)) continue;
                targets.add(new Target(reg.filter, reg, null));
            }
        }
        for (HubPackages.Receiver receiver : packages.receivers(intent, onlyPackage)) {
            ActivityInfo info = receiver.info();
            if (!info.enabled || !info.applicationInfo.enabled) continue;
            boolean same = info.packageName.equals(sender.name());
            if (!same && !info.exported) continue;
            if (!same && info.permission != null && !info.permission.isEmpty() && !packages.holds(sender.name(), info.permission)) {
                System.out.println("[apklayer-hub] " + sender.name() + " lacks " + info.permission + " for " + info.packageName + "/" + info.name); continue;
            }
            if (!holdsAll(info.packageName, required)) continue;
            targets.add(new Target(receiver.filter(), null, info));
        }
        return targets;
    }

    private boolean holdsAll(String pkg, String[] required) {
        for (String permission : required) if (permission != null && !permission.isEmpty() && !packages.holds(pkg, permission)) return false;
        return true;
    }

    private void run(HubDirectory.Peer sender, Intent intent, IBinder resultTo, int code, String data, Bundle extras, String[] required,
            boolean ordered, boolean isSticky, int userId) {
        List<Target> targets = receivers(sender, intent, required);
        System.out.println("[apklayer-hub] broadcast " + intent.getAction() + " from " + sender.name() + (ordered ? " ordered" : "") + (isSticky ? " sticky" : "") + ": " + targets.size() + " receivers");
        if (ordered) targets.sort(Comparator.comparingInt(Target::priority).reversed()); // stable: registered receivers keep their lead at equal priority
        int resultCode = code;
        String resultData = data;
        Bundle resultExtras = extras;
        for (Target target : targets) {
            Intent delivery = new Intent(intent);
            if (target.manifest != null) delivery.setComponent(new ComponentName(target.manifest.packageName, target.manifest.name));
            if (!ordered) { deliver(target, delivery, resultCode, resultData, resultExtras, false, isSticky, userId, false); continue; }
            Result result = deliver(target, delivery, resultCode, resultData, resultExtras, true, isSticky, userId, true);
            if (result != null) { resultCode = result.code; resultData = result.data; resultExtras = result.extras; if (result.abort) break; }
        }
        if (resultTo != null) {
            try { IIntentReceiver.Stub.asInterface(resultTo).performReceive(new Intent(intent), resultCode, resultData, resultExtras, false, false, userId); }
            catch (RemoteException gone) { System.out.println("[apklayer-hub] the result receiver of " + intent.getAction() + " is gone"); }
        }
    }

    /** Delivers to one receiver; when waiting (ordered) returns what it answered, or null when it did not answer in time. */
    private Result deliver(Target target, Intent intent, int code, String data, Bundle extras, boolean ordered, boolean isSticky, int userId, boolean wait) {
        IBinder who;
        HubDirectory.Peer peer = null;
        if (target.dynamic != null) who = target.dynamic.peer.appThread();  // an app's receivers finish through its application thread token
        else {
            peer = process(target.manifest.packageName);
            if (peer == null) { System.out.println("[apklayer-hub] could not start " + target.manifest.packageName + " for " + intent.getAction()); return null; }
            who = peer.appThread();
        }
        CompletableFuture<Result> answer = new CompletableFuture<>();
        if (wait) synchronized (this) { pending.put(who, answer); }
        try {
            if (target.dynamic != null) target.dynamic.receiver.performReceive(intent, code, data, extras, ordered, isSticky, userId);
            else IApplicationThread.Stub.asInterface(who).scheduleReceiver(intent, target.manifest, null, code, data, extras, ordered, false, userId, 0, 0, null);
        } catch (RemoteException gone) {
            synchronized (this) { pending.remove(who); }
            return null;
        }
        if (!wait) return null;
        try { return answer.get(ORDERED_TIMEOUT_MS, TimeUnit.MILLISECONDS); }
        catch (Exception timeout) {
            synchronized (this) { pending.remove(who, answer); }
            System.out.println("[apklayer-hub] receiver of " + intent.getAction() + " did not finish in time");
            return null;
        }
    }

    /** The running process of a package, started when needed. */
    private HubDirectory.Peer process(String name) {
        HubDirectory.Peer peer = directory.peer(name);
        if (peer != null) return peer;
        CompletableFuture<HubDirectory.Peer> waiting;
        synchronized (this) { waiting = starting.computeIfAbsent(name, key -> new CompletableFuture<>()); }
        peer = directory.peer(name);
        if (peer != null) { synchronized (this) { starting.remove(name, waiting); } return peer; }
        directory.launch(name);
        try { return waiting.get(START_TIMEOUT_MS, TimeUnit.MILLISECONDS); }
        catch (Exception timeout) { synchronized (this) { starting.remove(name, waiting); } return directory.peer(name); }
    }
}
