package android.view;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;

/**
 * AOSP-faithful android.view.PixelCopy. Copies pixels from a Surface / Window /
 * SurfaceView into a destination Bitmap asynchronously. Mac.apk has no GPU
 * back-buffer readback path (Unity/ANGLE present to their own layers, which read
 * back black per the verification invariants), so a faithful copy isn't
 * possible; we validate the request the same way AOSP does, then report
 * ERROR_SOURCE_NO_DATA on the listener's thread so callers' callbacks always
 * fire instead of hanging. Mirrors
 * frameworks/base/graphics/java/android/view/PixelCopy.java.
 */
public final class PixelCopy {
    public static final int SUCCESS = 0;
    public static final int ERROR_UNKNOWN = 1;
    public static final int ERROR_TIMEOUT = 2;
    public static final int ERROR_SOURCE_NO_DATA = 3;
    public static final int ERROR_SOURCE_INVALID = 4;
    public static final int ERROR_DESTINATION_INVALID = 5;

    public interface OnPixelCopyFinishedListener {
        void onPixelCopyFinished(int copyResult);
    }

    private PixelCopy() {}

    public static void request(SurfaceView source, Bitmap dest,
            OnPixelCopyFinishedListener listener, Handler listenerThread) {
        request(source.getHolder().getSurface(), dest, listener, listenerThread);
    }

    public static void request(SurfaceView source, Rect srcRect, Bitmap dest,
            OnPixelCopyFinishedListener listener, Handler listenerThread) {
        request(source.getHolder().getSurface(), srcRect, dest, listener, listenerThread);
    }

    public static void request(Surface source, Bitmap dest,
            OnPixelCopyFinishedListener listener, Handler listenerThread) {
        request(source, null, dest, listener, listenerThread);
    }

    public static void request(Surface source, Rect srcRect, Bitmap dest,
            OnPixelCopyFinishedListener listener, Handler listenerThread) {
        validateBitmapDest(dest);
        if (source == null || !source.isValid()) {
            throw new IllegalArgumentException("Surface isn't valid, source.isValid() == false");
        }
        deliver(listener, listenerThread, ERROR_SOURCE_NO_DATA);
    }

    public static void request(Window source, Bitmap dest,
            OnPixelCopyFinishedListener listener, Handler listenerThread) {
        request(source, null, dest, listener, listenerThread);
    }

    public static void request(Window source, Rect srcRect, Bitmap dest,
            OnPixelCopyFinishedListener listener, Handler listenerThread) {
        validateBitmapDest(dest);
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        deliver(listener, listenerThread, ERROR_SOURCE_NO_DATA);
    }

    private static void validateBitmapDest(Bitmap bitmap) {
        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap cannot be null");
        }
        if (bitmap.isRecycled()) {
            throw new IllegalArgumentException("Bitmap is recycled");
        }
    }

    private static void deliver(final OnPixelCopyFinishedListener listener,
            Handler handler, final int result) {
        if (listener == null) return;
        Runnable r = new Runnable() {
            @Override public void run() { listener.onPixelCopyFinished(result); }
        };
        if (handler != null) {
            handler.post(r);
        } else {
            r.run();
        }
    }

    // ---- the API-34 Request/Result form ---------------------------------------
    // Source: frameworks/base graphics/java/android/view/PixelCopy.java
    // (android16-release). Same outcome as the listener overloads above -- there
    // is no GPU back-buffer readback path here (see the class header) -- but the
    // result travels as a Result object on an Executor rather than a status int
    // on a Handler, and the request validation is the same.

    /** AOSP PixelCopy.Request: a built copy request naming the source, the
     *  optional source rect and the destination Bitmap. */
    public static final class Request {
        private final Surface mSurface;
        private final Window mWindow;
        private final Rect mSourceRect;
        private final Bitmap mDestinationBitmap;

        private Request(Surface surface, Window window, Rect sourceRect, Bitmap dest) {
            mSurface = surface;
            mWindow = window;
            mSourceRect = sourceRect;
            mDestinationBitmap = dest;
        }

        public Rect getSourceRect() { return mSourceRect; }
        public Bitmap getDestinationBitmap() { return mDestinationBitmap; }
        /** @hide */ Surface macapkSurface() { return mSurface; }
        /** @hide */ Window macapkWindow() { return mWindow; }

        public static final class Builder {
            private final Surface mSurface;
            private final Window mWindow;
            private Rect mSourceRect;
            private Bitmap mDestinationBitmap;

            private Builder(Surface surface, Window window) {
                mSurface = surface;
                mWindow = window;
            }

            /** AOSP PixelCopy.Request.Builder.ofSurface(Surface). */
            public static Builder ofSurface(Surface source) {
                if (source == null || !source.isValid()) {
                    throw new IllegalArgumentException(
                            "Surface isn't valid, source.isValid() == false");
                }
                return new Builder(source, null);
            }
            /** AOSP PixelCopy.Request.Builder.ofSurface(SurfaceView). */
            public static Builder ofSurface(SurfaceView source) {
                if (source == null) throw new IllegalArgumentException("source is null");
                return ofSurface(source.getHolder().getSurface());
            }
            /** AOSP PixelCopy.Request.Builder.ofWindow(Window). */
            public static Builder ofWindow(Window source) {
                if (source == null) throw new IllegalArgumentException("source is null");
                return new Builder(null, source);
            }
            /** AOSP PixelCopy.Request.Builder.ofWindow(View): resolves the
             *  view's window, and AOSP throws when the view is not attached to
             *  one. */
            public static Builder ofWindow(View source) {
                if (source == null || !source.isAttachedToWindow()) {
                    throw new IllegalArgumentException(
                            "View must be attached to a window");
                }
                android.content.Context c = source.getContext();
                Window w = null;
                while (c instanceof android.content.ContextWrapper) {
                    if (c instanceof android.app.Activity) {
                        w = ((android.app.Activity) c).getWindow();
                        break;
                    }
                    c = ((android.content.ContextWrapper) c).getBaseContext();
                }
                if (w == null) {
                    throw new IllegalArgumentException(
                            "Unable to resolve window from view: " + source);
                }
                return new Builder(null, w);
            }

            public Builder setSourceRect(Rect srcRect) { mSourceRect = srcRect; return this; }
            public Builder setDestinationBitmap(Bitmap destinationBitmap) {
                if (destinationBitmap != null) validateBitmapDest(destinationBitmap);
                mDestinationBitmap = destinationBitmap;
                return this;
            }
            public Request build() {
                return new Request(mSurface, mWindow, mSourceRect, mDestinationBitmap);
            }
        }
    }

    /** AOSP PixelCopy.Result: the status plus the bitmap that was written. */
    public static final class Result {
        private final int mStatus;
        private final Bitmap mBitmap;
        private Result(int status, Bitmap bitmap) { mStatus = status; mBitmap = bitmap; }
        public int getStatus() { return mStatus; }
        /** AOSP: throws IllegalStateException unless getStatus() == SUCCESS. */
        public Bitmap getBitmap() {
            if (mStatus != SUCCESS) {
                throw new IllegalStateException("Copy request didn't succeed, status = "
                        + mStatus);
            }
            return mBitmap;
        }
    }

    /** AOSP PixelCopy.request(Request, Executor, Consumer&lt;Result&gt;) (API 34).
     *  Validates as AOSP does, then reports ERROR_SOURCE_NO_DATA on the caller's
     *  Executor -- the same status the listener overloads above report, and for
     *  the same reason (no readback path). The callback ALWAYS fires, so a
     *  caller never hangs. */
    public static void request(Request request, java.util.concurrent.Executor callbackExecutor,
            java.util.function.Consumer<Result> listener) {
        if (request == null) throw new IllegalArgumentException("request is null");
        if (callbackExecutor == null) {
            throw new IllegalArgumentException("callbackExecutor is null");
        }
        if (listener == null) throw new IllegalArgumentException("listener is null");
        Bitmap dest = request.getDestinationBitmap();
        if (dest != null) validateBitmapDest(dest);
        final Result result = new Result(ERROR_SOURCE_NO_DATA, dest);
        callbackExecutor.execute(() -> listener.accept(result));
    }
}
