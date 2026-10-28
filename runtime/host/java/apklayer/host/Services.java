package apklayer.host;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.internal.DoNotInstrument;

/**
 * Runs the app's own services in-process (PLAN P6 groundwork). The converter output routes the app's
 * {@code startService / startForegroundService / stopService / bindService / unbindService} calls here
 * (DenyInstrument): Robolectric's stock {@code bindService} only reports a preconfigured binder, so
 * apps that cast the binder in {@code onServiceConnected} (Termux, music players...) failed. Services
 * the app does not declare itself go to the framework unchanged.
 */
@DoNotInstrument
public final class Services {
    private static final class Running {
        ServiceController<? extends Service> controller;
        Service service;
        IBinder binder;
        boolean bound, started;
        int lastStartId, startResult;
        IBinder traced;
        int connections;
        Intent intent;
    }

    private static final Map<String, Running> RUNNING = new HashMap<>();
    private static final Map<ServiceConnection, String> CONNECTIONS = new IdentityHashMap<>();
    private static int startId;
    /** ServiceController.create idles the main looper: a bind queued behind the one that creates a service would run inside it and create the service again. */
    private static boolean creating;
    private static final java.util.ArrayDeque<Runnable> DEFERRED = new java.util.ArrayDeque<>();

    private Services() {}

    /** The app's service class named by the intent, or null if it is not one of ours. */
    private static ComponentName resolve(Context ctx, Intent intent) {
        ComponentName cn = intent.getComponent();
        if (cn == null) {
            ResolveInfo ri = ctx.getPackageManager().resolveService(intent, 0);
            if (ri == null || ri.serviceInfo == null) return null;
            cn = new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name);
        }
        if (!cn.getPackageName().equals(ctx.getPackageName())) return null;
        try {
            Class.forName(className(cn), false, Services.class.getClassLoader()).asSubclass(Service.class);
            return cn;
        } catch (ClassNotFoundException | ClassCastException e) {
            return null;
        }
    }

    private static String className(ComponentName cn) {
        String n = cn.getClassName();
        return n.startsWith(".") ? cn.getPackageName() + n : n;
    }

    @SuppressWarnings("unchecked")
    private static Running instance(ComponentName cn, Intent intent) throws ClassNotFoundException {
        String key = className(cn);
        Running r = RUNNING.get(key);
        if (r == null) {
            r = new Running();
            r.intent = intent;
            Class<? extends Service> cls = (Class<? extends Service>) Class.forName(key, true, Services.class.getClassLoader());
            if (Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] creating service " + cls.getName() + " on " + Thread.currentThread().getName());
            r.controller = Robolectric.buildService(cls, intent);
            creating = true;
            try { r.controller.create(); } finally { creating = false; }
            r.service = r.controller.get();
            RUNNING.put(key, r);
            System.out.println("[apklayer] service created: " + cls.getSimpleName());
        }
        return r;
    }

    /** A service in a process of its own (GeckoView children) dies with its last service, as Android kills that process: the next bind starts a fresh one (their static state forbids reuse). */
    private static void destroy(String key, Running r) {
        r.controller.destroy();
        RUNNING.remove(key);
        if (Boolean.getBoolean("apklayer.componenthost") && RUNNING.isEmpty()) { System.out.println("[apklayer] last service of this process destroyed: exiting"); System.out.flush(); Runtime.getRuntime().halt(0); }
    }

    /** Runs a task on the main thread and waits for it (services are created and called there). */
    private static <T> T onMain(java.util.function.Supplier<T> task) {
        if (Looper.myLooper() == Looper.getMainLooper()) return task.get();
        Object[] out = new Object[1];
        RuntimeException[] error = new RuntimeException[1];
        CountDownLatch done = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                out[0] = task.get();
            } catch (RuntimeException e) {
                error[0] = e;
            } finally {
                done.countDown();
            }
        });
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (error[0] != null) throw error[0];
        @SuppressWarnings("unchecked") T t = (T) out[0];
        return t;
    }

    // ---- entry points the converter routes the app's calls to ----

    public static ComponentName startService(Context ctx, Intent intent) {
        ComponentName cn = resolve(ctx, intent);
        if (cn == null) return ctx.startService(intent);
        return onMain(() -> {
            try {
                deliver(cn, intent, intent);
                return cn;
            } catch (ClassNotFoundException e) {
                return null;
            }
        });
    }

    /** Creates the service when needed and delivers one start command (this call's intent: the controller would resend the creation one); {start id, onStartCommand's result}. */
    private static int[] deliver(ComponentName cn, Intent created, Intent delivered) throws ClassNotFoundException {
        Running r = instance(cn, created);
        r.started = true;
        r.lastStartId = ++startId;
        r.startResult = r.service.onStartCommand(delivered, 0, r.lastStartId);
        for (Runnable next; (next = DEFERRED.poll()) != null; ) next.run();  // binds that arrived while the service was being created
        return new int[] {r.lastStartId, r.startResult};
    }

    /** The hub starts one of this app's services on behalf of another app (a null delivered intent is the redelivery after a restart); null when it is not ours. */
    static int[] startFromHub(Context ctx, Intent explicit, Intent delivered) {
        if (resolve(ctx, explicit) == null) return null;
        // apklayer patch: creating a service lets the main looper run other posted work; a start that arrives meanwhile would create a second
        // instance (the first is not in RUNNING yet), so it waits like a bind does (DEFERRED) and runs when the creation is done
        java.util.concurrent.CompletableFuture<int[]> result = new java.util.concurrent.CompletableFuture<>();
        Runnable start = () -> {
            try { result.complete(deliver(explicit.getComponent(), explicit, delivered)); }
            catch (ClassNotFoundException e) { result.complete(null); }
            catch (RuntimeException failure) { result.completeExceptionally(failure); }
        };
        new Handler(Looper.getMainLooper()).post(() -> { if (creating) DEFERRED.add(start); else start.run(); });
        try { return result.get(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        catch (java.util.concurrent.ExecutionException failure) { throw failure.getCause() instanceof RuntimeException r ? r : new IllegalStateException(failure.getCause()); }
    }

    /** Service.stopSelf / stopSelfResult: stops when no newer start command arrived (start id -1 always stops) and nothing is bound. */
    static boolean stopSelf(ComponentName cn, int id) {
        return onMain(() -> {
            Running r = RUNNING.get(className(cn));
            if (r == null || (id >= 0 && id != r.lastStartId)) return false;
            r.started = false;
            if (r.connections == 0) { destroy(className(cn), r); }
            return true;
        });
    }

    public static ComponentName startForegroundService(Context ctx, Intent intent) {
        return startService(ctx, intent);
    }

    public static boolean stopService(Context ctx, Intent intent) {
        ComponentName cn = resolve(ctx, intent);
        if (cn == null) return ctx.stopService(intent);
        return onMain(() -> {
            Running r = RUNNING.get(className(cn));
            if (r == null) return false;
            r.started = false;
            if (r.connections == 0) {
                destroy(className(cn), r);
            }
            return true;
        });
    }

    public static boolean bindService(Context ctx, Intent intent, ServiceConnection conn, int flags) {
        ComponentName cn = resolve(ctx, intent);
        if (cn == null) return ctx.bindService(intent, conn, flags);
        return onMain(() -> {
            if (creating) { DEFERRED.add(() -> bindService(ctx, intent, conn, flags)); return true; }
            try {
                Running r = instance(cn, intent);
                r.connections++; // before onBind: a service that calls stopSelf() there (Chromium's ChildProcessService) stays while it is bound
                if (!r.bound) {
                    try { r.binder = r.service.onBind(intent); }
                    catch (RuntimeException failure) { r.connections--; throw failure; }
                    r.bound = true;
                }
                CONNECTIONS.put(conn, className(cn));
                IBinder shown = r.binder;
                if (Boolean.getBoolean("apklayer.tracebind") && shown != null) { if (r.traced == null) r.traced = traced(shown); shown = r.traced; } // one IBinder for every connection, as on Android
                IBinder binder = shown;
                new Handler(Looper.getMainLooper()).post(() -> conn.onServiceConnected(cn, binder));
                for (Runnable next; (next = DEFERRED.poll()) != null; ) next.run();
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        });
    }

    /** Debugging (-Dapklayer.logcat): a Binder in front of the service's that prints every transaction it gets and how it ended. */
    private static IBinder traced(IBinder original) {
        if (original == null) return null;
        return new android.os.Binder() {
            @Override public String getInterfaceDescriptor() { try { return original.getInterfaceDescriptor(); } catch (RuntimeException | android.os.RemoteException e) { return null; } }
            @Override protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
                System.out.println("[apklayer] binder " + original.getClass().getSimpleName() + " transaction " + code + " from pid " + android.os.Binder.getCallingPid());
                try { boolean ok = original.transact(code, data, reply, flags); String note = "";
                    if (reply != null && reply.dataSize() > 0) { int at = reply.dataPosition(); reply.setDataPosition(0); int status = reply.readInt(); note = " exception code " + status + (status != 0 ? " " + reply.readString() : " string? " + (code == 2 ? reply.readString() : "")); reply.setDataPosition(at); }
                    System.out.println("[apklayer] binder transaction " + code + " -> " + ok + " reply " + (reply == null ? -1 : reply.dataSize()) + " bytes" + note); return ok; }
                catch (RuntimeException | android.os.RemoteException failure) { failure.printStackTrace(System.out); throw failure; }
            }
        };
    }

    public static void unbindService(Context ctx, ServiceConnection conn) {
        String key = CONNECTIONS.remove(conn);
        if (key == null) {
            ctx.unbindService(conn);
            return;
        }
        onMain(() -> {
            Running r = RUNNING.get(key);
            if (r != null && Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] unbind: " + (r.connections - 1) + " connections left of " + key);
            if (r != null && --r.connections == 0) {
                r.service.onUnbind(r.intent);
                r.bound = false;
                if (!r.started) {
                    destroy(key, r);
                }
            }
            return null;
        });
    }
}
