package android.window;
import android.view.AttachedSurfaceControl;
/** Groups several surfaces so they present atomically. Mac.apk composites a
 *  single window, so there is nothing to synchronize: add() invokes the
 *  when-ready callback immediately and markSyncReady is a no-op. API 34.
 *  Source: frameworks/base/core/java/android/window/SurfaceSyncGroup.java */
public final class SurfaceSyncGroup {
    public SurfaceSyncGroup(String name) { }
    public boolean add(AttachedSurfaceControl surfaceControl, Runnable runnable) {
        if (runnable != null) { try { runnable.run(); } catch (Throwable ignored) { } }
        return true;
    }
    /**
     * AOSP overload taking an embedded surface package. Same contract as the
     * AttachedSurfaceControl form: nothing to synchronize on a single
     * composited window, so the when-ready callback fires immediately.
     */
    public boolean add(android.view.SurfaceControlViewHost.SurfacePackage surfacePackage,
            Runnable runnable) {
        if (runnable != null) { try { runnable.run(); } catch (Throwable ignored) { } }
        return true;
    }

    /**
     * AOSP: "Add a Transaction to this sync group. This allows the caller to
     * provide other info that should be synced with the other transactions in
     * this sync group."
     *
     * With no sync group to defer to, the transaction must be APPLIED rather
     * than dropped: AOSP guarantees the transaction takes effect when the
     * group presents, and a group that presents immediately means it takes
     * effect immediately. Dropping it would silently lose whatever surface
     * change the caller staged -- the visible-nothing-happens failure this
     * class exists to avoid.
     */
    public void addTransaction(android.view.SurfaceControl.Transaction transaction) {
        if (transaction != null) {
            transaction.apply();
        }
    }

    public void markSyncReady() { }
}
