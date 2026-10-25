// android.hardware.camera2.CameraDispatch -- NOT an AOSP class. Package-private
// so it never appears in the API surface an app resolves against.
//
// WHY IT EXISTS. camera2 delivers every callback on a caller-supplied
// destination, and the app picks the destination two different ways depending
// on which overload it called: a Handler (the API 21 overloads) or an Executor
// (the API 28 ones). Every callback site in this package would otherwise carry
// the same `if (handler != null) handler.post(r) else executor.execute(r)`
// branch, and the branch that matters -- what happens when the app passed
// NEITHER -- would be re-decided at each site.
//
// AOSP centralises the same decision in
// frameworks/base/core/java/android/hardware/camera2/impl/CameraDeviceImpl.java:
//
//   static Handler checkHandler(Handler handler) {
//       if (handler == null) {
//           Looper looper = Looper.myLooper();
//           if (looper == null) {
//               throw new IllegalArgumentException(
//                   "No handler given, and current thread has no looper!");
//           }
//           handler = new Handler(looper);
//       }
//       return handler;
//   }
//
// That throw is behaviour, not defensive coding: an app that calls
// createCaptureSession from a bare worker thread with a null Handler gets an
// IllegalArgumentException on real Android, and it gets one here, naming the
// same cause. Silently running the callback on whatever thread produced the
// frame would be strictly worse -- the app's callback would touch its views
// off the main thread and fail somewhere unrelated.
package android.hardware.camera2;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;

final class CameraDispatch {

    private final Handler mHandler;
    private final Executor mExecutor;

    private CameraDispatch(Handler handler, Executor executor) {
        mHandler = handler;
        mExecutor = executor;
    }

    /** The Handler overloads' destination. A null Handler means "this thread's
     *  Looper", and no Looper is an error -- see the AOSP quote above. */
    static CameraDispatch of(Handler handler) {
        if (handler == null) {
            Looper looper = Looper.myLooper();
            if (looper == null) {
                throw new IllegalArgumentException(
                        "No handler given, and current thread has no looper!");
            }
            handler = new Handler(looper);
        }
        return new CameraDispatch(handler, null);
    }

    /** The Executor overloads' destination. AOSP rejects a null Executor at the
     *  entry point (CameraManager.java:1348-1350 for openCamera), so by the
     *  time we get here it is non-null. */
    static CameraDispatch of(Executor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor was null");
        }
        return new CameraDispatch(null, executor);
    }

    /**
     * Post a callback to the app's chosen destination.
     *
     * Never runs inline, even when already on the destination thread: camera2's
     * contract is that a callback arrives on the Handler/Executor, and an app
     * that (for instance) calls session.close() from inside onConfigured would
     * otherwise re-enter our own state machine underneath the lock that is
     * calling it.
     *
     * Throwables from app code are caught and logged rather than allowed to
     * kill the pump thread or the Handler's loop. On real Android an exception
     * thrown from a camera callback lands on the app's own Looper and crashes
     * the app; ours would instead take down the frame pump and stop every
     * OTHER callback, turning one app bug into "the camera froze". Logging it
     * with its stack keeps the failure attributable.
     */
    void run(Runnable r) {
        Runnable guarded = () -> {
            try {
                r.run();
            } catch (Throwable t) {
                CameraBackend.warn("exception thrown from an app camera callback:");
                t.printStackTrace();
            }
        };
        if (mHandler != null) {
            mHandler.post(guarded);
        } else {
            mExecutor.execute(guarded);
        }
    }
}
