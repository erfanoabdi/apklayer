package android.view;

import android.content.ClipData;
import android.content.ClipDescription;

/**
 * An in-window drag-and-drop session: the parts of AOSP's system-side DragState
 * (frameworks/base/services/core/java/com/android/server/wm/DragState.java) and
 * of ViewRootImpl's drag handling (handleDragEvent / setDragFocus, android14
 * ViewRootImpl.java :7955-8062) that a one-window, one-process host needs.
 *
 * WHY IT EXISTS (2026-09-29). View.startDragAndDrop returned false and
 * setOnDragListener was a no-op, so every app that moves things with the
 * platform drag-and-drop API could pick nothing up. Owner report: Anuto TD
 * "Cannot place turrets" -- its TowerViewControl.onTouch calls
 * TowerView.startDrag(ClipData, DragShadowBuilder, tower, 0) and GameView
 * places the tower from its OnDragListener's ACTION_DROP.
 *
 * THE AOSP SEQUENCE this reproduces, in order:
 *  1. View.startDragAndDrop (View.java :27990-28105): false when not attached;
 *     the shadow builder's onProvideShadowMetrics must not report a negative
 *     size (IllegalStateException), a zero size throws from targetSdk P on and
 *     is made 1x1 before it; the shadow is drawn by onDrawShadow; the system
 *     then refuses the drag (performDrag returns null -> false) when another
 *     drag is in progress or when the touch cannot be transferred, i.e. no
 *     pointer is down (DragDropController.performDrag, transferTouchFocus);
 *     on success the local state is cached for every later event
 *     (ViewRootImpl.setLocalDragState).
 *  2. The pointer now belongs to the drag: the source window's touch stream is
 *     CANCELLED (InputDispatcher::transferTouchFocus synthesizes the cancel).
 *  3. ACTION_DRAG_STARTED (position = the touch point, ClipDescription, no
 *     ClipData) to the whole window, which records the interested views
 *     (ViewGroup.dispatchDragEvent).
 *  4. Each pointer move: ACTION_DRAG_LOCATION to the frontmost interested view
 *     under the point; a change of that view sends EXITED / ENTERED
 *     (ViewRootImpl.setDragFocus).
 *  5. Pointer up: ACTION_DROP (with the ClipData) to the view under the point;
 *     its return value is the drop result (reportDropResult).
 *  6. ACTION_DRAG_ENDED (getResult() = that result, no ClipDescription) to every
 *     interested view, then the session is gone.
 *
 * THREADS. AOSP delivers every drag event on the UI thread
 * (ViewRootImpl.dispatchDragEvent -> mHandler MSG_DISPATCH_DRAG_EVENT ->
 * handleDragEvent). The pointer here can arrive on the Cocoa thread (a tree
 * whose renderer OS-main drives keeps host input there, TouchDispatcher.hostInputTree), so every drag event
 * hops to the main looper when one is running and we are not on it
 * (runOnUi); the STARTED post is queued first, so order is kept. The
 * once-only transitions (STARTED, end) are additionally guarded by the class
 * monitor, which is never held while app code (onDrag) runs.
 *
 * DIVERGENCES (listed): the drag never leaves the app's own window (no
 * cross-app / host-Finder drag; DRAG_FLAG_GLOBAL is accepted but acts local),
 * and a drag started from a floating window (Dialog/PopupWindow) is refused
 * (start returns false: the overlay's coordinate space and touch routing are
 * not the activity's); no accessibility events. The shadow is drawn over the
 * frame by the canvas compositor (drawShadow), as the system draws the drag
 * surface above the app.
 *
 * Bisect gate: MACAPK_DRAG_AND_DROP=0 restores the pre-change behaviour
 * (startDragAndDrop returns false; nothing else here runs).
 */
public final class MacApkDragSession {
    static final boolean ENABLED = !"0".equals(System.getenv("MACAPK_DRAG_AND_DROP"));

    private static volatile MacApkDragSession sActive;

    final View mRoot;
    final Object mLocalState;
    final ClipData mData;
    final ClipDescription mDescription;
    final int mFlags;
    final android.graphics.Bitmap mShadow;
    final int mShadowTouchX, mShadowTouchY;
    /** Last pointer position, ROOT (window) coordinates. Written on the UI
     *  thread, read by the render thread (drawShadow). */
    volatile float mX, mY;
    /** ViewRootImpl.mCurrentDragView. */
    View mCurrentDragView;
    /** Guarded by MacApkDragSession.class. */
    boolean mStartedDispatched;
    volatile boolean mCancelSourcePending = true;
    /** The pointer's UP/CANCEL has been received (possibly still queued for the
     *  UI thread): the pointer no longer belongs to the drag. */
    volatile boolean mFinishing;

    private MacApkDragSession(View root, Object localState, ClipData data, int flags,
                              android.graphics.Bitmap shadow, int touchX, int touchY,
                              float x, float y) {
        mRoot = root;
        mLocalState = localState;
        mData = data;
        mDescription = data != null ? data.getDescription() : null;
        mFlags = flags;
        mShadow = shadow;
        mShadowTouchX = touchX;
        mShadowTouchY = touchY;
        mX = x;
        mY = y;
    }

    /** True while a drag owns the pointer (until its UP/CANCEL arrives). */
    public static boolean isActive() {
        MacApkDragSession s = sActive;
        return s != null && !s.mFinishing;
    }

    /** Run `r` on the UI thread: inline when we ARE on it or when no real main
     *  loop is running (a bare JVM, where nothing would ever dispatch a post),
     *  otherwise queued on the main looper -- AOSP's mHandler hop. */
    private static void runOnUi(Runnable r) {
        if (android.os.Looper.macapkRealMainLoopRunning()
                && !android.os.Looper.macapkIsActivityThread()) {
            // GATE-MUTANT(touchinput:ui-hop) begin
            try {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
                return;
            } catch (Throwable t) {
                com.macapk.loader.NativeLog.w("Mac.apk.Drag", "UI post failed, running inline: " + t);
            }
            // GATE-MUTANT(touchinput:ui-hop) end
        }
        r.run();
    }

    /** View.startDragAndDrop's body. */
    static boolean start(View source, ClipData data, View.DragShadowBuilder builder,
                         Object localState, int flags) {
        if (source == null || builder == null) return false;
        if (!source.isAttachedToWindow()) {                              // mAttachInfo == null
            // AOSP View.startDragAndDrop (android14 View.java:28000) logs this Log.w
            // before returning false; keeping it makes the refusal STATED, not silent.
            com.macapk.loader.NativeLog.w("Mac.apk.Drag", "startDragAndDrop: called on a detached view");
            return false;
        }
        View root = source.getRootView();
        // A Dialog/PopupWindow (WindowOverlayStack) is its own window: its root's
        // left/top are overlay-local, TouchDispatcher offsets by SURFACE px, the
        // canvas compositor draws the shadow only over the activity's root and
        // the source's touch stream cancel would have to go through the overlay.
        // None of that is wired, so refuse rather than deliver events at the
        // wrong point (AOSP would start the drag; this is a listed divergence).
        android.app.Activity act = com.macapk.loader.ActivityHost.peek();
        View content = act != null ? act.macapkContentView() : null;
        if (content != null && root != content) {
            com.macapk.loader.NativeLog.w("Mac.apk.Drag",
                "startDragAndDrop: drag from a floating window is not supported");
            return false;
        }
        float[] pt = com.macapk.loader.TouchDispatcher.macapkPointerIfDown();
        if (pt == null) {                                                 // transferTouchFocus fails
            com.macapk.loader.NativeLog.w("Mac.apk.Drag",
                "startDragAndDrop: no pointer is down (AOSP: unable to transfer touch focus)");
            return false;
        }
        if (sActive != null) {                                            // "Drag already in progress"
            com.macapk.loader.NativeLog.w("Mac.apk.Drag", "startDragAndDrop: drag already in progress");
            return false;
        }
        android.graphics.Point size = new android.graphics.Point();
        android.graphics.Point touch = new android.graphics.Point();
        builder.onProvideShadowMetrics(size, touch);
        if (size.x < 0 || size.y < 0 || touch.x < 0 || touch.y < 0) {
            throw new IllegalStateException("Drag shadow dimensions must not be negative");
        }
        if (size.x == 0 || size.y == 0) {
            if (!View.sAcceptZeroSizeDragShadow) {
                throw new IllegalStateException("Drag shadow dimensions must be positive");
            }
            size.x = 1;
            size.y = 1;
        }
        android.graphics.Bitmap shadow = null;
        try {
            shadow = android.graphics.Bitmap.createBitmap(size.x, size.y,
                    android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(shadow);
            builder.onDrawShadow(c);
        } catch (Throwable t) {
            // The shadow is cosmetic; AOSP draws into a surface that cannot fail
            // this way. Keep the drag, lose only the picture.
            com.macapk.loader.NativeLog.w("Mac.apk.Drag", "shadow draw failed: " + t);
            shadow = null;
        }
        MacApkDragSession s = new MacApkDragSession(root, localState, data, flags, shadow,
                touch.x, touch.y, pt[0] - root.getLeft(), pt[1] - root.getTop());
        synchronized (MacApkDragSession.class) {
            if (sActive != null) {                                        // lost a race to another start
                com.macapk.loader.NativeLog.w("Mac.apk.Drag", "startDragAndDrop: drag already in progress");
                return false;
            }
            sActive = s;
        }
        com.macapk.loader.NativeLog.i("Mac.apk.Drag", "drag started from "
                + source.getClass().getName() + " at (" + s.mX + "," + s.mY + ") shadow="
                + size.x + "x" + size.y + " desc=" + s.mDescription);
        // STARTED arrives asynchronously on a device (a message from the
        // system), i.e. after startDragAndDrop has returned to the app. Post it
        // when a real main loop is running; otherwise (a bare JVM) the first
        // pointer event dispatches it first. Always queued BEFORE any pointer
        // event's own post, so the order STARTED, LOCATION..., DROP holds.
        if (android.os.Looper.macapkRealMainLoopRunning()) {
            try {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(s::ensureStarted);
            } catch (Throwable t) {
                s.ensureStarted();
            }
        }
        root.invalidate();
        return true;
    }

    private DragEvent obtain(int action, float x, float y, ClipDescription desc, ClipData data,
                             boolean result) {
        return DragEvent.obtain(action, x, y, mLocalState, desc, data, result);
    }

    /** ACTION_DRAG_STARTED, exactly once (the posted message and the first
     *  pointer event race for it; the monitor decides who dispatches). */
    void ensureStarted() {
        synchronized (MacApkDragSession.class) {
            if (mStartedDispatched || sActive != this) return;
            mStartedDispatched = true;
        }
        mCurrentDragView = null;
        DragEvent ev = obtain(DragEvent.ACTION_DRAG_STARTED, mX, mY, mDescription, null, false);
        try { mRoot.dispatchDragEvent(ev); }
        catch (Throwable t) { com.macapk.loader.NativeLog.w("Mac.apk.Drag", "DRAG_STARTED threw: " + t); }
    }

    /** Consumes the one pending source-window cancel (step 2). */
    public static boolean takeSourceCancel() {
        MacApkDragSession s = sActive;
        if (s == null || !s.mCancelSourcePending) return false;
        s.mCancelSourcePending = false;
        return true;
    }

    /** The drag's root, so the caller can convert SURFACE px to root coordinates. */
    public static View activeRoot() {
        MacApkDragSession s = sActive;
        return s != null ? s.mRoot : null;
    }

    /**
     * One pointer event while a drag is active, in ROOT coordinates.
     * action: MotionEvent ACTION_DOWN/MOVE (location), UP (drop + end), CANCEL (end).
     * May be called on any thread; the work runs on the UI thread (runOnUi).
     */
    public static void onPointer(final int action, final float x, final float y) {
        final MacApkDragSession s = sActive;
        if (s == null) return;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            s.mFinishing = true;     // isActive() is false from now; the end is queued
        }
        runOnUi(() -> s.onPointerOnUi(action, x, y));
    }

    private void onPointerOnUi(int action, float x, float y) {
        if (sActive != this) return;
        ensureStarted();
        mX = x;
        mY = y;
        switch (action) {
            case MotionEvent.ACTION_UP: {
                boolean result = dispatchPositional(DragEvent.ACTION_DROP, x, y);
                end(result);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                end(false);
                break;
            default:
                dispatchPositional(DragEvent.ACTION_DRAG_LOCATION, x, y);
                break;
        }
        try { mRoot.invalidate(); } catch (Throwable ignored) {}
    }

    /** A fresh ACTION_DOWN while a drag still owns the pointer means its UP was
     *  lost (AOSP: the system tears the drag down with the window/stream). End
     *  it as cancelled so input is never left routed to a dead drag. */
    public static void abort() {
        final MacApkDragSession s = sActive;
        if (s == null) return;
        s.mFinishing = true;
        runOnUi(() -> { s.ensureStarted(); s.end(false); });
    }

    /** View.cancelDragAndDrop (android14 View.java:28165): "mSession
     *  .cancelDragAndDrop(mAttachInfo.mDragToken, false)" -- the system ends the
     *  drag and sends ACTION_DRAG_ENDED (result false) to every view that got
     *  STARTED. false when no drag from `caller`'s window is in flight. */
    static boolean cancel(View caller) {
        final MacApkDragSession s = sActive;
        if (s == null || caller == null || caller.getRootView() != s.mRoot) return false;
        s.mFinishing = true;
        runOnUi(() -> { s.ensureStarted(); s.end(false); });
        return true;
    }

    /** View.updateDragShadow (android14 View.java:28191): with an active drag,
     *  "canvas.drawColor(0, PorterDuff.Mode.CLEAR); shadowBuilder.onDrawShadow(canvas)"
     *  on the drag surface. false when no drag from `caller`'s window is in flight. */
    static boolean updateShadow(View caller, View.DragShadowBuilder builder) {
        final MacApkDragSession s = sActive;
        if (s == null || caller == null || builder == null
                || caller.getRootView() != s.mRoot || s.mShadow == null) return false;
        try {
            s.mShadow.eraseColor(0);
            builder.onDrawShadow(new android.graphics.Canvas(s.mShadow));
        } catch (Throwable t) {
            com.macapk.loader.NativeLog.e("Mac.apk.Drag", "Unable to update drag shadow: " + t);
        }
        try { s.mRoot.invalidate(); } catch (Throwable ignored) {}
        return true;
    }

    /** ViewRootImpl.handleDragEvent for LOCATION / DROP. */
    private boolean dispatchPositional(int action, float x, float y) {
        DragEvent ev = obtain(action, x, y, mDescription,
                action == DragEvent.ACTION_DROP ? mData : null, false);
        boolean result = false;
        try { result = mRoot.dispatchDragEvent(ev); }
        catch (Throwable t) { com.macapk.loader.NativeLog.w("Mac.apk.Drag", "drag dispatch threw: " + t); }
        if (action == DragEvent.ACTION_DRAG_LOCATION && !ev.mEventHandlerWasCalled) {
            setDragFocusOn(this, null, ev);
        }
        if (action == DragEvent.ACTION_DROP) {
            com.macapk.loader.NativeLog.i("Mac.apk.Drag", "Reporting drop result: " + result
                    + " at (" + x + "," + y + ")");
        }
        return result;
    }

    /** DragState.broadcastDragEndedLocked (android14 :223-240): every notified
     *  window gets ENDED with "float x = 0; float y = 0; ... if (!mDragResult &&
     *  (ws.mSession.mPid == mPid)) { x = ws.translateToWindowX(mCurrentX); ..." --
     *  the unconsumed-drop location goes back to the app that started the drag
     *  (always us), and a consumed drop reports (0, 0). */
    private void end(boolean result) {
        synchronized (MacApkDragSession.class) {
            if (sActive != this) return;
            sActive = null;
        }
        float ex = 0f, ey = 0f;
        // GATE-MUTANT(touchinput:ended-coords) begin -- debug/touchinput_gate.sh C6
        if (!result) { ex = mX; ey = mY; }
        // GATE-MUTANT(touchinput:ended-coords) end
        DragEvent ev = obtain(DragEvent.ACTION_DRAG_ENDED, ex, ey, null, null, result);
        mCurrentDragView = null;
        try { mRoot.dispatchDragEvent(ev); }
        catch (Throwable t) { com.macapk.loader.NativeLog.w("Mac.apk.Drag", "DRAG_ENDED threw: " + t); }
        com.macapk.loader.NativeLog.i("Mac.apk.Drag", "drag ended result=" + result);
        try { mRoot.invalidate(); } catch (Throwable ignored) {}
    }

    /** ViewRootImpl.setDragFocus (android14 :8039-8062), called by
     *  View.dispatchDragEvent for LOCATION and DROP. */
    static void setDragFocus(View newDragTarget, DragEvent event) {
        MacApkDragSession s = sActive;
        if (s != null) setDragFocusOn(s, newDragTarget, event);
    }

    private static void setDragFocusOn(MacApkDragSession s, View newDragTarget, DragEvent event) {
        if (s.mCurrentDragView != newDragTarget && !View.sCascadedDragDrop) {
            final float tx = event.mX;
            final float ty = event.mY;
            final int action = event.mAction;
            final ClipData td = event.mClipData;
            event.mX = 0;
            event.mY = 0;
            event.mClipData = null;
            if (s.mCurrentDragView != null) {
                event.mAction = DragEvent.ACTION_DRAG_EXITED;
                s.mCurrentDragView.callDragEventHandler(event);
            }
            if (newDragTarget != null) {
                event.mAction = DragEvent.ACTION_DRAG_ENTERED;
                newDragTarget.callDragEventHandler(event);
            }
            event.mAction = action;
            event.mX = tx;
            event.mY = ty;
            event.mClipData = td;
        }
        s.mCurrentDragView = newDragTarget;
    }

    /** The drag surface, drawn above everything the app drew (the system
     *  composites it above the app's windows). `root` is the tree the caller
     *  just drew; nothing is drawn for any other tree. */
    public static void drawShadow(android.graphics.Canvas canvas, View root) {
        MacApkDragSession s = sActive;
        if (s == null || s.mShadow == null || root == null || s.mRoot != root) return;
        canvas.drawBitmap(s.mShadow, root.getLeft() + s.mX - s.mShadowTouchX,
                root.getTop() + s.mY - s.mShadowTouchY, null);
    }
}
