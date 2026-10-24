// android.content.AsyncQueryHandler -- runs ContentResolver query/insert/
// update/delete off the caller's thread and posts the result back to it.
//
// This is now the REAL AOSP design, not a synchronous shortcut. The previous
// Mac.apk version ran each operation inline on the calling thread, which had
// two consequences worth stating because they are exactly the failure mode the
// no-shortcuts rule exists to catch:
//   1. cancelOperation() could not mean anything -- nothing was ever queued,
//      so there was nothing to cancel. Apps that start a query per keystroke
//      and cancel the previous one (the standard contacts-autocomplete
//      pattern) got every query executed.
//   2. a slow provider blocked the UI thread, where on Android it would not.
//
// Source: frameworks/base/core/java/android/content/AsyncQueryHandler.java
// (android11-release). Structure mirrored: one PROCESS-WIDE worker
// HandlerThread ("AsyncQueryWorker", AOSP line 129) shared by every instance,
// messages keyed by `what = token` so removeMessages(token) cancels (AOSP
// lines 167-168 and 193-194), and the reply posted back to `this` so the
// on*Complete callbacks land on the thread that created the handler.
package android.content;

import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;

public abstract class AsyncQueryHandler extends Handler {
    private static final String TAG = "AsyncQuery";

    private static final int EVENT_ARG_QUERY = 1;
    private static final int EVENT_ARG_INSERT = 2;
    private static final int EVENT_ARG_UPDATE = 3;
    private static final int EVENT_ARG_DELETE = 4;

    // WeakReference, as AOSP does: the handler outlives the Activity that
    // owns the resolver often enough that a strong ref leaks it.
    /* package */ final WeakReference<ContentResolver> mResolver;

    private static Looper sLooper = null;

    private Handler mWorkerThreadHandler;

    protected static final class WorkerArgs {
        public Uri uri;
        public Handler handler;
        public String[] projection;
        public String selection;
        public String[] selectionArgs;
        public String orderBy;
        public Object result;
        public Object cookie;
        public ContentValues values;
    }

    protected class WorkerHandler extends Handler {
        public WorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            final ContentResolver resolver = mResolver.get();
            if (resolver == null) return;

            WorkerArgs args = (WorkerArgs) msg.obj;

            int token = msg.what;
            int event = msg.arg1;

            switch (event) {
                case EVENT_ARG_QUERY:
                    Cursor cursor;
                    try {
                        cursor = resolver.query(args.uri, args.projection,
                                args.selection, args.selectionArgs,
                                args.orderBy);
                        // AOSP: getCount() fills the cursor window here, so
                        // the first access back on the UI thread is cheap.
                        if (cursor != null) {
                            cursor.getCount();
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Exception thrown during handling EVENT_ARG_QUERY", e);
                        cursor = null;
                    }

                    args.result = cursor;
                    break;

                case EVENT_ARG_INSERT:
                    try {
                        args.result = resolver.insert(args.uri, args.values);
                    } catch (Exception e) {
                        Log.w(TAG, "Exception thrown during handling EVENT_ARG_INSERT", e);
                        args.result = null;
                    }
                    break;

                case EVENT_ARG_UPDATE:
                    try {
                        args.result = resolver.update(args.uri, args.values, args.selection,
                                args.selectionArgs);
                    } catch (Exception e) {
                        Log.w(TAG, "Exception thrown during handling EVENT_ARG_UPDATE", e);
                        args.result = Integer.valueOf(0);
                    }
                    break;

                case EVENT_ARG_DELETE:
                    try {
                        args.result = resolver.delete(args.uri, args.selection, args.selectionArgs);
                    } catch (Exception e) {
                        Log.w(TAG, "Exception thrown during handling EVENT_ARG_DELETE", e);
                        args.result = Integer.valueOf(0);
                    }
                    break;
            }

            // Pass the original token back on top of the event value in arg1.
            Message reply = args.handler.obtainMessage(token);
            reply.obj = args;
            reply.arg1 = msg.arg1;

            reply.sendToTarget();
        }
    }

    public AsyncQueryHandler(ContentResolver cr) {
        super();
        mResolver = new WeakReference<ContentResolver>(cr);
        synchronized (AsyncQueryHandler.class) {
            if (sLooper == null) {
                HandlerThread thread = new HandlerThread("AsyncQueryWorker");
                thread.start();

                sLooper = thread.getLooper();
            }
        }
        mWorkerThreadHandler = createHandler(sLooper);
    }

    protected Handler createHandler(Looper looper) {
        return new WorkerHandler(looper);
    }

    public void startQuery(int token, Object cookie, Uri uri,
            String[] projection, String selection, String[] selectionArgs,
            String orderBy) {
        // The token is the message `what` so cancelOperation() can find it.
        Message msg = mWorkerThreadHandler.obtainMessage(token);
        msg.arg1 = EVENT_ARG_QUERY;

        WorkerArgs args = new WorkerArgs();
        args.handler = this;
        args.uri = uri;
        args.projection = projection;
        args.selection = selection;
        args.selectionArgs = selectionArgs;
        args.orderBy = orderBy;
        args.cookie = cookie;
        msg.obj = args;

        mWorkerThreadHandler.sendMessage(msg);
    }

    /**
     * Attempts to cancel an operation that has not already started. Per AOSP
     * there is NO guarantee: an operation already dequeued by the worker will
     * still call back. If several operations share a token they are all
     * cancelled. AOSP line 193.
     */
    public final void cancelOperation(int token) {
        mWorkerThreadHandler.removeMessages(token);
    }

    public void startInsert(int token, Object cookie, Uri uri, ContentValues initialValues) {
        Message msg = mWorkerThreadHandler.obtainMessage(token);
        msg.arg1 = EVENT_ARG_INSERT;

        WorkerArgs args = new WorkerArgs();
        args.handler = this;
        args.uri = uri;
        args.cookie = cookie;
        args.values = initialValues;
        msg.obj = args;

        mWorkerThreadHandler.sendMessage(msg);
    }

    public void startUpdate(int token, Object cookie, Uri uri, ContentValues values,
            String selection, String[] selectionArgs) {
        Message msg = mWorkerThreadHandler.obtainMessage(token);
        msg.arg1 = EVENT_ARG_UPDATE;

        WorkerArgs args = new WorkerArgs();
        args.handler = this;
        args.uri = uri;
        args.cookie = cookie;
        args.values = values;
        args.selection = selection;
        args.selectionArgs = selectionArgs;
        msg.obj = args;

        mWorkerThreadHandler.sendMessage(msg);
    }

    public void startDelete(int token, Object cookie, Uri uri, String selection,
            String[] selectionArgs) {
        Message msg = mWorkerThreadHandler.obtainMessage(token);
        msg.arg1 = EVENT_ARG_DELETE;

        WorkerArgs args = new WorkerArgs();
        args.handler = this;
        args.uri = uri;
        args.cookie = cookie;
        args.selection = selection;
        args.selectionArgs = selectionArgs;
        msg.obj = args;

        mWorkerThreadHandler.sendMessage(msg);
    }

    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {}

    protected void onInsertComplete(int token, Object cookie, Uri uri) {}

    protected void onUpdateComplete(int token, Object cookie, int result) {}

    protected void onDeleteComplete(int token, Object cookie, int result) {}

    @Override
    public void handleMessage(Message msg) {
        WorkerArgs args = (WorkerArgs) msg.obj;

        int token = msg.what;
        int event = msg.arg1;

        switch (event) {
            case EVENT_ARG_QUERY:
                onQueryComplete(token, args.cookie, (Cursor) args.result);
                break;

            case EVENT_ARG_INSERT:
                onInsertComplete(token, args.cookie, (Uri) args.result);
                break;

            case EVENT_ARG_UPDATE:
                onUpdateComplete(token, args.cookie, (Integer) args.result);
                break;

            case EVENT_ARG_DELETE:
                onDeleteComplete(token, args.cookie, (Integer) args.result);
                break;
        }
    }
}
