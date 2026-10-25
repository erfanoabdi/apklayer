// android.content.AsyncTaskLoader<D> -- a Loader that runs its
// loadInBackground() on a worker thread and delivers the result on the
// caller's thread. CursorLoader extends this. We mirror AOSP's
// observable contract (background execution + main-thread delivery +
// cancellation) using a JDK ExecutorService for the worker and the main
// Looper Handler for delivery, instead of AOSP's internal ModernAsyncTask.
//
// AOSP ref:
//   frameworks/base/core/java/android/content/AsyncTaskLoader.java
package android.content;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;

public abstract class AsyncTaskLoader<D> extends Loader<D> {
    // AOSP's real AsyncTaskLoader.LoadTask is itself a nested AsyncTask
    // subclass (frameworks/base/core/java/android/content/AsyncTaskLoader.java,
    // verified from source) executed via AsyncTask.THREAD_POOL_EXECUTOR --
    // there is no separate loader-owned pool on real Android. Share the
    // SAME executor here instead of maintaining a second, independent
    // Executors.newCachedThreadPool() (unbounded: ThreadPoolExecutor(0,
    // Integer.MAX_VALUE, 60s, ...)) that duplicated AsyncTask's own
    // now-fixed unboundedness one class over. See
    // docs/efficiency-jvm-config_2026-07-24.md Finding 3.
    private static final Executor DEFAULT_EXECUTOR = AsyncTask.THREAD_POOL_EXECUTOR;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    volatile boolean mCancellingTask = false;
    volatile boolean mTaskRunning = false;
    long mUpdateThrottle;
    long mLastLoadCompleteTime = -10000;

    private volatile Thread mWorker;
    private volatile boolean mTaskCanceled = false;

    public AsyncTaskLoader(Context context) {
        super(context);
    }

    public void setUpdateThrottle(long delayMS) { mUpdateThrottle = delayMS; }

    @Override protected void onForceLoad() {
        super.onForceLoad();
        cancelLoad();
        startTask();
    }

    @Override protected boolean onCancelLoad() {
        if (mTaskRunning) {
            mCancellingTask = true;
            mTaskCanceled = true;
            Thread w = mWorker;
            if (w != null) w.interrupt();
            cancelLoadInBackground();
            return true;
        }
        return false;
    }

    private void startTask() {
        mTaskRunning = true;
        mCancellingTask = false;
        mTaskCanceled = false;
        DEFAULT_EXECUTOR.execute(() -> {
            mWorker = Thread.currentThread();
            D result = null;
            Throwable thrown = null;
            try {
                result = onLoadInBackground();
            } catch (Throwable t) {
                thrown = t;
            }
            final D finalResult = result;
            final boolean wasCanceled = mTaskCanceled;
            mMainHandler.post(() -> {
                mTaskRunning = false;
                mWorker = null;
                if (wasCanceled || mCancellingTask) {
                    mCancellingTask = false;
                    onCanceled(finalResult);
                    deliverCancellation();
                } else {
                    mLastLoadCompleteTime = android.os.SystemClock.uptimeMillis();
                    deliverResult(finalResult);
                }
            });
            if (thrown != null) {
                // AOSP lets the load throw; the result is simply not
                // delivered. We swallow to keep the worker pool healthy
                // (the exception already aborted this task's payload).
            }
        });
    }

    /** AOSP calls loadInBackground() from onLoadInBackground(); the
     *  indirection lets a subclass intercept. Default forwards. */
    protected D onLoadInBackground() {
        return loadInBackground();
    }

    /** Subclasses implement the actual data fetch here (off the main
     *  thread). */
    public abstract D loadInBackground();

    /** Called on the main thread if the load was canceled after
     *  loadInBackground produced a result, so the subclass can release
     *  it (e.g. close a Cursor). */
    public void onCanceled(D data) {}

    /** AOSP hook: interrupt a long-running loadInBackground. Default
     *  no-op; CursorLoader cancels its CancellationSignal here. */
    public void cancelLoadInBackground() {}

    public boolean isLoadInBackgroundCanceled() { return mCancellingTask; }

    public void waitForLoader() {
        Thread w = mWorker;
        if (w != null) {
            try { w.join(); } catch (InterruptedException ignored) {}
        }
    }
}
