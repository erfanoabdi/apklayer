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
        int connections;
        Intent intent;
    }

    private static final Map<String, Running> RUNNING = new HashMap<>();
    private static final Map<ServiceConnection, String> CONNECTIONS = new IdentityHashMap<>();
    private static int startId;

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
            r.controller = Robolectric.buildService(cls, intent);
            r.controller.create();
            r.service = r.controller.get();
            RUNNING.put(key, r);
            System.out.println("[apklayer] service created: " + cls.getSimpleName());
        }
        return r;
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
                Running r = instance(cn, intent);
                r.started = true;
                r.controller.startCommand(0, ++startId);
                return cn;
            } catch (ClassNotFoundException e) {
                return null;
            }
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
                r.controller.destroy();
                RUNNING.remove(className(cn));
            }
            return true;
        });
    }

    public static boolean bindService(Context ctx, Intent intent, ServiceConnection conn, int flags) {
        ComponentName cn = resolve(ctx, intent);
        if (cn == null) return ctx.bindService(intent, conn, flags);
        return onMain(() -> {
            try {
                Running r = instance(cn, intent);
                if (!r.bound) {
                    r.binder = r.service.onBind(intent);
                    r.bound = true;
                }
                r.connections++;
                CONNECTIONS.put(conn, className(cn));
                IBinder binder = r.binder;
                new Handler(Looper.getMainLooper()).post(() -> conn.onServiceConnected(cn, binder));
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        });
    }

    public static void unbindService(Context ctx, ServiceConnection conn) {
        String key = CONNECTIONS.remove(conn);
        if (key == null) {
            ctx.unbindService(conn);
            return;
        }
        onMain(() -> {
            Running r = RUNNING.get(key);
            if (r != null && --r.connections == 0) {
                r.service.onUnbind(r.intent);
                r.bound = false;
                if (!r.started) {
                    r.controller.destroy();
                    RUNNING.remove(key);
                }
            }
            return null;
        });
    }
}
