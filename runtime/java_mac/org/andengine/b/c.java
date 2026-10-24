// RETIRED 2026-09-27 (MACAPK_RETIRE_ENGINE_SHADOWS, default ON): an app that
// bundles its own org.andengine.b.c now gets ITS OWN class (AppClassLoader
// defines the app's copy for any class carrying the retired-shadow marker
// below), exactly as on Android, where nothing on the boot classpath shadows
// it. The deadlock this file worked around was never the lock's: our
// GLSurfaceView.onPause/onResume were no-ops, so the render thread kept
// entering Engine.onDrawFrame after the UpdateThread exited. GLSurfaceView now
// honours AOSP's pause/exit contract (MACAPK_GLSV_PAUSE), and the app's own
// EngineLock pings update/draw 1:1 under it. The header below is the
// historical rationale (its deadlock claim was refuted offline:
// debug/geh_andengine-lock-shadow_gate.sh). Kept for one soak release so
// MACAPK_RETIRE_ENGINE_SHADOWS=0 or MACAPK_GLSV_PAUSE=0 (the pause knob brings
// it back too: the app's own lock needs the pause contract) can restore it;
// then DELETE this file (that moves FRAMEWORK_ID, so batch it with another
// framework-shape change). Tracked in Current_State "Per-APK patches".
// docs/andengine-lock-shadow-retired_2026-09-27.md.
//
// Shadow AndEngine's "engine state lock" class. The original
// (org.andengine.b.c) is a ReentrantLock subclass with an AtomicBoolean
// + Condition for cross-thread synchronization between AndEngine's
// UpdateThread and its GL thread:
//   c(): block until AtomicBoolean == true (the update thread signals it)
//   d(): block until AtomicBoolean == false
//   a(): set true + signalAll
//   b(): set false + signalAll
//
// On Android the AtomicBoolean is flipped by the GL thread between
// frames, so c()/d() pace the update thread at the renderer's rate.
// In v2 the renderer runs on the Cocoa main thread; no GL thread is
// ever flipping the AtomicBoolean, so the original waits would block
// the UpdateThread forever and Engine.a(GLState) on the render side
// would deadlock.
//
// Earlier the shadow no-op'd c()/d(). That removed the deadlock but
// turned the UpdateThread's running-branch (Engine.m()) into a tight
// CPU loop that holds this lock for bursts back-to-back, starving the
// main thread's render that contends for the same lock. Net effect:
// frame times jittered between 25ms and 1.3s on the same scene.
//
// New behavior: c()/d() briefly drop the lock, sleep a millisecond,
// and reacquire. That gives the main thread a fair shot at the lock
// once per update tick without changing AndEngine semantics in any
// way that affects correctness.
//
// Why everything here is `public` instead of package-private (which is
// what the original obfuscated AndEngine class uses):
//
// This shim ships in android-framework.jar, which the JVM loads through
// the system ('app') classloader. The rest of org.andengine.b.* lives
// in the APK's own converted classes.jar and is loaded through
// AppClassLoader. The JVM
// identifies a "runtime package" by the (package-name, classloader)
// pair; classes that share a package name but live in different
// classloaders are in DIFFERENT runtime packages. Package-private
// access fails across runtime packages with IllegalAccessError, which
// is exactly what we hit on org.andengine.b.a -> c.c() in
// onDrawFrame.
//
// Widening to `public` keeps the bytecode binary-compatible (the
// obfuscated call site is invokevirtual c.c()V, which doesn't care
// whether the target is package-private or public) and bypasses the
// cross-classloader access check entirely. The methods are still only
// called from inside org.andengine.b, so the looser visibility makes
// no observable difference at the API surface.

package org.andengine.b;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Deprecated(since = com.macapk.loader.AppClassLoader.RETIRED_SHADOW, forRemoval = true)
public class c extends ReentrantLock {
    public final Condition a;
    public final AtomicBoolean b;

    public c(boolean fair) {
        super(fair);
        this.a = newCondition();
        this.b = new AtomicBoolean(true);
        com.macapk.loader.NativeLog.i("AndEngineLockShadow",
            "loaded -- pacing UpdateThread via unlock/sleep/relock");
    }

    public void a() { b.set(true); }
    public void b() { b.set(false); }

    // Yield the lock long enough for the render thread to take it.
    // The render thread acquires the lock once per frame; a 1ms yield
    // is plenty given a 16ms frame budget. Without this the
    // UpdateThread spins, contends, and the render-thread frame time
    // bounces between 25ms and >1s.
    private void yieldLock() {
        if (isHeldByCurrentThread()) {
            unlock();
            try { Thread.sleep(1); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            lock();
        } else {
            try { Thread.sleep(1); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
    }

    public void c() { yieldLock(); }
    public void d() { yieldLock(); }
}
