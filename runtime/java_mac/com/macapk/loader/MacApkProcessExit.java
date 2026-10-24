package com.macapk.loader;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;

/**
 * What a guest's System.exit / Runtime.exit / Runtime.halt does on this device.
 *
 * The converter re-points every such call site here (com.macapk.dex.GuestRepoint);
 * this class then does the two jobs Android splits between the kernel and the
 * ActivityManager:
 *
 * 1. END THE PROCESS WITHOUT DEADLOCKING. HotSpot's exit runs libc exit(), whose
 *    __cxa_finalize runs guest static destructors, and one that re-enters JNI
 *    blocks forever in JavaThread::block_if_vm_exited -- reproduced
 *    deterministically on Telegram (window close) and on Aurora Store's account
 *    switch (its "Restart" button). The only safe exit is the native _exit that
 *    the window-close path already uses (cocoa_bridge.mm macapk_terminate_now),
 *    reached here through NativeCocoa.exitProcess. That is also the AOSP-
 *    faithful answer: an app process is SIGKILLed, nothing runs at death.
 *
 * 2. PLAY THE ACTIVITYMANAGER. On a device the app's OWN state dies but a
 *    startActivity() already handed to the system survives it and opens in a
 *    brand-new process. That asymmetry is the basis of every "restart myself"
 *    idiom -- JakeWharton's ProcessPhoenix, shipped by Aurora Store and NewPipe:
 *
 *      main process:     startActivity(PhoenixActivity)  ; finish() ; Runtime.exit(0)
 *      ":phoenix" proc:  PhoenixActivity.onCreate -> killProcess(mainPid) ;
 *                        startActivities(restart intents) ; finish() ; Runtime.exit(0)
 *      (new) main proc:  the restart intents open the app afresh
 *
 *    Modelled here with two rules, both read off the manifest and the activity
 *    stack rather than off any app's name:
 *
 *    a. THE CALLER'S COMPONENT DECLARES A SECONDARY android:process. That process
 *       dying takes only its own components with it; the default process (the one
 *       we actually are) stays up, and whatever it started is delivered. So we
 *       finish that component's activities and RETURN. PhoenixActivity is exactly
 *       this: it has already posted the restart intents when it calls exit.
 *
 *    b. THE DEFAULT PROCESS EXITS WITH A SAME-PACKAGE START STILL PENDING. That is
 *       the restart request. The pending Intents are marshalled to a file and the
 *       native side relaunches the bundle -- after this process is gone, so the
 *       app stub's single-instance lock sees a free slot -- with
 *       MACAPK_RESTART_INTENTS naming the file; Boot opens on the first of them
 *       instead of the launcher (the pending PhoenixActivity), which then runs
 *       rule (a) and hands control to the real restart intents. Two processes,
 *       as on a device, minus the third one Android spends on ":phoenix".
 *
 *    c. Otherwise the app asked to quit: the window closes, as on a device.
 *
 * WHICH PROCESS IS EXITING. A process exit kills the CALLER's process, so the
 * question rule (a) must answer is "which process does the calling thread
 * belong to", not "which activity is on top". Those differ exactly when a
 * worker thread outlives the moment it was started in: OpenTTD's
 * RestartMainActivity (android:process=":RestartMainActivity") starts
 * `new Thread(RestartMainActivity$1)` whose run() is, from the app's own
 * bytecode, sleep(2000); startActivity(MainActivity); sleep(1000);
 * System.exit(0). By the exit, MainActivity (default process) is on top, so
 * keying rule (a) on the top activity filed the ":RestartMainActivity"
 * process's exit as the DEFAULT process's and ended the whole app in the middle
 * of its first-run data install (fleet A/B 2026-09-24, the fleet's only CRASH;
 * docs/fleet-emulator-ab_2026-09-25.md). So:
 *
 *    - On the MAIN thread the top activity stays the answer. "The main thread"
 *      is the ONE thread Boot designated, by identity (onMainThread): a worker
 *      is never promoted to it by having called startActivity (2026-09-25,
 *      the reason this attribution had never fired live for OpenTTD). Every
 *      one of our hosted "processes" shares that thread, the component running
 *      on it is the one being dispatched, and ActivityHost pushes an activity BEFORE its
 *      onCreate, so PhoenixActivity.onCreate's exit keeps resolving to
 *      ":phoenix" exactly as before.
 *    - On any OTHER thread the answer is the component whose code the thread
 *      was started to run: the OUTERMOST guest frame on its stack (the
 *      Runnable/Thread body that sits right under Thread.run), mapped to a
 *      manifest component through its enclosing classes (RestartMainActivity$1
 *      -> RestartMainActivity). Outermost, not innermost, because a component
 *      class's STATIC helpers run in whichever process calls them: old
 *      ProcessPhoenix.triggerRebirth is a static on the ":phoenix" activity
 *      class that the DEFAULT process calls, and must stay the default's exit.
 *      When that frame is not a manifest component (a thread pool worker, a
 *      library Thread subclass, a native-attached GL thread) we know nothing
 *      better than before and keep the top-activity answer.
 *
 * And the calling thread never comes back from a contained exit. AOSP
 * libcore Runtime.exit (android14-release ojluni/.../java/lang/Runtime.java:
 * 143-178) ends in nativeExit(status), and its javadoc says a caller that
 * arrives after shutdown began "blocks indefinitely"; nothing on the dying
 * process runs another instruction. So a worker thread whose process we just
 * ended is parked for good instead of returning into code that assumed it was
 * dead. The main thread cannot be parked (the default process lives on it), so
 * it returns, as it always has. Every hosted "process" shares ONE JVM, though,
 * so a worker is parked only when nothing the default process needs goes down
 * with it: the thread must have been created for that component's code (not a
 * pool thread such as AsyncTask's single serial executor, which the default
 * process shares), and it must hold no monitor or lock (a device frees those
 * with the process; here they would deadlock the survivor). Otherwise it
 * returns, as it did before, with a log line saying why.
 *
 * MACAPK_EXIT_THREAD_OWNER=0 restores the top-activity attribution on every
 * thread, and with it the returning worker, for bisecting; default ON. It is
 * NOT a full pre-v1.0.1645 reproduction, deliberately: a contained exit on a
 * worker still hands the activity finish to the main looper (the old inline
 * finish mutated main-thread ActivityHost state from the worker), and the
 * process names are still compared RESOLVED (":x" equals "<pkg>:x"). With it
 * ON, a worker is parked only when its OWN entry frame named the ended process
 * and the conditions above hold; the top-activity fallback still returns, as
 * before, since it cannot tell whose thread it is.
 * MACAPK_GUEST_EXIT=legacy restores the raw Runtime.exit call for bisecting
 * (i.e. the deadlock); default is this class.
 */
public final class MacApkProcessExit {
    private MacApkProcessExit() {}
    private static final String TAG = "Mac.apk.Process";
    private static final boolean LEGACY = "legacy".equals(System.getenv("MACAPK_GUEST_EXIT"));

    public static void systemExit(int status)            { exit(status, "System.exit"); }
    public static void runtimeExit(Runtime r, int status) { exit(status, "Runtime.exit"); }
    public static void runtimeHalt(Runtime r, int status) { exit(status, "Runtime.halt"); }

    /** Bisect gate for the calling-thread attribution; see the class comment. */
    static final boolean THREAD_OWNER = !"0".equals(System.getenv("MACAPK_EXIT_THREAD_OWNER"));

    private static void exit(int status, String api) {
        if (LEGACY) { Runtime.getRuntime().exit(status); return; }
        boolean onMain = onMainThread();
        Owner contained = exitLocked(status, api, onMain);
        // Only rule (a) returns here; rules (b) and (c) end this process. Park
        // only a thread we KNOW belongs to the ended process AND that nothing
        // else here depends on:
        //  - byThreadEntry: its entry frame is that component's code. The
        //    top-activity fallback cannot tell whose a thread is, so it returns.
        //  - dedicated: the thread was created to run that code (nothing but
        //    java.lang.Thread under the guest frame). A pool thread is SHARED
        //    across every process we host, e.g. AsyncTask's single serial
        //    thread; on a device the dead process's pool died with it, here
        //    parking it would starve the default process forever. It returns.
        //  - holds no locks: see heldLocks().
        if (contained != null && contained.byThreadEntry && contained.dedicated && !onMain) {
            String held = heldLocks();
            if (held == null) {
                endThisThread(api, status);
            } else {
                NativeLog.w(TAG, api + "(" + status + ") on " + Thread.currentThread().getName()
                        + ": NOT parking the ended process's thread, it holds " + held
                        + "; on a device these die with the process, here the default"
                        + " process shares them and would deadlock. Returning instead.");
            }
        } else if (contained != null && contained.byThreadEntry && !onMain) {
            NativeLog.i(TAG, api + "(" + status + ") on " + Thread.currentThread().getName()
                    + ": a shared executor thread ran the ended process's task; it returns"
                    + " to its pool rather than park, since the default process uses it too");
        }
    }

    /** Null when the current thread provably holds no monitor and no ownable
     *  synchronizer, else a description of what it holds. A parked thread keeps
     *  them forever, and every hosted "process" shares one heap, so a lock a
     *  dead process's worker held (an exit inside synchronized(singleton), a
     *  static synchronized helper) would deadlock the default process, which a
     *  device never does because the kernel frees the whole process. We choose
     *  to RETURN in that case (the pre-v1.0.1645 behaviour for that thread)
     *  over parking faithfully, because keeping the default process alive is
     *  the reason the exit was contained at all. "Could not check" counts as
     *  holding something, for the same reason. Package-private for the gate. */
    static String heldLocks() {
        try {
            java.lang.management.ThreadInfo[] ti = java.lang.management.ManagementFactory
                .getThreadMXBean()
                .getThreadInfo(new long[] { Thread.currentThread().threadId() }, true, true);
            if (ti == null || ti.length == 0 || ti[0] == null) return "unknown locks (no ThreadInfo)";
            java.lang.management.MonitorInfo[] mon = ti[0].getLockedMonitors();
            java.lang.management.LockInfo[] syn = ti[0].getLockedSynchronizers();
            if (mon.length == 0 && syn.length == 0) return null;
            StringBuilder sb = new StringBuilder();
            for (java.lang.management.MonitorInfo mi : mon) {
                sb.append(sb.length() == 0 ? "" : ", ").append("monitor ").append(mi)
                  .append(" (taken in ").append(mi.getLockedStackFrame()).append(')');
            }
            for (java.lang.management.LockInfo li : syn) {
                sb.append(sb.length() == 0 ? "" : ", ").append("synchronizer ").append(li);
            }
            return sb.toString();
        } catch (Throwable t) {
            return "unknown locks (" + t + ")";
        }
    }

    /** Returns the owner when the exit was contained to a secondary process;
     *  never returns otherwise. The monitor serialises the decision exactly as
     *  the old synchronized exit did, but a contained worker must not stay
     *  parked INSIDE it, or the default process could never exit afterwards. */
    private static synchronized Owner exitLocked(int status, String api, boolean onMain) {
        Manifest m = Boot.macapkManifest();
        String pkg = m != null ? m.packageName : null;

        // Rule (a): a secondary-process component is the one leaving.
        Activity top = ActivityHost.macapkTopActivity();
        Owner owner = exitingProcess(m, ActivityHost.macapkAppClassLoader(), onMain,
                top != null ? top.getClass().getName() : null);
        if (owner.process != null) {
            final java.util.Set<String> same = activitiesIn(m, owner.process);
            NativeLog.i(TAG, api + "(" + status + ") from " + owner.by
                    + " (" + (owner.byThreadEntry ? "the calling thread's entry" : "top activity")
                    + " on " + Thread.currentThread().getName() + "), declared android:process=\""
                    + owner.process + "\": that process ends,"
                    + " the default one we host stays up and delivers what it started");
            // The activity stack is main-thread state (ActivityHost mutates it
            // there), and AOSP removes a dead process's activities from the
            // system side, never from a thread of the process that died. So a
            // worker's exit hands the removal to the main looper.
            if (onMain || !android.os.Looper.macapkHasMainThread()) {
                ActivityHost.macapkFinishActivities(same);
            } else {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() { ActivityHost.macapkFinishActivities(same); }
                });
            }
            return owner;
        }

        // Rule (b): the default process is leaving with a start still owed.
        java.util.List<Intent> pending = ActivityHost.macapkPendingSelfStarts(pkg);
        String file = null;
        if (!pending.isEmpty()) {
            try {
                android.os.Parcel p = android.os.Parcel.obtain();
                p.writeInt(pending.size());
                for (Intent in : pending) in.writeToParcel(p, 0);
                byte[] bytes = p.marshall();
                java.io.File f = java.io.File.createTempFile("macapk-restart-", ".parcel");
                java.nio.file.Files.write(f.toPath(), bytes);
                file = f.getAbsolutePath();
                StringBuilder sb = new StringBuilder();
                for (Intent in : pending) {
                    ComponentName cn = in.getComponent();
                    sb.append(cn == null ? "?" : cn.getClassName()).append(' ');
                }
                NativeLog.i(TAG, api + "(" + status + ") with " + pending.size()
                        + " pending start(s) [" + sb.toString().trim() + "]: relaunching"
                        + " the app on them, as the ActivityManager would");
            } catch (Throwable t) {
                NativeLog.w(TAG, api + ": could not marshal the pending starts (" + t
                        + "); exiting without relaunch");
                file = null;
            }
        } else {
            NativeLog.i(TAG, api + "(" + status + ") from the app (" + owner.by + " on "
                    + Thread.currentThread().getName() + "): exiting");
        }
        NativeCocoa.exitProcess(status, file);
        // Not reached. If the native call ever returns, fall back to the raw
        // path rather than let the app run on past an exit it requested.
        Runtime.getRuntime().halt(status);
        return null;
    }

    /** The main (UI) thread, without the side effect of getMainLooper(), which
     *  would PIN the main thread to the caller if none were registered yet.
     *
     *  THE one Android main thread, by identity (ActivityHost.macapkOnMainThread),
     *  not "any member of Looper's main-thread set". A set member that is not the
     *  thread Boot designated -- a worker that called startActivity before
     *  ActivityHost stopped registering such callers, the frame-callback thread
     *  ViewCanvasDriver marks -- is not where the default process lives, and
     *  treating it as main skips the calling-thread attribution below. That is
     *  exactly how OpenTTD's RestartMainActivity$1 exit was still charged to the
     *  top activity after v1.0.1645 (docs/tablet-ab-triage_2026-09-25.md).
     *  MACAPK_ONE_MAIN_THREAD=0 restores set membership. Package-private for
     *  debug/procexit_gate.sh, which calls it from a real worker. */
    static boolean onMainThread() {
        try {
            return android.os.Looper.macapkHasMainThread()
                && ActivityHost.macapkOnMainThread();
        } catch (Throwable t) {
            return false;
        }
    }

    /** The process a contained exit ended was this thread's: it never runs
     *  again (libcore Runtime.exit "blocks indefinitely"). Renamed so a thread
     *  dump says why it is parked rather than looking like a hang. */
    private static void endThisThread(String api, int status) {
        Thread t = Thread.currentThread();
        try { t.setName(t.getName() + " [" + api + "(" + status + "): process ended]"); }
        catch (Throwable ignored) {}
        parkForever();
    }

    /** Parks the current thread for good. LockSupport.park returns at once while
     *  the interrupt status is set and does NOT clear it, so without the
     *  Thread.interrupted() every interrupt (an onDestroy's worker.interrupt(),
     *  or a thread that was already interrupted when it called exit) would turn
     *  this loop into a 100% CPU spin. Package-private for the gate. */
    static void parkForever() {
        while (true) {
            java.util.concurrent.locks.LockSupport.park(MacApkProcessExit.class);
            Thread.interrupted();
        }
    }

    /** Who an exit on the current thread belongs to. */
    static final class Owner {
        /** Resolved name of the secondary process that is exiting, or null
         *  for the default process. */
        final String process;
        /** The class the answer was read off, for the log. */
        final String by;
        /** True when read off the calling thread's entry frame, false when it
         *  fell back to the top activity. */
        final boolean byThreadEntry;
        /** True when the calling thread was created to run that entry frame:
         *  every frame under it is java.lang.Thread's own (Thread.run and its
         *  hidden runWith), or there is none (a guest Thread subclass's run, a
         *  native-attached thread). False for an executor/pool thread. */
        final boolean dedicated;
        Owner(String process, String by, boolean byThreadEntry, boolean dedicated) {
            this.process = process; this.by = by;
            this.byThreadEntry = byThreadEntry; this.dedicated = dedicated;
        }
    }

    /** The outermost guest frame's class and whether its thread is dedicated
     *  to it; see Owner.dedicated. */
    static final class Entry {
        final Class<?> cls;
        final boolean dedicated;
        Entry(Class<?> cls, boolean dedicated) { this.cls = cls; this.dedicated = dedicated; }
    }

    /** Package-private so debug/procexit_gate.sh can run the decision without
     *  an app. {@code appCl} is the loader that defines the guest's classes;
     *  {@code topActivity} the class name of the top activity, or null. */
    static Owner exitingProcess(Manifest m, ClassLoader appCl, boolean onMain,
                                String topActivity) {
        if (THREAD_OWNER && !onMain && m != null && appCl != null) {
            Entry entry = outermostGuestFrame(appCl);
            if (entry != null) {
                String p = componentProcessOf(m, entry.cls);
                if (p != null) {
                    return new Owner(p.isEmpty() ? null : p, entry.cls.getName(), true,
                            entry.dedicated);
                }
            }
        }
        String p = topActivity != null ? componentProcessOf(m, topActivity) : null;
        return new Owner(p == null || p.isEmpty() ? null : p,
                topActivity != null ? topActivity : "no activity", false, false);
    }

    /** The outermost frame on the current thread whose class the guest defined
     *  (by appCl, or by a loader the guest parented on it), or null. Thread.run
     *  and any executor plumbing are JDK / framework frames and are skipped for
     *  the attribution, but they decide Entry.dedicated: a frame under the
     *  guest one that is not java.lang.Thread's (ThreadPoolExecutor$Worker,
     *  FutureTask, AsyncTask's plumbing) means the thread is a shared worker
     *  that merely ran this component's task. StackWalker's default skips
     *  hidden frames (Thread.runWith, lambda forms), which is what we want. */
    static Entry outermostGuestFrame(final ClassLoader appCl) {
        try {
            return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> {
                    Class<?> last = null;
                    boolean dedicated = true;
                    for (java.util.Iterator<StackWalker.StackFrame> it = frames.iterator();
                         it.hasNext(); ) {
                        Class<?> c = it.next().getDeclaringClass();
                        if (definedByGuest(c, appCl)) {
                            last = c;
                            dedicated = true;       // only frames OUTER than this count
                        } else if (c != Thread.class) {
                            dedicated = false;
                        }
                    }
                    return last != null ? new Entry(last, dedicated) : null;
                });
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean definedByGuest(Class<?> c, ClassLoader appCl) {
        for (ClassLoader l = c.getClassLoader(); l != null; l = l.getParent()) {
            if (l == appCl) return true;
        }
        return false;
    }

    /** The process of the manifest component a class belongs to: the class
     *  itself, else its enclosing classes, innermost first (an anonymous
     *  Runnable, a nested Thread subclass), else its '$'-prefixes LONGEST first
     *  (D8's Foo$$ExternalSyntheticLambda0 and R8 output keep the name but drop
     *  the InnerClasses attribute that getEnclosingClass reads). Both walks go
     *  from the closest owner outwards, so a component nested in another
     *  component (Outer$InnerService$1) belongs to Outer$InnerService, not to
     *  Outer. Returns the resolved secondary process name, "" for a
     *  default-process component, or null when the class is not part of any
     *  manifest component. */
    static String componentProcessOf(Manifest m, Class<?> c) {
        if (m == null || c == null) return null;
        for (Class<?> k = c; k != null; k = enclosing(k)) {
            String p = componentProcessExact(m, k.getName());
            if (p != null) return p;
        }
        return componentProcessByPrefix(m, c.getName());
    }

    private static Class<?> enclosing(Class<?> k) {
        try { return k.getEnclosingClass(); } catch (Throwable t) { return null; }
    }

    static String componentProcessOf(Manifest m, String className) {
        if (m == null || className == null) return null;
        String p = componentProcessExact(m, className);
        return p != null ? p : componentProcessByPrefix(m, className);
    }

    /** The closest component among {@code name}'s '$'-prefixes, longest first:
     *  a.Outer$Inner$1 tries a.Outer$Inner, then a.Outer. Foo$$Lambda0 tries
     *  "Foo$" (never a component) and then Foo. */
    private static String componentProcessByPrefix(Manifest m, String name) {
        for (int d = name.lastIndexOf('$'); d > 0; d = name.lastIndexOf('$', d - 1)) {
            String p = componentProcessExact(m, name.substring(0, d));
            if (p != null) return p;
        }
        return null;
    }

    private static String componentProcessExact(Manifest m, String name) {
        String raw = m.componentProcesses.get(name);
        if (raw != null) {
            String p = resolveProcess(m, raw);
            return p != null ? p : "";
        }
        if (m.activities.containsKey(name) || m.services.containsKey(name)
                || m.receivers.containsKey(name)) {
            return "";
        }
        for (Manifest.ProviderEntry pe : m.providers) {
            if (name.equals(pe.name)) return "";
        }
        return null;
    }

    /** AOSP's android:process resolution, the same rule Boot.run applies when it
     *  publishes the foreign-process table: a leading ':' names a process
     *  private to the app, spelled {@code <pkg><value>}; anything else is a
     *  literal name; the package name itself is the default process. Null for
     *  the default process. */
    static String resolveProcess(Manifest m, String raw) {
        if (raw == null) return null;
        String p = raw.trim();
        if (p.isEmpty()) return null;
        if (p.charAt(0) == ':') p = m.packageName + p;
        return p.equals(m.packageName) ? null : p;
    }

    /** Activity class names the manifest puts in the given resolved process. */
    static java.util.Set<String> activitiesIn(Manifest m, String process) {
        java.util.Set<String> same = new java.util.HashSet<>();
        if (m == null) return same;
        for (java.util.Map.Entry<String, Manifest.ActivityEntry> e : m.activities.entrySet()) {
            if (process.equals(resolveProcess(m, e.getValue().processName))) same.add(e.getKey());
        }
        return same;
    }
}
