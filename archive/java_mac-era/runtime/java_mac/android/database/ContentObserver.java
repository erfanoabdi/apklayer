// android.database.ContentObserver -- receives callbacks for changes
// to content. AOSP keeps the optional Handler the observer was
// constructed with; dispatchChange either posts onChange to that
// Handler's looper (so the callback runs on the observer's chosen
// thread) or, when no Handler was supplied, invokes onChange inline on
// the calling thread. The four onChange overloads chain downward so an
// observer that only overrides onChange(boolean) still receives every
// flavor of notification. The previous stub had a no-op dispatchChange
// (notifications never reached onChange) -- this restores real
// delivery so observer-driven Cursor / ContentResolver refresh fires.
// Source: frameworks/base/core/java/android/database/ContentObserver.java
package android.database;

import android.net.Uri;
import android.os.Handler;

import java.util.Collection;

public abstract class ContentObserver {
    /** The Handler the observer chose its callbacks to run on, or null
     *  to run them inline on the dispatch thread. Stored from the
     *  constructor. AOSP guards it with a Transport object for GC; we
     *  hold it directly (in-process, no Binder). */
    private final Handler mHandler;

    public ContentObserver(Handler handler) {
        mHandler = handler;
    }

    /** Returns true if this observer wants to receive notifications for
     *  changes it made to the content itself. Default false. */
    public boolean deliverSelfNotifications() {
        return false;
    }

    // ---- onChange overloads (override these) ----------------------
    // AOSP chains each overload down to the simpler one so subclasses
    // overriding only onChange(boolean) still get every notification.

    public void onChange(boolean selfChange) {}

    public void onChange(boolean selfChange, Uri uri) {
        onChange(selfChange);
    }

    public void onChange(boolean selfChange, Uri uri, int flags) {
        onChange(selfChange, uri);
    }

    public void onChange(boolean selfChange, Collection<Uri> uris, int flags) {
        for (Uri uri : uris) {
            onChange(selfChange, uri, flags);
        }
    }

    // ---- dispatchChange overloads (called by observables) ---------
    // Route through the Handler when present, else invoke inline.

    public final void dispatchChange(boolean selfChange) {
        dispatchChange(selfChange, null);
    }

    public final void dispatchChange(boolean selfChange, Uri uri) {
        dispatchChange(selfChange, uri, 0);
    }

    public final void dispatchChange(final boolean selfChange, final Uri uri, final int flags) {
        if (mHandler == null) {
            onChange(selfChange, uri, flags);
        } else {
            mHandler.post(new Runnable() {
                @Override public void run() {
                    onChange(selfChange, uri, flags);
                }
            });
        }
    }

    public final void dispatchChange(final boolean selfChange, final Collection<Uri> uris,
                                     final int flags) {
        if (mHandler == null) {
            onChange(selfChange, uris, flags);
        } else {
            mHandler.post(new Runnable() {
                @Override public void run() {
                    onChange(selfChange, uris, flags);
                }
            });
        }
    }
}
