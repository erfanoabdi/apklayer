// android.database.DataSetObservable -- specialization of Observable
// for DataSetObserver. Used by adapter classes (BaseAdapter,
// RecyclerView.Adapter) to notify subscribers of data changes.
package android.database;

public class DataSetObservable extends Observable<DataSetObserver> {
    /** Tell every observer the data set changed; they should refresh. */
    public void notifyChanged() {
        synchronized (mObservers) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onChanged();
            }
        }
    }

    /** Tell every observer the data is no longer valid (cursor closed,
     *  adapter detached, etc.) so they release references. */
    public void notifyInvalidated() {
        synchronized (mObservers) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onInvalidated();
            }
        }
    }
}
