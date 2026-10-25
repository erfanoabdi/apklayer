// android.database.ContentObservable -- the registry of
// ContentObservers a Cursor (or ContentResolver) keeps so it can fan a
// change notification out to all watchers. Extends the generic
// Observable<ContentObserver>; dispatchChange forwards to each
// observer's dispatchChange (which posts to the observer's Handler).
// Self-change notifications are filtered to observers that opted in via
// deliverSelfNotifications(). Mac.apk previously only had
// ContentObserver, not this observable container, so cursors had
// nowhere to register watchers; this restores the standard wiring.
// Source: frameworks/base/core/java/android/database/ContentObservable.java
package android.database;

import android.net.Uri;

public class ContentObservable extends Observable<ContentObserver> {

    /** @deprecated AOSP keeps this override for API compatibility; it
     *  simply registers like the base class. */
    @Deprecated
    public void registerObserver(ContentObserver observer) {
        super.registerObserver(observer);
    }

    /** @deprecated use dispatchChange(boolean, Uri) instead. */
    @Deprecated
    public void dispatchChange(boolean selfChange) {
        dispatchChange(selfChange, null);
    }

    /** Invokes dispatchChange on each registered observer, skipping
     *  observers that did not opt into self-change notifications when
     *  selfChange is true. */
    public void dispatchChange(boolean selfChange, Uri uri) {
        synchronized (mObservers) {
            for (ContentObserver observer : mObservers) {
                if (!selfChange || observer.deliverSelfNotifications()) {
                    observer.dispatchChange(selfChange, uri);
                }
            }
        }
    }

    /** @deprecated invokes onChange on each observer directly (no Uri,
     *  no self-change filtering). */
    @Deprecated
    public void notifyChange(boolean selfChange) {
        synchronized (mObservers) {
            for (ContentObserver observer : mObservers) {
                observer.onChange(selfChange);
            }
        }
    }
}
