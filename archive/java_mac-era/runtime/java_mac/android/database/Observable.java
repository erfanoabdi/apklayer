// android.database.Observable<T> -- AOSP base class for observer
// lists. RecyclerView.Adapter.AdapterDataObservable extends this
// (transitively via android.database.DataSetObservable). F-Droid's
// MainViewAdapter pulls it in during MainActivity.onCreate; without
// Observable in the classpath the ClassLoader throws
// NoClassDefFoundError when loading DataSetObservable.
//
// Source mirror: cs.android.com/.../frameworks/base/core/java/android/
// database/Observable.java. AOSP impl uses ArrayList + synchronized on
// the list itself; we follow the same shape so observer-add/remove
// semantics match what library code expects (especially the "already
// registered -> IllegalStateException" / "not registered -> ISE"
// contract that RecyclerView.AdapterDataObservable depends on).
package android.database;

import java.util.ArrayList;

public abstract class Observable<T> {
    protected final ArrayList<T> mObservers = new ArrayList<>();

    public void registerObserver(T observer) {
        if (observer == null) {
            throw new IllegalArgumentException("The observer is null.");
        }
        synchronized (mObservers) {
            if (mObservers.contains(observer)) {
                throw new IllegalStateException(
                    "Observer " + observer + " is already registered.");
            }
            mObservers.add(observer);
        }
    }

    public void unregisterObserver(T observer) {
        if (observer == null) {
            throw new IllegalArgumentException("The observer is null.");
        }
        synchronized (mObservers) {
            int index = mObservers.indexOf(observer);
            if (index == -1) {
                throw new IllegalStateException(
                    "Observer " + observer + " was not registered.");
            }
            mObservers.remove(index);
        }
    }

    public void unregisterAll() {
        synchronized (mObservers) { mObservers.clear(); }
    }
}
