// android.view.Choreographer -- per-thread frame-pacing primitive
// that hooks into the system's vsync. Real Android exposes this for
// games that want to schedule per-frame work tied to display refresh
// (libGDX uses it for the desktop GL thread on Android 4.1+, every
// modern compose/animation library uses it).
//
// Mac.apk's vsync is the Cocoa display tick (cocoa_bridge.mm drawInMTKView,
// the accumulator block that also fires the NDK AChoreographer). Three paths:
//
//  * MAIN-loop Choreographer (MACAPK_REAL_LOOPER): callbacks queue in
//    sMainQueue and ViewCanvasDriver drains them once per real frame
//    (macapkDriveMainFrame).
//  * PER-THREAD Choreographer on a prepared Looper (a render/pacing thread
//    such as Unity's "UnityChoreographer" or Swappy's ChoreographerThread),
//    MACAPK_CHOREO_VSYNC_THREADS (default ON since 2026-09-27): AOSP's own
//    shape. postFrameCallback schedules ONE vsync on the thread's native
//    looper; the display tick delivers it and wakes the thread; the thread's
//    Looper.loop() hands it to FrameDisplayEventReceiver, which posts doFrame
//    as an async message; doFrame runs the due callbacks phase by phase.
//    =0 restores the previous path: a Handler.postDelayed(one refresh
//    interval) timer per callback, which is not vsync at all.
//  * Everything else (no per-thread looper, or a runtime too old to bind the
//    vsync natives): that same Handler timer.
//
// Timebase, MACAPK_CHOREO_NANOTIME_BASE (default ON since 2026-09-27):
// frameTimeNanos is in the System.nanoTime() timebase, which is AOSP's
// contract (FrameCallback.doFrame javadoc, Choreographer.java:1268-1270 on
// main). =0 restores the previous process-relative rebasedNanos() base. Why
// the contract matters and what the old base cost:
// docs/unity-choreographer-vsync-nanotime-contract_2026-09-27.md.
//
// Per AOSP frameworks/base/core/java/android/view/Choreographer.java
// the public API surface is:
//   - getInstance(): static, per-thread Choreographer
//   - postFrameCallback(FrameCallback)
//   - postFrameCallbackDelayed(FrameCallback, long)
//   - removeFrameCallback(FrameCallback)
//   - postVsyncCallback(VsyncCallback) / removeVsyncCallback(VsyncCallback) (API 34)
//   - getFrameTime() / getFrameTimeNanos()
//   - FrameCallback (inner interface): doFrame(long frameTimeNanos)
//   - VsyncCallback (inner interface): onVsync(FrameData) (API 34)
//   - FrameData / FrameTimeline (inner classes, API 34)
//
// Plus the @hide CALLBACK_INPUT / CALLBACK_ANIMATION /
// CALLBACK_INSETS_ANIMATION / CALLBACK_TRAVERSAL / CALLBACK_COMMIT int
// constants that some libraries still reference reflectively; numbered to
// match AOSP exactly (verified identical on both android10-release [API 29,
// our declared SDK_INT] and main) so a caller compiled against a real
// android.jar that hardcodes AOSP's literal values, then posts through the
// hidden postCallback(int, Runnable, Object), lands in the same phase
// bucket here that it would on real Android.
package android.view;

public final class Choreographer {
    /** Public interface third-party code implements + passes to
     *  postFrameCallback. doFrame is called once per frame with the
     *  expected frame time in nanoseconds (system uptime base). */
    public interface FrameCallback {
        /** @param frameTimeNanos in the {@link System#nanoTime()} timebase
         *  (AOSP contract; see MACAPK_CHOREO_NANOTIME_BASE below). */
        void doFrame(long frameTimeNanos);
    }

    /** API 34: richer per-vsync callback that receives a FrameData
     *  timeline instead of a bare timestamp, so frame-pacing-sensitive
     *  code (AGDK/game-loop libraries, androidx.graphics.lowlatency
     *  front-buffer rendering) can pick a target present time. Fires
     *  once per vsync, like postFrameCallback, then auto-removes. */
    public interface VsyncCallback {
        void onVsync(FrameData data);
    }

    /** API 34: the frame-timeline payload delivered to VsyncCallback.
     *  AOSP pools/reuses one FrameData per Choreographer and gates every
     *  getter on an mInCallback flag so a caller that stashes the
     *  reference reads stale data (or throws) instead of silently
     *  observing a LATER frame's values once the object gets reused.
     *  Mac.apk allocates a fresh, immutable FrameData per dispatch
     *  instead of pooling -- there is no cross-frame reuse to guard
     *  against -- but keeps the same externally observable contract:
     *  every getter throws IllegalStateException once onVsync has
     *  returned. */
    public static class FrameData {
        private final long mFrameTimeNanos;
        private final FrameTimeline[] mFrameTimelines;
        private volatile boolean mInCallback = true;

        FrameData(long frameTimeNanos, FrameTimeline[] frameTimelines) {
            mFrameTimeNanos = frameTimeNanos;
            mFrameTimelines = frameTimelines;
        }
        void invalidate() {
            mInCallback = false;
            for (FrameTimeline t : mFrameTimelines) t.invalidate();
        }
        private void checkInCallback() {
            if (!mInCallback) {
                throw new IllegalStateException(
                    "FrameData is not valid outside of the vsync callback.");
            }
        }
        public long getFrameTimeNanos() { checkInCallback(); return mFrameTimeNanos; }
        public FrameTimeline[] getFrameTimelines() { checkInCallback(); return mFrameTimelines; }
        /** Mac.apk has no real compositor offering multiple candidate
         *  present times (see FrameTimeline below), so the single
         *  synthesized entry is always its own "preferred" one. */
        public FrameTimeline getPreferredFrameTimeline() { checkInCallback(); return mFrameTimelines[0]; }
    }

    /** API 34: one candidate vsync's timing data. Real AOSP can offer
     *  several (SurfaceFlinger's predicted frame timeline), each with a
     *  real vsyncId; Mac.apk has no such multi-candidate compositor
     *  pipeline, so FrameData always carries exactly one honest
     *  FrameTimeline built from the same rebasedNanos() value
     *  postFrameCallback already uses, rather than fabricating extra
     *  entries we cannot back with real data. */
    public static class FrameTimeline {
        private final long mVsyncId;
        private final long mExpectedPresentationTimeNanos;
        private final long mDeadlineNanos;
        private volatile boolean mInCallback = true;

        FrameTimeline(long vsyncId, long expectedPresentationTimeNanos, long deadlineNanos) {
            mVsyncId = vsyncId;
            mExpectedPresentationTimeNanos = expectedPresentationTimeNanos;
            mDeadlineNanos = deadlineNanos;
        }
        void invalidate() { mInCallback = false; }
        private void checkInCallback() {
            if (!mInCallback) {
                throw new IllegalStateException(
                    "FrameTimeline is not valid outside of the vsync callback.");
            }
        }
        public long getVsyncId() { checkInCallback(); return mVsyncId; }
        public long getExpectedPresentationTimeNanos() { checkInCallback(); return mExpectedPresentationTimeNanos; }
        public long getDeadlineNanos() { checkInCallback(); return mDeadlineNanos; }
    }

    // AOSP-hide but referenced by reflection in some libs (e.g. libGDX
    // checks for these via getMethod). Numbered to match AOSP exactly
    // (confirmed identical on both android10-release [API 29, our
    // declared Build.VERSION.SDK_INT] and main): a caller that hardcodes
    // AOSP's real literal values and posts through the hidden
    // postCallback(int, Runnable, Object) must land in the same phase
    // bucket here as it does on real Android.
    public static final int CALLBACK_INPUT             = 0;
    public static final int CALLBACK_ANIMATION         = 1;
    public static final int CALLBACK_INSETS_ANIMATION  = 2;
    public static final int CALLBACK_TRAVERSAL         = 3;
    public static final int CALLBACK_COMMIT            = 4;
    private static final int CALLBACK_LAST             = CALLBACK_COMMIT;

    private static final ThreadLocal<Choreographer> sThreadInstance =
        ThreadLocal.withInitial(Choreographer::new);

    // AOSP-private field name, read REFLECTIVELY by some engines (Unity's
    // EOS/AmongUs build, Swappy) to obtain the most recent vsync time without
    // a callback. We keep it updated to the last dispatched frame time so the
    // reflective read returns a live value instead of throwing NoSuchField.
    private long mLastFrameTimeNanos;

    // The Looper of the thread that created this (per-thread) Choreographer.
    // Real Android runs a thread's frame callbacks ON THAT THREAD's Looper:
    // a dedicated frame-driver thread (e.g. Unity's "UnityChoreographer",
    // Swappy's ChoreographerThread) does postFrameCallback() then blocks in
    // its OWN Looper.loop() waiting for doFrame. Delivering the callback to a
    // different Looper (we used to hardcode getMainLooper()) means that
    // driver thread waits forever and the render loop never advances. Capture
    // the creating thread's Looper so callbacks fire on the right thread.
    private final android.os.Looper mLooper;
    private final android.os.Handler mHandler;

    // Per-thread Handler-driven pending callbacks (postFrameCallback /
    // postVsyncCallback on a dedicated, non-main-vsync render thread),
    // keyed by the caller's FrameCallback/VsyncCallback identity so
    // removeFrameCallback/removeVsyncCallback can cancel the underlying
    // Handler Runnable before it fires -- mirrors AOSP's == identity match
    // in CallbackQueue.removeCallbacksLocked. The main-vsync path tracks
    // its own pending entries in sMainQueue and does not use this map.
    private final java.util.Map<Object, Runnable> mPendingCallbacks =
        java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<Object, Runnable>());

    // Tier 3 M7: when this Choreographer belongs to the MAIN loop (macapk-activity)
    // and MACAPK_REAL_LOOPER is on, frame callbacks are NOT posted on a 16 ms
    // Handler timer -- they are QUEUED and drained once per REAL frame by
    // macapkDriveMainFrame() (called from ViewCanvasDriver each Metal vsync), so
    // ValueAnimator / ViewPropertyAnimator / MotionLayout / Compose withFrameNanos
    // tick on TRUE vsync in the AOSP doFrame order (INPUT -> ANIMATION ->
    // [TRAVERSAL = the canvas doFrame] -> COMMIT). NON-main Choreographers (a
    // Unity/libGDX render-driver thread that blocks in its OWN Looper.loop()
    // waiting for doFrame) keep the per-thread Handler path UNTOUCHED.
    private static final class FrameAction {
        final int type; final FrameCallback frameCb; final VsyncCallback vsyncCb;
        final Runnable runnable; final long dueNanos;
        // The caller's token (postCallback's third argument, or AOSP's
        // FRAME_CALLBACK_TOKEN / VSYNC_CALLBACK_TOKEN). Matched by the
        // vsync-thread removeCallbacks, as AOSP's removeCallbacksLocked does.
        final Object token;
        FrameAction(int t, FrameCallback f, VsyncCallback v, Runnable r, long d) {
            this(t, f, v, r, null, d);
        }
        FrameAction(int t, FrameCallback f, VsyncCallback v, Runnable r, Object tok, long d) {
            type = t; frameCb = f; vsyncCb = v; runnable = r; token = tok; dueNanos = d;
        }
    }

    // ---- MACAPK_CHOREO_VSYNC_THREADS / MACAPK_CHOREO_NANOTIME_BASE (2026-09-27) ----
    //
    // Both default ON; each "=0" restores its previous path exactly. Read once.
    //
    // VSYNC_THREADS: a per-thread Choreographer (mLooper is a prepared per-thread
    // Looper, not the main one) is driven by the display vsync the way AOSP's is,
    // instead of one Handler.postDelayed(refresh interval) timer per callback.
    // Real Android has no timer here: Choreographer.scheduleFrameLocked calls
    // FrameDisplayEventReceiver.scheduleVsync, and the vsync comes back through
    // the thread's own Looper (see android.os.Looper.macapkScheduleVsync).
    // What it cost to be a timer: a render-pacing thread (Unity's
    // "UnityChoreographer", whose doFrame handler is the ONLY writer of the frame
    // counter UnityMain waits on) ticked off the display, on its own clock.
    static final boolean VSYNC_THREADS =
        !"0".equals(System.getenv("MACAPK_CHOREO_VSYNC_THREADS"));
    // NANOTIME_BASE: every frame time this class hands out is in the
    // System.nanoTime() timebase, AOSP's documented contract. The previous
    // process-relative rebasedNanos() base (kept for =0) broke any consumer that
    // mixes frameTimeNanos with System.nanoTime(): Unity's own choreographer
    // enable samples System.nanoTime() through JNI and its per-vsync handler then
    // stores frameTimeNanos next to it, so the two epochs met in one formula and
    // presentTime came out near minus the host uptime (the "TimeManager freeze"
    // that swappy_present_tick's offset rewrite papered over). Our own
    // ValueAnimator.reverse() and AnimationUtils.currentAnimationTimeMillis()
    // already used raw System.nanoTime() against these frame times.
    static final boolean NANOTIME_BASE =
        !"0".equals(System.getenv("MACAPK_CHOREO_NANOTIME_BASE"));
    // AOSP Choreographer's private tokens, so removeCallbacks(type, null, token)
    // cannot confuse a frame callback with a posted Runnable.
    private static final Object FRAME_CALLBACK_TOKEN = new Object();
    private static final Object VSYNC_CALLBACK_TOKEN = new Object();

    // Vsync-thread state (used only when mVsyncThread). mLock guards the queue
    // and mFrameScheduled, like AOSP's mLock.
    private final Object mLock = new Object();
    private final java.util.ArrayList<FrameAction> mVsyncQueue = new java.util.ArrayList<>();
    private boolean mFrameScheduled;
    // AOSP mCallbacksRunning: true while doFrame is running callbacks, so
    // getFrameTimeNanos() answers the frame's time rather than "now".
    private volatile boolean mCallbacksRunning;
    private final boolean mVsyncThread;
    // AOSP FrameDisplayEventReceiver, as two members instead of an inner class
    // (a new framework class would move FRAMEWORK_ID and re-convert every app's
    // DEX): onVsync is mDisplayEventReceiver, run() is mVsyncFrameRunnable.
    private final java.util.function.LongConsumer mDisplayEventReceiver;
    private final Runnable mVsyncFrameRunnable;
    private long mVsyncTimestampNanos;   // System.nanoTime() base; looper thread only
    private static final java.util.concurrent.atomic.AtomicInteger sVsyncFrames =
        new java.util.concurrent.atomic.AtomicInteger();
    // v1.0.411: PROCESS-WIDE (static), not per-thread. The Compose window
    // Recomposer's AndroidUiFrameClock posts its withFrameNanos frame callback
    // from whatever thread the recompose coroutine happens to run on (e.g. a
    // kotlinx DefaultDispatcher worker, or the JVM "main" thread) -- NOT
    // necessarily the macapk-activity main loop. Those threads have no Looper of
    // their own, so the Choreographer ctor falls mLooper back to getMainLooper(),
    // making isMainVsyncDriven() true -- but a PER-THREAD queue would only be
    // drained for the instance whose doMainFrame() macapkDriveMainFrame() calls
    // (the macapk-activity instance). Frame callbacks posted on any OTHER
    // main-vsync-driven Choreographer instance were silently dropped, so the
    // window recomposer parked in withFrameNanos forever and the screen never
    // recomposed (Reddit WelcomeV2 stayed blank: i3 stuck on the loading-sentinel
    // ViewState, never re-reading the produced n9k0). A single shared queue,
    // drained once per real vsync by the macapk-activity loop, runs ALL
    // main-vsync frame callbacks regardless of which thread enqueued them.
    // Self-looping driver threads (Unity/libGDX, which run their OWN Looper.loop())
    // have mLooper != getMainLooper() -> NOT main-vsync-driven -> they keep the
    // per-thread Handler path untouched (see postFrameCallbackDelayed below).
    private static final java.util.ArrayList<FrameAction> sMainQueue = new java.util.ArrayList<>();
    private boolean isMainVsyncDriven() {
        return android.os.Looper.MACAPK_REAL_LOOPER
            && mLooper == android.os.Looper.getMainLooper();
    }
    // v1.0.411 DIAGNOSTIC (MACAPK_CHOREO_THREADDIAG): confirm WHICH thread the
    // Compose window recomposer's AndroidUiFrameClock Choreographer lives on, and
    // whether its postFrameCallback takes the drained main-vsync path or the dead
    // per-thread Handler-timer path. The decisive probe for "Reddit's screen
    // recomposer parks in withFrameNanos because its Choreographer is never drained."
    private static final boolean THREADDIAG = System.getenv("MACAPK_CHOREO_THREADDIAG") != null;
    private static final java.util.concurrent.atomic.AtomicInteger sChoreoFire =
        new java.util.concurrent.atomic.AtomicInteger();
    // Time-diagnostic (MACAPK_CHOREO_TIME): log the frameTimeNanos delta Unity
    // actually receives each frame. Unity 2019+ derives Time.deltaTime from this
    // Choreographer timestamp, so a wrong per-frame delta here = crawling
    // animations even at 60fps render. (MACAPK_UHOOK also turned it on, with a
    // Crossy-era call/post log; both went with the il2cpp diag kit, 2026-09-27.)
    private static final boolean TIMEDIAG = System.getenv("MACAPK_CHOREO_TIME") != null;
    private static volatile long sLastWhen = 0;

    // MACAPK_FRAME_TRACE=1 (diagnostic, off by default; one static read when
    // unset): a "choreo-thread:<thread>" interval batch for every PER-THREAD
    // (non-main) Choreographer's frame dispatch, in the format of the native
    // "[frame-trace]" batches (cocoa_bridge.mm display/drive, bionic_shim.cpp
    // guest-swap/guest-pace/guest-present): 600 System.nanoTime() deltas per
    // line plus t_end, the last sample's System.nanoTime() in seconds, which on
    // macOS is mach_absolute_time -- the native batches' clock. So
    // debug/frame_hitch_stats.py can tell a late vsync-paced render thread
    // (this dispatch was late too) from a frame that was late on its own.
    private static final boolean FRAME_TRACE;
    static {
        String e = System.getenv("MACAPK_FRAME_TRACE");
        FRAME_TRACE = e != null && e.startsWith("1");
    }
    private long mTracePrevNanos;
    private int mTraceN;
    private int[] mTraceDtUs;
    private void traceThreadFrame() {
        final long now = System.nanoTime();
        if (mTracePrevNanos != 0L) {
            if (mTraceDtUs == null) mTraceDtUs = new int[600];
            long d = (now - mTracePrevNanos) / 1000L;
            mTraceDtUs[mTraceN++] = (int) Math.max(0L, Math.min(d, Integer.MAX_VALUE));
            if (mTraceN == mTraceDtUs.length) {
                StringBuilder sb = new StringBuilder(600 * 7 + 96);
                sb.append("[frame-trace] choreo-thread:")
                  .append(Thread.currentThread().getName().replace(' ', '_'))
                  .append(" n=").append(mTraceN).append(" dt_us=");
                for (int i = 0; i < mTraceN; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(mTraceDtUs[i]);
                }
                sb.append(" t_end=").append(String.format(java.util.Locale.ROOT, "%.6f", now / 1e9));
                System.err.println(sb);
                mTraceN = 0;
            }
        }
        mTracePrevNanos = now;
    }

    // Process-relative frameTimeNanos base. On macOS System.nanoTime() is HOST
    // UPTIME (observed ~292632 s = 3.4 days), so a raw `when` is a huge
    // nanosecond value. Unity derives its frame clock from the Choreographer
    // frameTimeNanos passed to doFrame(), so a huge `when` makes
    // Time.unscaledTime huge (~292620) -- the v1.0.212 "unscaledTime residual"
    // (that session rebased clock_gettime but NOT this Choreographer source).
    // At that magnitude a float32 holds only ~0.035 s precision, so any engine
    // logic that does absolute-frame-time math in float32 (Timeline / intro
    // animators / unscaledTime-based gates -- e.g. Crossy's SplashIntro) breaks.
    // On a real Android device frameTimeNanos is boot-relative and small, so
    // rebase ours process-relative to match. Rebasing subtracts a CONSTANT, so
    // every frame-to-frame DELTA is unchanged -- delta-based consumers are
    // unaffected; only the (huge) absolute timestamp is corrected.
    private static volatile long sNanoBase = 0;
    private static long rebasedNanos() {
        long n = System.nanoTime();
        long b = sNanoBase;
        if (b == 0) {
            synchronized (Choreographer.class) {
                if (sNanoBase == 0) sNanoBase = n;
                b = sNanoBase;
            }
        }
        long r = n - b;
        return r < 0 ? 0 : r;
    }

    /** "Now" on the frame clock: System.nanoTime() (AOSP), or rebasedNanos()
     *  under MACAPK_CHOREO_NANOTIME_BASE=0. Every frame time and due time in
     *  this class goes through here or toFrameClock, so the two can never mix. */
    private static long frameClockNanos() {
        return NANOTIME_BASE ? System.nanoTime() : rebasedNanos();
    }

    /** A System.nanoTime()-base timestamp (a delivered vsync) on the frame
     *  clock: unchanged, or shifted by rebasedNanos()'s own base under =0. */
    private static long toFrameClock(long nanoTimeNanos) {
        if (NANOTIME_BASE) return nanoTimeNanos;
        rebasedNanos();   // initialises sNanoBase if this is the first use
        long r = nanoTimeNanos - sNanoBase;
        return r < 0 ? 0 : r;
    }

    // AOSP derives the non-main-vsync Handler tick from the real display:
    // mFrameIntervalNanos = (long) (1e9 / getRefreshRate()), read from
    // DisplayManagerGlobal's DisplayInfo. Mac.apk has no DisplayManagerGlobal;
    // android.view.Display (same package) is the single authoritative source
    // for the advertised refresh rate, so derive from it here instead of a
    // second, independently-hardcoded literal that could silently drift from
    // Display's -- see Display.getRefreshRate().
    private static long frameIntervalNanos() {
        float hz = new Display().getRefreshRate();
        return (long) (1_000_000_000L / hz);
    }
    private static long frameIntervalMillis() {
        return frameIntervalNanos() / 1_000_000L;
    }

    // Builds the single honest FrameTimeline entry postVsyncCallback hands
    // out (see the FrameData/FrameTimeline class comments above for why
    // there is only ever one).
    private static FrameData buildFrameData(long nanos) {
        FrameTimeline timeline = new FrameTimeline(nanos, nanos, nanos + frameIntervalNanos());
        return new FrameData(nanos, new FrameTimeline[] { timeline });
    }

    private Choreographer() {
        android.os.Looper l = android.os.Looper.myLooper();
        if (l == null) l = android.os.Looper.getMainLooper();
        mLooper = l;
        mHandler = new android.os.Handler(mLooper);
        // A per-thread looper that can receive the display vsync gets AOSP's
        // vsync-driven frame; the main looper answers false here (it has its own
        // per-real-frame drain), and so does a runtime too old to bind the
        // natives, which keeps the Handler timer.
        mVsyncThread = VSYNC_THREADS && mLooper.macapkCanReceiveVsync();
        mDisplayEventReceiver = mVsyncThread ? this::onDisplayVsync : null;
        mVsyncFrameRunnable = mVsyncThread ? () -> doVsyncFrame(mVsyncTimestampNanos) : null;
    }

    /** Per-thread singleton. Real Android constructs lazily on first
     *  call from any thread that has a Looper; we do the same. */
    public static Choreographer getInstance() {
        Choreographer c = sThreadInstance.get();
        if (THREADDIAG) android.util.Log.i("Mac.apk.Choreo",
            "getInstance thread=" + Thread.currentThread().getName()
            + " mLooper==mainLooper=" + (c.mLooper == android.os.Looper.getMainLooper())
            + " mainVsyncDriven=" + c.isMainVsyncDriven()
            + " vsyncThread=" + c.mVsyncThread
            + " realLooper=" + android.os.Looper.MACAPK_REAL_LOOPER);
        return c;
    }

    // Hidden AOSP API: Choreographer.postCallback(int, Runnable, Object). Some
    // engines (incl. Unity via JNI/reflection) register frame work through this
    // rather than the public postFrameCallback. Mirror it onto the same Looper.
    public void postCallback(int callbackType, Runnable action, Object token) {
        postCallbackDelayed(callbackType, action, token, 0);
    }
    public void postCallbackDelayed(int callbackType, Runnable action,
                                    Object token, long delayMillis) {
        if (action == null) return;
        if (isMainVsyncDriven()) {
            synchronized (sMainQueue) {
                sMainQueue.add(new FrameAction(callbackType, null, null, action,
                    frameClockNanos() + Math.max(0L, delayMillis) * 1_000_000L));
            }
            return;
        }
        if (mVsyncThread) {
            // AOSP postCallbackDelayed validates the type before queueing; an
            // out-of-range type would otherwise sit in a phase doFrame never runs.
            if (callbackType < 0 || callbackType > CALLBACK_LAST) {
                throw new IllegalArgumentException("callbackType is invalid");
            }
            postVsyncThreadAction(new FrameAction(callbackType, null, null, action, token,
                vsyncDueMillis(delayMillis)));
            return;
        }
        mHandler.postDelayed(action, Math.max(0L, delayMillis) + 16L);
    }
    public void removeCallbacks(int callbackType, Runnable action, Object token) {
        if (mVsyncThread) {
            // AOSP CallbackQueue.removeCallbacksLocked: a null action or a null
            // token is a wildcard; both null clears the phase.
            synchronized (mLock) {
                mVsyncQueue.removeIf(a -> a.type == callbackType
                    && (action == null || actionOf(a) == action)
                    && (token == null || a.token == token));
            }
            return;
        }
        if (action != null && isMainVsyncDriven()) {
            synchronized (sMainQueue) {
                sMainQueue.removeIf(a -> a.runnable == action && a.type == callbackType);
            }
        }
    }

    /** Schedule cb to run on the next frame. Mac.apk doesn't have a
     *  separate vsync source for callbacks; we approximate by running
     *  the callback as a Handler.postDelayed(16ms). Apps that use
     *  Choreographer for animation timing get ~60 Hz cadence. */
    public void postFrameCallback(FrameCallback callback) {
        postFrameCallbackDelayed(callback, 0);
    }

    public void postFrameCallbackDelayed(FrameCallback callback, long delayMillis) {
        if (callback == null) return;
        if (THREADDIAG) android.util.Log.i("Mac.apk.Choreo",
            "postFrameCallback thread=" + Thread.currentThread().getName()
            + " mainVsyncDriven=" + isMainVsyncDriven()
            + " mLooper==main=" + (mLooper == android.os.Looper.getMainLooper())
            + " cb=" + callback.getClass().getName());
        // Tier 3 M7: the MAIN-loop Choreographer queues the callback for the next
        // REAL vsync (drained by macapkDriveMainFrame) instead of a 16 ms Handler
        // timer. A delay-0 postFrameCallback (the ValueAnimator / withFrameNanos
        // case) becomes due immediately and runs on the very next frame.
        if (isMainVsyncDriven()) {
            synchronized (sMainQueue) {
                sMainQueue.add(new FrameAction(CALLBACK_ANIMATION, callback, null, null,
                    frameClockNanos() + Math.max(0L, delayMillis) * 1_000_000L));
            }
            return;
        }
        // MACAPK_CHOREO_VSYNC_THREADS: a per-thread Choreographer runs the
        // callback on the next display vsync delivered to its own Looper.
        if (mVsyncThread) {
            postVsyncThreadAction(new FrameAction(CALLBACK_ANIMATION, callback, null, null,
                FRAME_CALLBACK_TOKEN, vsyncDueMillis(delayMillis)));
            return;
        }
        // MACAPK_CHOREO_VSYNC_THREADS=0 (or no per-thread looper, or a runtime
        // without the vsync natives): a Handler timer of one refresh interval.
        // This is NOT vsync -- it free-runs on the thread's own clock.
        final long when = frameClockNanos() + delayMillis * 1_000_000L
            + frameIntervalNanos();
        // Run on the Looper of the thread that owns this Choreographer (the
        // thread that called getInstance()), NOT the main Looper -- a dedicated
        // frame-driver thread blocks in its own Looper.loop() waiting for this.
        final Runnable r = new Runnable() {
                @Override public void run() {
                    mPendingCallbacks.remove(callback);
                    if (FRAME_TRACE) traceThreadFrame();
                    if (TIMEDIAG) {
                        long _lw = sLastWhen; sLastWhen = when;
                        android.util.Log.i("Mac.apk.Choreo",
                            "doFrame fire #" + sChoreoFire.incrementAndGet()
                            + " when=" + when + " dWhenMs="
                            + ((when - _lw) / 1_000_000L)
                            + " nowMs=" + (System.nanoTime() / 1_000_000L));
                    }
                    try {
                        mLastFrameTimeNanos = when;
                        callback.doFrame(when);
                    } catch (Throwable t) {
                        android.util.Log.e("Mac.apk.Choreo", "doFrame threw: " + t, t);
                    }
                }
            };
        mPendingCallbacks.put(callback, r);
        mHandler.postDelayed(r, Math.max(0L, delayMillis) + frameIntervalMillis());
    }

    /** API 34: like postFrameCallback, but the callback receives a
     *  FrameData timeline instead of a bare frameTimeNanos. Fires once per
     *  vsync then auto-removes, matching AOSP's dispatch of VsyncCallback
     *  through the CALLBACK_ANIMATION bucket (postCallbackDelayedInternal
     *  (CALLBACK_ANIMATION, callback, VSYNC_CALLBACK_TOKEN, 0)) -- so it
     *  orders relative to ANIMATION-phase work exactly like
     *  postFrameCallback does. */
    public void postVsyncCallback(VsyncCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        if (isMainVsyncDriven()) {
            synchronized (sMainQueue) {
                sMainQueue.add(new FrameAction(CALLBACK_ANIMATION, null, callback, null,
                    frameClockNanos()));
            }
            return;
        }
        if (mVsyncThread) {
            // AOSP: postCallbackDelayedInternal(CALLBACK_ANIMATION, callback,
            // VSYNC_CALLBACK_TOKEN, 0).
            postVsyncThreadAction(new FrameAction(CALLBACK_ANIMATION, null, callback, null,
                VSYNC_CALLBACK_TOKEN, vsyncDueMillis(0L)));
            return;
        }
        final long when = frameClockNanos() + frameIntervalNanos();
        final Runnable r = new Runnable() {
                @Override public void run() {
                    mPendingCallbacks.remove(callback);
                    FrameData data = buildFrameData(when);
                    try {
                        mLastFrameTimeNanos = when;
                        callback.onVsync(data);
                    } catch (Throwable t) {
                        android.util.Log.e("Mac.apk.Choreo", "onVsync threw: " + t, t);
                    } finally {
                        data.invalidate();
                    }
                }
            };
        mPendingCallbacks.put(callback, r);
        mHandler.postDelayed(r, frameIntervalMillis());
    }

    /** API 34: cancel a callback registered via postVsyncCallback. See
     *  removeFrameCallback below for the identity-map cancellation
     *  mechanism used on the per-thread (non-main-vsync) path. */
    public void removeVsyncCallback(VsyncCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        if (isMainVsyncDriven()) {
            synchronized (sMainQueue) {
                sMainQueue.removeIf(a -> a.vsyncCb == callback);
            }
            return;
        }
        if (mVsyncThread) {
            synchronized (mLock) {
                mVsyncQueue.removeIf(a -> a.type == CALLBACK_ANIMATION && a.vsyncCb == callback);
            }
            return;
        }
        Runnable r = mPendingCallbacks.remove(callback);
        if (r != null) mHandler.removeCallbacks(r);
    }

    public void removeFrameCallback(FrameCallback callback) {
        if (callback == null) return;
        // Tier 3 M7: the MAIN-loop Choreographer can cancel a queued frame
        // callback (the vsync-driven path tracks them, unlike the Handler path).
        if (isMainVsyncDriven()) {
            synchronized (sMainQueue) {
                sMainQueue.removeIf(a -> a.frameCb == callback);
            }
            return;
        }
        // Vsync-thread path: AOSP removeCallbacksInternal(CALLBACK_ANIMATION,
        // callback, FRAME_CALLBACK_TOKEN) -- the record is gone before doFrame
        // extracts it, so a removed callback provably does not fire.
        if (mVsyncThread) {
            synchronized (mLock) {
                mVsyncQueue.removeIf(a -> a.type == CALLBACK_ANIMATION && a.frameCb == callback);
            }
            return;
        }
        // Per-thread (dedicated render-driver) path: AOSP's
        // removeCallbacksInternal removes the queued callback record before
        // doFrame reads it, so a removed callback provably does not fire.
        // Cancel the underlying Handler Runnable via the identity map
        // postFrameCallbackDelayed populated, instead of the previous
        // "let it fire, doFrame is idempotent" assumption -- unverified
        // across engines, and a callback touching state torn down at
        // removal time would observe a stale/freed reference.
        Runnable r = mPendingCallbacks.remove(callback);
        if (r != null) mHandler.removeCallbacks(r);
    }

    /** The time the frame in progress started, in the System.nanoTime()
     *  timebase (AOSP getFrameTimeNanos: mLastFrameTimeNanos while callbacks
     *  run). AOSP THROWS outside a callback; Mac.apk has always answered "now"
     *  there instead, and still does -- on the frame clock, so under
     *  MACAPK_CHOREO_NANOTIME_BASE=0 this is exactly the old rebasedNanos(). */
    public long getFrameTime() { return getFrameTimeNanos() / 1_000_000L; }
    public long getFrameTimeNanos() {
        if (NANOTIME_BASE && mCallbacksRunning) return mLastFrameTimeNanos;
        return frameClockNanos();
    }

    /** @hide -- backs AnimationUtils.getExpectedPresentationTimeNanos().
     *  AOSP's expected presentation time is the vsync deadline of the frame
     *  currently being built (Choreographer.FrameTimeline.getExpectedPresentation
     *  TimeNanos); outside a frame callback AOSP has no value and its callers
     *  fall back to the current frame time, which is what this returns. */
    public long macapkExpectedPresentationTimeNanos() { return frameClockNanos(); }

    /** Tier 3 M7: drive the calling thread's Choreographer one REAL frame. Called
     *  from ViewCanvasDriver on the macapk-activity main loop each Metal vsync
     *  (BEFORE the canvas traversal), so the MAIN-loop Choreographer's queued
     *  frame callbacks -- ValueAnimator / ViewPropertyAnimator / MotionLayout /
     *  Compose withFrameNanos -- tick on true vsync. The view TRAVERSAL
     *  (CALLBACK_TRAVERSAL) is the canvas doFrame itself, which the driver runs
     *  right after this returns; an animation that invalidated here therefore
     *  renders on this same frame. No-op for a non-main-loop Choreographer. */
    public static void macapkDriveMainFrame() {
        Choreographer c = sThreadInstance.get();
        if (c != null) c.doMainFrame();
    }

    private void doMainFrame() {
        if (!isMainVsyncDriven()) return;
        final long nanos = frameClockNanos();
        mLastFrameTimeNanos = nanos;
        java.util.ArrayList<FrameAction> due;
        synchronized (sMainQueue) {
            if (sMainQueue.isEmpty()) return;
            due = new java.util.ArrayList<>();
            // Snapshot the callbacks that are due THIS frame and remove them up
            // front, so a callback that re-posts itself for the next frame (the
            // normal ValueAnimator / withFrameNanos pattern) lands in the queue
            // for the NEXT doMainFrame, not an infinite loop within this one.
            java.util.Iterator<FrameAction> it = sMainQueue.iterator();
            while (it.hasNext()) {
                FrameAction a = it.next();
                if (a.dueNanos <= nanos) { due.add(a); it.remove(); }
            }
        }
        if (due.isEmpty()) return;
        // AOSP doFrame order: INPUT(0) -> ANIMATION(1) -> INSETS_ANIMATION(2)
        // -> [TRAVERSAL(3) = canvas] -> COMMIT(4).
        due.sort((a, b) -> Integer.compare(a.type, b.type));
        if (TIMEDIAG) android.util.Log.i("Mac.apk.Choreo",
            "doMainFrame fire #" + sChoreoFire.incrementAndGet() + " ran=" + due.size()
            + " nanos=" + nanos);
        mCallbacksRunning = true;
        try {
            for (FrameAction a : due) {
                try {
                    if (a.frameCb != null) {
                        a.frameCb.doFrame(nanos);
                    } else if (a.vsyncCb != null) {
                        FrameData data = buildFrameData(nanos);
                        try {
                            a.vsyncCb.onVsync(data);
                        } finally {
                            data.invalidate();
                        }
                    } else if (a.runnable != null) {
                        a.runnable.run();
                    }
                } catch (Throwable t) {
                    android.util.Log.e("Mac.apk.Choreo", "doMainFrame callback threw: " + t, t);
                }
            }
        } finally {
            mCallbacksRunning = false;
        }
    }

    // ---------------------------------------------------------------------
    // MACAPK_CHOREO_VSYNC_THREADS: the per-thread, vsync-driven Choreographer.
    // A transcription of AOSP's postCallbackDelayedInternal / scheduleFrameLocked
    // / FrameDisplayEventReceiver / doFrame / doCallbacks (frameworks/base
    // core/java/android/view/Choreographer.java, main), minus what has no
    // Mac.apk counterpart (FrameInfo, buffer-stuffing recovery, FPS divisor,
    // tracing). Only ever reached when mVsyncThread.

    /** The record's action as AOSP stores it (Runnable / FrameCallback /
     *  VsyncCallback), for removeCallbacks' identity match. */
    private static Object actionOf(FrameAction a) {
        if (a.frameCb != null) return a.frameCb;
        if (a.vsyncCb != null) return a.vsyncCb;
        return a.runnable;
    }

    /** A vsync-thread record's DUE time. For this queue FrameAction.dueNanos
     *  holds AOSP's CallbackRecord.dueTime, i.e. SystemClock.uptimeMillis() +
     *  delay in MILLISECONDS, not a frame-clock nanosecond value. AOSP keeps the
     *  due time, the MSG_DO_SCHEDULE_CALLBACK message time and the extraction
     *  "now" all on that one clock; mixing a nanosecond due time with a
     *  millisecond message time strands a delayed record whenever uptimeMillis()
     *  truncates (the message fires, finds it not yet due, and nothing asks
     *  again), so this queue uses AOSP's clock throughout. */
    private static long vsyncDueMillis(long delayMillis) {
        return android.os.SystemClock.uptimeMillis() + Math.max(0L, delayMillis);
    }

    /** AOSP postCallbackDelayedInternal: queue the record; if it is due now,
     *  schedule the frame, else post an async message at its due time that
     *  schedules it then (MSG_DO_SCHEDULE_CALLBACK). */
    private void postVsyncThreadAction(FrameAction a) {
        synchronized (mLock) {
            mVsyncQueue.add(a);
            final long now = android.os.SystemClock.uptimeMillis();
            if (a.dueNanos <= now) {
                scheduleFrameLocked();
            } else {
                final int type = a.type;
                android.os.Message msg = android.os.Message.obtain(mHandler,
                    () -> doScheduleCallback(type));
                msg.setAsynchronous(true);
                mHandler.sendMessageAtTime(msg, a.dueNanos);
            }
        }
    }

    /** AOSP doScheduleCallback: a delayed record came due; schedule the frame
     *  if nothing is scheduled and the phase really has a due record (it may
     *  have been removed in the meantime). */
    private void doScheduleCallback(int type) {
        synchronized (mLock) {
            if (mFrameScheduled) return;
            final long now = android.os.SystemClock.uptimeMillis();
            for (FrameAction a : mVsyncQueue) {
                if (a.type == type && a.dueNanos <= now) { scheduleFrameLocked(); return; }
            }
        }
    }

    /** AOSP scheduleFrameLocked -> scheduleVsyncLocked: ask the display for ONE
     *  vsync on this Choreographer's Looper. Already scheduled = nothing to do,
     *  so N callbacks posted in one frame still get exactly one doFrame. */
    private void scheduleFrameLocked() {
        if (mFrameScheduled) return;
        mFrameScheduled = true;
        scheduleVsyncLocked();
    }

    private void scheduleVsyncLocked() {
        if (mLooper.macapkScheduleVsync(mDisplayEventReceiver)) return;
        // The native request failed after the constructor saw it bound, which
        // should not happen. Run the frame on the Handler timer rather than
        // leave the callbacks waiting for a vsync nothing will deliver.
        mHandler.postDelayed(() -> onDisplayVsync(System.nanoTime()), frameIntervalMillis());
    }

    /** AOSP FrameDisplayEventReceiver.onVsync: runs on this Choreographer's
     *  Looper thread (android.os.Looper.loop takes the delivered vsync there)
     *  and posts doFrame (FrameDisplayEventReceiver.run, here
     *  mVsyncFrameRunnable) as an ASYNC message at the vsync's own time, so
     *  messages that predate the vsync still run first. */
    private void onDisplayVsync(long timestampNanos) {
        final long now = System.nanoTime();
        // AOSP: "Frame time is ... in the future!" -> clamp to now.
        if (timestampNanos > now) timestampNanos = now;
        mVsyncTimestampNanos = timestampNanos;
        android.os.Message msg = android.os.Message.obtain(mHandler, mVsyncFrameRunnable);
        msg.setAsynchronous(true);
        // AOSP sends at timestampNanos / NANOS_PER_MS, in the uptimeMillis
        // clock -- on a device the same clock as System.nanoTime(). Under
        // MACAPK_GUEST_CLOCK_NANOTIME (default ON) our uptimeMillis() IS that
        // same clock, so this expression equals AOSP's timestampNanos /
        // NANOS_PER_MS exactly (the age-then-add-back below is just a more
        // roundabout way of writing the same value). Only under =0 is
        // SystemClock.uptimeMillis() process-relative, which is why this
        // carries the vsync's AGE across instead of its absolute value: that
        // is what keeps the two arms giving the same delivery time.
        mHandler.sendMessageAtTime(msg,
            android.os.SystemClock.uptimeMillis() - (now - timestampNanos) / 1_000_000L);
    }

    /** AOSP doFrame, in the parts Mac.apk has: drop a frame nobody scheduled,
     *  resync a vsync delivered more than one interval late to the last vsync
     *  boundary before now (so frameTimeNanos is never more than one frame
     *  stale), refuse a frame time that runs backwards, then run the phases in
     *  AOSP order. frameTimeNanos is on the frame clock (System.nanoTime() by
     *  default). */
    private void doVsyncFrame(long timestampNanos) {
        final long frameTimeNanos;
        synchronized (mLock) {
            if (!mFrameScheduled) return;
            long ts = timestampNanos;
            final long intervalNanos = frameIntervalNanos();
            final long startNanos = System.nanoTime();
            final long jitterNanos = startNanos - ts;
            if (intervalNanos > 0 && jitterNanos >= intervalNanos) {
                ts = startNanos - (jitterNanos % intervalNanos);
            }
            final long ft = toFrameClock(ts);
            if (ft < mLastFrameTimeNanos) {
                // AOSP: "Frame time appears to be going backwards" -- wait for
                // the next vsync, leaving mFrameScheduled set.
                scheduleVsyncLocked();
                return;
            }
            mFrameScheduled = false;
            mLastFrameTimeNanos = ft;
            frameTimeNanos = ft;
        }
        final int n = sVsyncFrames.incrementAndGet();
        if (FRAME_TRACE) traceThreadFrame();
        if (TIMEDIAG) {
            long lw = sLastWhen; sLastWhen = frameTimeNanos;
            android.util.Log.i("Mac.apk.Choreo", "doFrame(vsync) #" + n
                + " thread=" + Thread.currentThread().getName()
                + " frameTimeNanos=" + frameTimeNanos
                + " dMs=" + ((frameTimeNanos - lw) / 1_000_000L)
                + " ageUs=" + ((System.nanoTime() - timestampNanos) / 1_000L));
        } else if (n == 1) {
            android.util.Log.i("Mac.apk.Choreo", "first vsync-driven doFrame on thread="
                + Thread.currentThread().getName()
                + " (MACAPK_CHOREO_VSYNC_THREADS=1, nanoTimeBase=" + NANOTIME_BASE + ")");
        }
        for (int type = CALLBACK_INPUT; type <= CALLBACK_LAST; type++) {
            doVsyncCallbacks(type, frameTimeNanos);
        }
    }

    /** AOSP doCallbacks: extract the phase's records that are due NOW (so a
     *  record an earlier phase just posted can still run in a later phase of
     *  this frame, while one a callback re-posts for its own phase waits for
     *  the next vsync), in due-time order, and run them outside the lock. */
    private void doVsyncCallbacks(int type, long frameTimeNanos) {
        java.util.ArrayList<FrameAction> due = null;
        synchronized (mLock) {
            // AOSP extracts at System.nanoTime()/NANOS_PER_MS, which on a device
            // is uptimeMillis(): the clock the due times were written in.
            final long now = android.os.SystemClock.uptimeMillis();
            java.util.Iterator<FrameAction> it = mVsyncQueue.iterator();
            while (it.hasNext()) {
                FrameAction a = it.next();
                if (a.type == type && a.dueNanos <= now) {
                    if (due == null) due = new java.util.ArrayList<>();
                    due.add(a);
                    it.remove();
                }
            }
            if (due == null) return;
            mCallbacksRunning = true;
        }
        // AOSP's CallbackQueue is kept sorted by due time; a stable sort of the
        // extracted records gives the same order.
        due.sort((x, y) -> Long.compare(x.dueNanos, y.dueNanos));
        try {
            for (FrameAction a : due) {
                try {
                    if (a.frameCb != null) {
                        a.frameCb.doFrame(frameTimeNanos);
                    } else if (a.vsyncCb != null) {
                        FrameData data = buildFrameData(frameTimeNanos);
                        try {
                            a.vsyncCb.onVsync(data);
                        } finally {
                            data.invalidate();
                        }
                    } else if (a.runnable != null) {
                        a.runnable.run();
                    }
                } catch (Throwable t) {
                    android.util.Log.e("Mac.apk.Choreo", "doFrame callback threw: " + t, t);
                }
            }
        } finally {
            synchronized (mLock) { mCallbacksRunning = false; }
        }
    }
}
