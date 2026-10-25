// android.widget.Filter -- abstract async filter for adapter data. The
// adapter (ArrayAdapter / CursorAdapter) supplies performFiltering (run on
// a worker thread) and publishResults (run back on the main thread). Was a
// bare `extends Object` stub with no abstract methods; ArrayAdapter.getFilter
// and AutoCompleteTextView depend on the real contract. AOSP:
//   frameworks/base/core/java/android/widget/Filter.java
//   public abstract class Filter
package android.widget;

import android.os.Handler;
import android.os.Looper;

public abstract class Filter {
    // AOSP runs filtering on a dedicated worker thread (a HandlerThread)
    // and posts the results back to the calling looper via a result Handler.
    // We mirror that: spin a daemon worker for performFiltering, then post
    // publishResults onto the main looper.
    private final Object mLock = new Object();
    private Handler mResultHandler;

    public interface FilterListener {
        void onFilterComplete(int count);
    }

    public static class FilterResults {
        public FilterResults() {}
        public Object values;
        public int count;
    }

    public Filter() {}

    public final void filter(CharSequence constraint) {
        filter(constraint, null);
    }

    public final void filter(CharSequence constraint, FilterListener listener) {
        final CharSequence c = constraint != null ? constraint.toString() : null;
        final FilterListener fl = listener;
        synchronized (mLock) {
            if (mResultHandler == null) {
                mResultHandler = new Handler(Looper.getMainLooper());
            }
        }
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                final FilterResults results = performFiltering(c);
                Runnable publish = new Runnable() {
                    @Override public void run() {
                        publishResults(c, results);
                        if (fl != null) {
                            fl.onFilterComplete(results != null ? results.count : -1);
                        }
                    }
                };
                Handler h;
                synchronized (mLock) { h = mResultHandler; }
                if (h != null) h.post(publish); else publish.run();
            }
        }, "Filter");
        worker.setDaemon(true);
        worker.start();
    }

    protected abstract FilterResults performFiltering(CharSequence constraint);

    protected abstract void publishResults(CharSequence constraint,
            FilterResults results);

    public CharSequence convertResultToString(Object resultValue) {
        return resultValue == null ? "" : resultValue.toString();
    }
}
