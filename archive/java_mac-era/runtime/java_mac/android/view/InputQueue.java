// android.view.InputQueue -- the native-side input event queue a NativeActivity
// (or any window that calls Window.takeInputQueue) receives events through;
// native code reads it with the NDK's AInputQueue_* functions.
//
// Mirrors frameworks/base core/java/android/view/InputQueue.java
// (android16-release). The natives (core/jni/android_view_InputQueue.cpp) are
// implemented in runtime/translator/src/bionic_shim_nativeactivity.cpp, which
// also provides the NDK AInputQueue / AInputEvent / AMotionEvent / AKeyEvent
// functions guest code calls. The window creates the queue when it is added
// (Window.macapkOnWindowAddedForInput) and TouchDispatcher sends the window's
// events here before the view hierarchy, as ViewRootImpl's native input stages
// do.
//
// One Mac.apk-specific piece: macapkPostFinish. AOSP's native queue posts a
// MSG_FINISH_INPUT to the creating thread's native Looper and calls
// finishInputEvent there; ours calls macapkPostFinish from whichever thread
// ran AInputQueue_finishEvent, and it posts finishInputEvent to the creating
// thread's Looper.
package android.view;

import android.os.Looper;
import android.os.MessageQueue;
import android.util.LongSparseArray;
import android.util.Pools.Pool;
import android.util.Pools.SimplePool;

import dalvik.system.CloseGuard;

import java.lang.ref.WeakReference;

public final class InputQueue {
    private final LongSparseArray<ActiveInputEvent> mActiveEventArray =
            new LongSparseArray<ActiveInputEvent>(20);
    private final Pool<ActiveInputEvent> mActiveInputEventPool =
            new SimplePool<ActiveInputEvent>(20);

    private final CloseGuard mCloseGuard = CloseGuard.get();

    private long mPtr;

    /** The creating thread's Looper, where finishInputEvent runs. */
    private final android.os.Handler mMacapkFinishHandler;

    private static native long nativeInit(WeakReference<InputQueue> weakQueue,
            MessageQueue messageQueue);
    private static native long nativeSendKeyEvent(long ptr, KeyEvent e, boolean preDispatch);
    private static native long nativeSendMotionEvent(long ptr, MotionEvent e);
    private static native void nativeDispose(long ptr);

    /** @hide */
    public InputQueue() {
        Looper looper = Looper.myLooper();
        mMacapkFinishHandler = new android.os.Handler(
                looper != null ? looper : Looper.getMainLooper());
        mPtr = nativeInit(new WeakReference<InputQueue>(this), Looper.myQueue());

        mCloseGuard.open("InputQueue.dispose");
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void finalize() throws Throwable {
        try {
            dispose(true);
        } finally {
            super.finalize();
        }
    }

    /** @hide */
    public void dispose() {
        dispose(false);
    }

    /** @hide */
    public void dispose(boolean finalized) {
        if (mCloseGuard != null) {
            if (finalized) {
                mCloseGuard.warnIfOpen();
            }
            mCloseGuard.close();
        }

        if (mPtr != 0) {
            nativeDispose(mPtr);
            mPtr = 0;
        }
    }

    /** @hide */
    public long getNativePtr() {
        return mPtr;
    }

    /** @hide */
    public void sendInputEvent(InputEvent e, Object token, boolean predispatch,
            FinishedInputEventCallback callback) {
        ActiveInputEvent event = obtainActiveInputEvent(token, callback);
        long id;
        if (e instanceof KeyEvent) {
            id = nativeSendKeyEvent(mPtr, (KeyEvent) e, predispatch);
        } else {
            id = nativeSendMotionEvent(mPtr, (MotionEvent) e);
        }
        synchronized (mActiveEventArray) {
            mActiveEventArray.put(id, event);
        }
    }

    private void finishInputEvent(long id, boolean handled) {
        ActiveInputEvent e = null;
        synchronized (mActiveEventArray) {
            int index = mActiveEventArray.indexOfKey(id);
            if (index >= 0) {
                e = mActiveEventArray.valueAt(index);
                mActiveEventArray.removeAt(index);
            }
        }
        if (e != null) {
            e.mCallback.onFinishedInputEvent(e.mToken, handled);
            recycleActiveInputEvent(e);
        }
    }

    /** Called from native (AInputQueue_finishEvent, any thread). */
    private void macapkPostFinish(final long id, final boolean handled) {
        mMacapkFinishHandler.post(() -> finishInputEvent(id, handled));
    }

    private ActiveInputEvent obtainActiveInputEvent(Object token,
            FinishedInputEventCallback callback) {
        ActiveInputEvent e;
        synchronized (mActiveInputEventPool) {
            e = mActiveInputEventPool.acquire();
        }
        if (e == null) {
            e = new ActiveInputEvent();
        }
        e.mToken = token;
        e.mCallback = callback;
        return e;
    }

    private void recycleActiveInputEvent(ActiveInputEvent e) {
        e.recycle();
        synchronized (mActiveInputEventPool) {
            mActiveInputEventPool.release(e);
        }
    }

    private final class ActiveInputEvent {
        public Object mToken;
        public FinishedInputEventCallback mCallback;

        public void recycle() {
            mToken = null;
            mCallback = null;
        }
    }

    /**
     * Interface to receive notification of when an InputQueue is associated
     * and dissociated with a thread.
     */
    public static interface Callback {
        /**
         * Called when the given InputQueue is now associated with the
         * thread making this call, so it can start receiving events from it.
         */
        void onInputQueueCreated(InputQueue queue);

        /**
         * Called when the given InputQueue is no longer associated with
         * the thread and thus not dispatching events.
         */
        void onInputQueueDestroyed(InputQueue queue);
    }

    /** @hide */
    public static interface FinishedInputEventCallback {
        void onFinishedInputEvent(Object token, boolean handled);
    }
}
