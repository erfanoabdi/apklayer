package com.google.android.gms.tasks;

import java.util.ArrayList;
import java.util.List;

// com.google.android.gms.tasks.CancellationTokenSource -- produces a
// CancellationToken whose cancel() the owner triggers later. Mirrors
// play-services' observable contract: getToken() always returns the same token;
// cancel() latches the canceled state once and fires every registered
// OnTokenCanceledListener (and any later-registered listener fires immediately).
// Thread-safe; idempotent cancel(). createCancellationToken() is the documented
// alias some SDK versions expose.
public final class CancellationTokenSource {

    private final Object lock = new Object();
    private boolean canceled = false;
    private final List<OnTokenCanceledListener> listeners =
            new ArrayList<OnTokenCanceledListener>();

    private final CancellationToken token = new CancellationToken() {
        @Override public boolean isCancellationRequested() {
            synchronized (lock) { return canceled; }
        }
        @Override public CancellationToken onCanceledRequested(OnTokenCanceledListener l) {
            if (l == null) return this;
            boolean fireNow;
            synchronized (lock) {
                if (canceled) { fireNow = true; }
                else { listeners.add(l); fireNow = false; }
            }
            if (fireNow) l.onCanceled();
            return this;
        }
    };

    public CancellationTokenSource() {}

    public CancellationToken getToken() { return token; }

    /** Documented alias used by some play-services-tasks versions. */
    public CancellationToken createCancellationToken() { return token; }

    public void cancel() {
        List<OnTokenCanceledListener> toFire;
        synchronized (lock) {
            if (canceled) return;
            canceled = true;
            toFire = new ArrayList<OnTokenCanceledListener>(listeners);
            listeners.clear();
        }
        for (OnTokenCanceledListener l : toFire) {
            try { l.onCanceled(); } catch (RuntimeException ignored) {}
        }
    }
}
