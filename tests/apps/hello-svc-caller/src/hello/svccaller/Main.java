package hello.svccaller;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.widget.TextView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Starts, stops and inspects a started service of hello.svctarget (another app, started on demand by the hub). The scenario comes from -Dhello.svc=... */
public class Main extends Activity {
    static final ComponentName COUNTER = new ComponentName("hello.svctarget", "hello.svctarget.CounterService");

    private static void require(boolean yes, String detail) { if (!yes) throw new AssertionError(detail); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("svc caller");
        setContentView(view);
        new Thread(() -> {
            try { run(System.getProperty("hello.svc", "start")); }
            catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[svc] FAIL " + failure); }
        }, "svc-caller").start();
    }

    private Intent counter(String text) { return new Intent().setComponent(COUNTER).putExtra("text", text); }

    /** {count, last text, last start id, pid} of the running service instance, through a short-lived binding. */
    private Object[] state() throws Exception { return state(COUNTER); }

    private Object[] state(ComponentName service) throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        IBinder[] binder = new IBinder[1];
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { binder[0] = service; connected.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        require(bindService(new Intent().setComponent(service), connection, Context.BIND_AUTO_CREATE), "bind");
        require(connected.await(15, TimeUnit.SECONDS), "no connection");
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            require(binder[0].transact(1, data, reply, 0), "transact");
            return new Object[] {reply.readInt(), reply.readString(), reply.readInt(), reply.readInt()};
        } finally { data.recycle(); reply.recycle(); unbindService(connection); }
    }

    private void run(String scenario) throws Exception {
        switch (scenario) {
            case "start" -> {
                require(COUNTER.equals(startService(counter("a"))), "startService result");
                Object[] first = state();
                require((int) first[0] == 1 && "a".equals(first[1]), "first state " + java.util.Arrays.toString(first));
                startService(counter("b"));
                Object[] second = state();
                require((int) second[0] == 2 && "b".equals(second[1]) && (int) second[2] > (int) first[2] && (int) second[3] == (int) first[3], "second state " + java.util.Arrays.toString(second));
                require(stopService(new Intent().setComponent(COUNTER)), "stopService");
                Object[] fresh = state();  // a new instance: the old one was destroyed
                require((int) fresh[0] == 0, "service survived stopService " + java.util.Arrays.toString(fresh));
                System.out.println("[svc] PASS start: started in another app (pid " + first[3] + "), start ids, stopService destroys it");
            }
            case "selfstop" -> {
                startService(counter("s").putExtra("selfstop", true));
                Object[] fresh = state();
                require((int) fresh[0] == 0, "service survived stopSelf " + java.util.Arrays.toString(fresh));
                System.out.println("[svc] PASS selfstop: stopSelf in the service's app ended it");
            }
            case "sticky" -> {
                startService(counter("k").putExtra("mode", Service_START_STICKY));
                Object[] before = state();
                System.out.println("[svc] KILL " + before[3]);
                Thread.sleep(3000);
                Object[] after = null;
                for (int attempt = 0; attempt < 10; attempt++) {
                    after = state();
                    if ((int) after[0] >= 1) break;
                    Thread.sleep(1000);
                }
                require((int) after[3] != (int) before[3] && (int) after[0] == 1 && "(null)".equals(after[1]), "after the kill " + java.util.Arrays.toString(after));
                System.out.println("[svc] PASS sticky: restarted after the kill with a null intent, new pid " + after[3]);
            }
            case "respawn" -> {  // a service process that ends after each unbind starts again for every new binding (the hub must not count those exits as failures)
                ComponentName exit = new ComponentName(this, ExitService.class);
                java.util.Set<Integer> pids = new java.util.HashSet<>();
                for (int round = 0; round < 8; round++) pids.add(respawnedPid(exit));
                require(pids.size() >= 2, "the service process never ended: " + pids);
                System.out.println("[svc] PASS respawn: 8 bindings in a row connected, the service ran in " + pids.size() + " processes");
            }
            case "stoplock" -> {  // stopSelf from a thread holding a lock that onDestroy takes: no deadlock (the destroy runs later on the main thread)
                startService(new Intent(this, LockService.class));
                new Thread(() -> {
                    try { require(LockService.DESTROYED.await(10, TimeUnit.SECONDS), "the service was not destroyed: stopSelf waited for onDestroy (deadlock)"); System.out.println("[svc] PASS stoplock: stopSelf from a lock-holding thread returned, onDestroy ran on the main thread"); }
                    catch (Throwable failure) { System.out.println("[svc] FAIL stoplock " + failure); }
                }, "stoplock-check").start();
            }
            case "filters" -> filters();
            case "own" -> own();
            case "rebind" -> {  // unbind and bind again at once: the ":other" host, which ends with its last binding, never hands out its binder while it exits
                java.util.Set<Integer> pids = new java.util.HashSet<>();
                for (int round = 0; round < 8; round++) pids.add(pidOf(new Intent().setComponent(new ComponentName(this, OtherPidService.class)))[0]);
                require(pids.size() >= 2 && !pids.contains(android.os.Process.myPid()), "the \":other\" host never ended (or ran here): " + pids);
                System.out.println("[svc] PASS rebind: 8 bind/call/unbind rounds in a row, every call answered, the host ran in " + pids.size() + " processes");
            }
            case "framework" -> {  // start and stop through the context itself, as framework code calls them (the converter routes only the app's own calls)
                java.lang.reflect.Method start = Context.class.getMethod("startService", Intent.class), stop = Context.class.getMethod("stopService", Intent.class);
                Intent pid = new Intent(this, PidService.class);
                Object started = start.invoke(getBaseContext(), pid);
                require(new ComponentName(this, PidService.class).equals(started), "startService returned " + started);
                require(PidService.STARTED.await(10, TimeUnit.SECONDS), "onStartCommand never ran");
                require((Boolean) stop.invoke(getBaseContext(), pid), "stopService returned false");
                require(PidService.DESTROYED.await(10, TimeUnit.SECONDS) && PidService.destroyed == 1, "stopService: destroyed " + PidService.destroyed + " times");  // later, on the main thread, as on Android
                System.out.println("[svc] PASS framework: a same-process service started and stopped through ContextImpl ran onStartCommand and onDestroy here");
            }
            case "late" -> late();
            case "oneway" -> {
                String[] answers = probe(0, IBinder.FLAG_ONEWAY);
                String sync = answers[0], oneway = answers[1];
                System.out.println("[svc] probe sync: " + sync + "; oneway: " + oneway);
                String expected = "uid=" + android.os.Process.myUid() + " held=0 unheld=-1";
                require(sync.equals("pid=" + android.os.Process.myPid() + " " + expected), "synchronous caller identity or permissions: " + sync);
                require(oneway.equals("pid=0 " + expected), "oneway caller identity or permissions: " + oneway);
                System.out.println("[svc] PASS oneway: the service sees this app's uid and permissions over synchronous and oneway calls");
            }
            case "private" -> {
                try { startService(new Intent().setComponent(new ComponentName("hello.svctarget", "hello.svctarget.PrivateService"))); require(false, "private service started"); }
                catch (SecurityException expected) { System.out.println("[svc] PASS private: " + expected.getMessage()); }
            }
            default -> System.out.println("[svc] unknown scenario");
        }
    }

    /** The pid of the service process through one binding. A bind that lands while the last process ends may connect to it (Android hands out the
     * cached binder too): the call then fails with DeadObjectException and the binding is connected again to the restarted service. */
    private int respawnedPid(ComponentName service) throws Exception {
        java.util.concurrent.BlockingQueue<IBinder> connected = new java.util.concurrent.LinkedBlockingQueue<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) { connected.add(binder); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        require(bindService(new Intent().setComponent(service), connection, Context.BIND_AUTO_CREATE), "bind");
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                IBinder binder = connected.poll(15, TimeUnit.SECONDS);
                require(binder != null, "no connection within 15 s");
                Parcel data = Parcel.obtain(), reply = Parcel.obtain();
                try { binder.transact(1, data, reply, 0); reply.readInt(); reply.readString(); reply.readInt(); return reply.readInt(); }
                catch (android.os.DeadObjectException ended) { System.out.println("[svc] connected to the ending process, waiting for the restart"); }
                finally { data.recycle(); reply.recycle(); }
            }
            throw new AssertionError("the restarted service is dead too");
        } finally { unbindService(connection); }
    }

    /** EchoService's PROBE once per flags (0 or oneway), in one binding: the caller's pid, uid and both permission checks as the service saw them. */
    private String[] probe(int... flags) throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        IBinder[] binder = new IBinder[1];
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { binder[0] = service; connected.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        require(bindService(new Intent().setComponent(new ComponentName("hello.svctarget", "hello.svctarget.EchoService")), connection, Context.BIND_AUTO_CREATE), "bind");
        try {
            require(connected.await(15, TimeUnit.SECONDS), "no connection");
            String[] answers = new String[flags.length];
            for (int i = 0; i < flags.length; i++) {
                CountDownLatch answered = new CountDownLatch(1);
                int at = i;
                android.os.Binder callback = new android.os.Binder() {
                    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int f) {
                        answers[at] = "pid=" + data.readInt() + " uid=" + data.readInt() + " held=" + data.readInt() + " unheld=" + data.readInt();
                        answered.countDown();
                        return true;
                    }
                };
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken("hello.svctarget.IProbe");  // as an AIDL proxy does
                    data.writeStrongBinder(callback);
                    require(binder[0].transact(2, data, null, flags[i]), "transact");
                } finally { data.recycle(); }
                require(answered.await(15, TimeUnit.SECONDS), "no answer");
            }
            return answers;
        } finally { unbindService(connection); }
    }

    private static final int Service_START_STICKY = 1;

    /** One connection bound to two services at once; onBind once per distinct intent; onUnbind returning true gives onRebind for that intent. */
    private void filters() throws Exception {
        require(COUNTER.equals(startService(counter("f"))), "startService");  // keeps the service across its unbinds
        java.util.Map<String, IBinder> got = new java.util.concurrent.ConcurrentHashMap<>();
        CountDownLatch both = new CountDownLatch(2);
        ServiceConnection shared = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { got.put(name.getClassName(), service); both.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        ComponentName echo = new ComponentName("hello.svctarget", "hello.svctarget.EchoService");
        require(bindService(new Intent("a").setComponent(COUNTER), shared, Context.BIND_AUTO_CREATE), "bind a");
        require(bindService(new Intent().setComponent(echo), shared, Context.BIND_AUTO_CREATE), "bind echo");
        require(both.await(15, TimeUnit.SECONDS), "one connection, two services: connected " + got.keySet());
        Object[] a = binding(got.get(COUNTER.getClassName()));
        CountDownLatch other = new CountDownLatch(1);
        IBinder[] b = new IBinder[1];
        ServiceConnection second = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { b[0] = service; other.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        require(bindService(new Intent("b").setComponent(COUNTER), second, Context.BIND_AUTO_CREATE) && other.await(15, TimeUnit.SECONDS), "bind b");
        Object[] bb = binding(b[0]);
        require("a".equals(a[2]) && "b".equals(bb[2]) && (int) bb[0] == 2, "one onBind per intent: a " + java.util.Arrays.toString(a) + " b " + java.util.Arrays.toString(bb));
        unbindService(shared);  // both of its bindings end: intent a has no client left, onUnbind(a) asks for a rebind
        CountDownLatch again = new CountDownLatch(1);
        IBinder[] c = new IBinder[1];
        ServiceConnection third = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { c[0] = service; again.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        require(bindService(new Intent("a").setComponent(COUNTER), third, Context.BIND_AUTO_CREATE) && again.await(15, TimeUnit.SECONDS), "rebind a");
        Object[] cc = binding(c[0]);
        require("a".equals(cc[2]) && (int) cc[0] == 2 && (int) cc[1] == 1, "rebind of a: " + java.util.Arrays.toString(cc));
        unbindService(second); unbindService(third);
        require(stopService(new Intent().setComponent(COUNTER)), "stopService");
        System.out.println("[svc] PASS filters: one connection bound to two services, one onBind per intent, onRebind after onUnbind returned true");
    }

    /** A package installed while this app and a component host run (the test driver installs hello.svclate on "[svc] INSTALL"): it is known and its service runs. */
    private void late() throws Exception {
        require(COUNTER.equals(startService(counter("l"))), "startService");
        Object[] host = state();  // hello.svctarget's component host runs now
        java.nio.file.Path apps = java.nio.file.Path.of(System.getProperty("apklayer.apps")), info = apps.resolve("hello.svclate/info.properties");
        try (java.nio.file.WatchService watch = apps.getFileSystem().newWatchService()) {
            apps.register(watch, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE);
            System.out.println("[svc] INSTALL hello-svc-late");
            while (!java.nio.file.Files.exists(info)) {  // events, not a poll: the package directory appears, then its info.properties (written last by install)
                if (java.nio.file.Files.isDirectory(info.getParent())) info.getParent().register(watch, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE);  // a rename into the directory is a create
                if (java.nio.file.Files.exists(info)) break;
                java.nio.file.WatchKey key = watch.poll(60, TimeUnit.SECONDS);
                require(key != null, "hello.svclate was not installed");
                key.pollEvents(); key.reset();
            }
        }
        android.content.pm.PackageInfo installed = getPackageManager().getPackageInfo("hello.svclate", android.content.pm.PackageManager.GET_SERVICES);
        require(installed.services != null && installed.services.length == 1, "late package's services " + java.util.Arrays.toString(installed.services));
        ComponentName late = new ComponentName("hello.svclate", "hello.svctarget.CounterService");
        require(late.equals(startService(new Intent().setComponent(late).putExtra("text", "late"))), "startService of the late package");
        Object[] got = state(late);
        require((int) got[0] == 1 && "late".equals(got[1]) && (int) got[3] != (int) host[3], "late package's service " + java.util.Arrays.toString(got) + " host " + java.util.Arrays.toString(host));
        require(stopService(new Intent().setComponent(late)) && stopService(new Intent().setComponent(COUNTER)), "stopService");
        System.out.println("[svc] PASS late: a package installed while a component host ran is known (" + installed.packageName + ") and its service runs in its own process (pid " + got[3] + ")");
    }

    /** Own-package services bound by action and package (as microG binds its ":unstable" DroidGuard) and by component, in this process and in ":other"; binds nothing resolves return false and never connect. */
    private void own() throws Exception {
        int me = android.os.Process.myPid();
        int[] hereProbe = pidOf(new Intent("hello.svccaller.PID").setPackage(getPackageName()));
        int[] otherProbe = pidOf(new Intent("hello.svccaller.OTHER_PID").setPackage(getPackageName()));
        int here = hereProbe[0], other = otherProbe[0];
        int otherByName = pidOf(new Intent().setComponent(new ComponentName(this, OtherPidService.class)))[0];
        int[] isolated = pidOf(new Intent().setComponent(new ComponentName(this, IsolatedPidService.class)));
        // the multiprocess provider: created in this process and in ":other", not in the isolated one (ActivityManagerService gives an isolated process none)
        require(hereProbe[1] == 1 && otherProbe[1] == 1, "multiprocess provider missing: here " + hereProbe[1] + ", \":other\" " + otherProbe[1]);
        require(isolated[0] != me && isolated[1] == 0, "isolated service (pid " + isolated[0] + ") created the provider: " + isolated[1]);
        require(here == me, "in-process service runs in pid " + here + ", not " + me);
        // the ":other" host ends with its last binding (pidOf unbinds), so the two binds may see two pids
        require(other > 0 && other != me && otherByName > 0 && otherByName != me, "\":other\" service pids " + other + " / " + otherByName + " (this process " + me + ")");
        CountDownLatch called = new CountDownLatch(1);
        ServiceConnection none = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { System.out.println("[svc] connected to a missing service: " + name + " " + service); called.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        require(!bindService(new Intent().setComponent(new ComponentName(this, "hello.svccaller.Missing")), none, Context.BIND_AUTO_CREATE), "bind of a missing own component returned true");
        require(!bindService(new Intent("hello.svccaller.NOBODY").setPackage(getPackageName()), none, Context.BIND_AUTO_CREATE), "bind of an unresolved own action returned true");
        require(!bindService(new Intent().setComponent(new ComponentName("hello.nosuchpackage", "hello.Missing")), none, Context.BIND_AUTO_CREATE), "bind of a missing package returned true");
        require(!called.await(2, TimeUnit.SECONDS), "a refused bind connected");
        // bindService with an Executor: onServiceConnected runs on that executor's thread
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "svc-executor"));
        java.util.concurrent.BlockingQueue<String> thread = new java.util.concurrent.LinkedBlockingQueue<>();
        ServiceConnection onExecutor = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { thread.add(Thread.currentThread().getName()); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        require(bindService(new Intent("hello.svccaller.PID").setPackage(getPackageName()), Context.BIND_AUTO_CREATE, executor, onExecutor), "bind with an executor");
        String connectedOn = thread.poll(15, TimeUnit.SECONDS);
        unbindService(onExecutor);
        executor.shutdown();
        require("svc-executor".equals(connectedOn), "onServiceConnected of an executor bind ran on " + connectedOn);
        System.out.println("[svc] PASS own: in-process service pid " + here + ", \":other\" service pid " + other + " by action and by name; three unresolved binds returned false; an executor bind connected on its executor; the multiprocess provider in both, not in the isolated pid " + isolated[0]);
    }

    /** {pid, whether that process created ProbeProvider} of the service the intent binds. */
    private int[] pidOf(Intent intent) throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        IBinder[] binder = new IBinder[1];
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { binder[0] = service; connected.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        require(bindService(intent, connection, Context.BIND_AUTO_CREATE), "bind " + intent);
        try {
            require(connected.await(15, TimeUnit.SECONDS), "no connection for " + intent);
            require(binder[0] != null, "null binder for " + intent);
            Parcel data = Parcel.obtain(), reply = Parcel.obtain();
            try { require(binder[0].transact(1, data, reply, 0), "transact"); return new int[] {reply.readInt(), reply.readInt()}; }
            finally { data.recycle(); reply.recycle(); }
        } finally { unbindService(connection); }
    }

    private static Object[] binding(IBinder binder) throws Exception {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try { require(binder.transact(2, data, reply, 0), "transact 2"); return new Object[] {reply.readInt(), reply.readInt(), reply.readString()}; }
        finally { data.recycle(); reply.recycle(); }
    }
}
