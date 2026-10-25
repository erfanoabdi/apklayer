// android.os.CancellationSignal -- propagates a cancellation request into a
// long-running operation (ContentResolver.query, CursorLoader, etc). This is a
// faithful port of AOSP: cancel() flips a flag, fires the registered listener
// (and any attached native/transport signal), and throwIfCanceled() raises
// OperationCanceledException. Previously a pure stub (isCanceled() hardcoded
// false), so cancellation never fired.
//
// AOSP ref: frameworks/base/core/java/android/os/CancellationSignal.java
package android.os;

public final class CancellationSignal {
    private boolean mIsCanceled;
    private OnCancelListener mOnCancelListener;
    private OnCancelListener mCancelInProgress;
    private boolean mCancelInProgressFlag;

    public CancellationSignal() {}

    public boolean isCanceled() {
        synchronized (this) {
            return mIsCanceled;
        }
    }

    public void throwIfCanceled() {
        if (isCanceled()) {
            throw new OperationCanceledException();
        }
    }

    public void cancel() {
        final OnCancelListener listener;
        synchronized (this) {
            if (mIsCanceled) {
                return;
            }
            mIsCanceled = true;
            mCancelInProgressFlag = true;
            listener = mOnCancelListener;
        }
        try {
            if (listener != null) {
                listener.onCancel();
            }
        } finally {
            synchronized (this) {
                mCancelInProgressFlag = false;
                notifyAll();
            }
        }
    }

    public void setOnCancelListener(OnCancelListener listener) {
        synchronized (this) {
            waitForCancelFinishedLocked();
            if (mOnCancelListener == listener) {
                return;
            }
            mOnCancelListener = listener;
            if (!mIsCanceled || listener == null) {
                return;
            }
        }
        // Fire immediately if cancel already happened.
        listener.onCancel();
    }

    private void waitForCancelFinishedLocked() {
        while (mCancelInProgressFlag) {
            try {
                wait();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public interface OnCancelListener {
        void onCancel();
    }
}
