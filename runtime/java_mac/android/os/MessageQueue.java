// android.os.MessageQueue.
//
// TWO behaviors coexist, selected by MACAPK_REAL_LOOPER:
//
//  * Historical model (flag OFF -- the default): Mac.apk never ran a real
//    main loop. The Cocoa runloop + Handler.drainGlobal owned timing and
//    main-Handler posts went to Handler.GLOBAL_QUEUE drained per frame.
//    In that model this class is a near-stub: apps poke addIdleHandler()
//    on the object Looper.myQueue() hands back (no-ops) and nothing ever
//    calls next(). That path is preserved byte-for-byte.
//
//  * Real main loop (flag ON -- MACAPK_REAL_LOOPER=1): the MAIN looper's
//    queue (Looper.MAIN.getQueue()) is THIS object, and the macapk-activity
//    thread runs a real Looper.loop() that blocks in next() and dispatches
//    messages forever -- the Android execution model (Tier 2). This is the
//    real implementation below: an in-order (by `when`) singly-linked list
//    of Message, AOSP-faithful next()/enqueueMessage structure (see the
//    cited AOSP source in the methods), blocking on a plain Java monitor
//    rather than nativePollOnce.
//
// Why a Java monitor and NOT NativeLooper.pollOnce: real Android unifies a
// thread's Java MessageQueue and its NDK ALooper on ONE native poll loop,
// but doing that here would make the main loop SERVICE this thread's native
// ALooper fds. Unity registers its display/vsync fds during bring-up and is
// already driven from the OS main thread's Metal callback
// (__macapk_arm64_choreographer_fire); servicing the same fds again from the
// main loop would double-drive the engine. So the real main queue blocks on
// a monitor and is woken by enqueueMessage()/quit() only -- native fds stay
// exactly as serviced today. (Revisit in a later tier if a main-thread app
// ever needs ALooper_addFd delivery on the main looper.)
package android.os;

public final class MessageQueue {
    // ---- Historical idle-handler surface (kept for both modes) ----
    public interface IdleHandler { boolean queueIdle(); }
    public interface OnFileDescriptorEventListener {
        // AOSP MessageQueue.OnFileDescriptorEventListener event bits.
        int EVENT_INPUT  = 1 << 0;
        int EVENT_OUTPUT = 1 << 1;
        int EVENT_ERROR  = 1 << 2;
        int onFileDescriptorEvents(java.io.FileDescriptor fd, int events);
    }

    // ---- Real queue state (used only by the MACAPK_REAL_LOOPER main loop) ----
    /** Head of the in-order message list. Guarded by `this`. Mirrors AOSP
     *  MessageQueue.mMessages. */
    private Message mMessages;
    /** True while next() is blocked in wait() with nothing ready, so
     *  enqueueMessage knows whether a notify is needed (AOSP mBlocked). */
    private boolean mBlocked;
    /** Set by quit(); next() returns null (loop exits) once the queue
     *  drains. */
    private boolean mQuitting;
    /** AOSP MessageQueue.mQuitAllowed. The MAIN looper's queue is built with
     *  this FALSE so it can never be quit: AOSP's quit(boolean) throws
     *  IllegalStateException("Main thread not allowed to quit.") before
     *  touching any state, and Looper.prepareMainLooper() is the only path
     *  that constructs a non-quittable queue.
     *
     *  v1.0.833: we had no such guard, so any caller reaching Looper.quit()
     *  on MAIN silently set mQuitting on the process-wide singleton. mQuitting
     *  never resets, and enqueueMessage() returns false for every later post,
     *  so the queue accepts nothing again for the life of the process -- a
     *  LAUNCH_ACTIVITY posted afterwards is dropped and the activity never
     *  runs onCreate. That is the cold-install symptom ActivityHost's M3
     *  "main looper QUIT" branch reports (it was worded
     *  "main Looper.loop() returned (main looper quit)" until 2026-08-12, when
     *  it was split because it also fired on the healthy re-entrant path). Restoring the AOSP guard both prevents it
     *  and names the culprit, since the exception carries the caller's stack.
     *  Real callers exist: HandlerThread.quit()/quitSafely() and
     *  IntentService both call Looper.quit() on what they believe is a
     *  non-main looper. */
    private final boolean mQuitAllowed;

    /** Ordinary per-thread queue: quitting is allowed, as in AOSP's
     *  Looper.prepare() -> new MessageQueue(true). */
    MessageQueue() { this(true); }

    MessageQueue(boolean quitAllowed) { mQuitAllowed = quitAllowed; }

    /** Registered idle handlers, fired by next() when the queue is about to
     *  block because there is no message ready. Mirrors AOSP
     *  MessageQueue.mIdleHandlers (an ArrayList). Guarded by `this` for
     *  add/remove; next() snapshots it before running so a handler that
     *  re-registers does not deadlock on the monitor. */
    private final java.util.ArrayList<IdleHandler> mIdleHandlers =
        new java.util.ArrayList<>();
    /** Scratch array AOSP keeps so the snapshot allocation only happens once
     *  the queue actually idles (mPendingIdleHandlers). */
    private IdleHandler[] mPendingIdleHandlers;

    /** Next sync-barrier token. AOSP increments mNextBarrierToken on every
     *  postSyncBarrier so removeSyncBarrier can find the exact barrier. */
    private int mNextBarrierToken;

    /** Sentinel target for internal callback-only posts (the per-frame
     *  heartbeat from the Cocoa event source, and future internal frame
     *  messages). Lazily created so MessageQueue's class init does not
     *  recurse through Looper during Looper's own field initialization.
     *  Not used for app messages. */
    private static volatile Handler sInternalTarget;
    private static Handler internalTarget() {
        Handler h = sInternalTarget;
        if (h == null) {
            synchronized (MessageQueue.class) {
                h = sInternalTarget;
                if (h == null) {
                    h = new Handler(Looper.getMainLooper());
                    sInternalTarget = h;
                }
            }
        }
        return h;
    }

    /** AOSP MessageQueue.addOnFileDescriptorEventListener: register a listener
     *  for I/O events on a raw fd, serviced by this thread's native ALooper.
     *  v1.0.464 (subsystem-04 close-list): the OnFileDescriptorEventListener
     *  interface already existed but the add/remove methods were absent
     *  (NoSuchMethodError for any caller). Mac.apk does NOT wire the Java
     *  MessageQueue to this thread's native ALooper fd set (see the file
     *  header: native fds are serviced separately to avoid double-driving
     *  engines like Unity), so registration is recorded but no Java-side fd
     *  callback is delivered -- the honest "fd delivery on the Java queue
     *  unsupported" contract rather than a link error. The methods exist so
     *  apps that defensively register an fd listener still link and run. */
    public void addOnFileDescriptorEventListener(java.io.FileDescriptor fd,
                                                 int events,
                                                 OnFileDescriptorEventListener listener) {
        // No-op: Java-side fd delivery is not modeled (native ALooper owns fds).
    }
    public void removeOnFileDescriptorEventListener(java.io.FileDescriptor fd) {
        // No-op: nothing was registered on the Java queue.
    }

    public boolean isIdle() {
        synchronized (this) {
            final long now = SystemClock.uptimeMillis();
            return mMessages == null || now < mMessages.when;
        }
    }

    /** AOSP MessageQueue.addIdleHandler: register a handler invoked when the
     *  queue is about to block waiting for more messages. AOSP throws NPE on
     *  a null handler; we mirror that so callers see the same contract. The
     *  handler stays registered until it returns false from queueIdle() or
     *  is explicitly removed. */
    public void addIdleHandler(IdleHandler h) {
        if (h == null) {
            throw new NullPointerException("Can't add a null IdleHandler");
        }
        synchronized (this) { mIdleHandlers.add(h); }
    }

    /** AOSP MessageQueue.removeIdleHandler: drops a previously-registered
     *  handler. Silently does nothing if it was never added (AOSP semantics).
     *  This may be called from inside queueIdle() (a handler removing itself),
     *  which is safe because next() iterates a snapshot, not mIdleHandlers. */
    public void removeIdleHandler(IdleHandler h) {
        synchronized (this) { mIdleHandlers.remove(h); }
    }

    /** Pull the next ready message, blocking until one is due or the queue
     *  quits (then returns null). Mirrors AOSP
     *  frameworks/base/.../LegacyMessageQueue/MessageQueue.java next():
     *  walk `mMessages` (ordered by `when`), skip a sync barrier
     *  (target==null with no callback) to the next async message, return
     *  the head when due, else block for `Math.min(msg.when - now,
     *  Integer.MAX_VALUE)`.
     *
     *  Two deliberate divergences from AOSP, both forced by using a plain
     *  Java monitor instead of nativePollOnce:
     *   (1) Block primitive: wait() on `this`, not nativePollOnce -- see the
     *       file header for why we do not poll this thread's native fds.
     *   (2) Lost-wakeup safety: AOSP's nativeWake is EDGE-LATCHED (a wake
     *       byte written before the next nativePollOnce still wakes it), so
     *       AOSP can compute a timeout, drop the lock, and poll in separate
     *       steps. Object.notify has NO such latch -- a notify with no waiter
     *       is lost. So the message check and the wait() MUST sit in the SAME
     *       synchronized region (we hold `this` from check through entering
     *       wait()); enqueueMessage/quit also take `this`, so a notify can
     *       never slip between our check and our wait. */
    Message next() {
        // AOSP-faithful idle bookkeeping: pendingIdleHandlerCount starts at -1
        // and is only computed the first time the queue idles inside a single
        // next() call (so idle handlers fire at most once per blocking point).
        int pendingIdleHandlerCount = -1;
        synchronized (this) {
            for (;;) {
                final long now = SystemClock.uptimeMillis();
                Message prevMsg = null;
                Message msg = mMessages;
                // Skip a sync barrier (target-less, callback-less) to the
                // next asynchronous message. We never enqueue real barriers;
                // internal callback posts carry callback!=null so they are
                // NOT treated as barriers (the extra `msg.callback == null`
                // guard vs AOSP's bare `msg.target == null`).
                if (msg != null && msg.target == null && msg.callback == null) {
                    do {
                        prevMsg = msg;
                        msg = msg.next;
                    } while (msg != null && !msg.isAsynchronous());
                }
                if (msg != null && now >= msg.when) {
                    // Ready: unlink and return it.
                    mBlocked = false;
                    if (prevMsg != null) prevMsg.next = msg.next;
                    else                 mMessages = msg.next;
                    msg.next = null;
                    msg.markInUse();
                    return msg;
                }
                // Nothing ready. Quit drains to null once observed.
                if (mQuitting) return null;

                // First idle pass for this blocking point: snapshot the idle
                // handlers so we run them outside the data-structure walk.
                // AOSP MessageQueue.next() does exactly this (mPendingIdle
                // Handlers + pendingIdleHandlerCount). Only consider running
                // idle handlers when there is no message due yet (msg==null
                // means empty; msg!=null with now<msg.when means head not due).
                if (pendingIdleHandlerCount < 0
                        && (mMessages == null || now < mMessages.when)) {
                    pendingIdleHandlerCount = mIdleHandlers.size();
                }
                if (pendingIdleHandlerCount <= 0) {
                    // No idle handlers to run -- go to sleep.
                    mBlocked = true;
                    try {
                        if (msg != null) {
                            // Head not due yet: wake when it becomes due.
                            wait(Math.min(msg.when - now, Integer.MAX_VALUE));
                        } else {
                            // Empty: block until enqueueMessage/quit notifies.
                            wait();
                        }
                    } catch (InterruptedException ignored) {}
                    mBlocked = false;
                    // Loop: re-check (a notify, a timeout, or a spurious wakeup).
                    continue;
                }
                if (mPendingIdleHandlers == null) {
                    mPendingIdleHandlers =
                        new IdleHandler[Math.max(pendingIdleHandlerCount, 4)];
                }
                mPendingIdleHandlers =
                    mIdleHandlers.toArray(mPendingIdleHandlers);

                // Run idle handlers WITHOUT holding the monitor: AOSP runs them
                // outside the lock so a handler may post messages / add or remove
                // handlers / call back into the queue without deadlocking.
                for (int i = 0; i < pendingIdleHandlerCount; i++) {
                    final IdleHandler idler = mPendingIdleHandlers[i];
                    mPendingIdleHandlers[i] = null; // release the reference.
                    boolean keep = false;
                    try {
                        keep = idler.queueIdle();
                    } catch (Throwable t) {
                        // AOSP lets the exception propagate; we swallow it so a
                        // misbehaving handler cannot tear down the main loop.
                    }
                    if (!keep) {
                        synchronized (this) { mIdleHandlers.remove(idler); }
                    }
                }
                // Reset so we never run idle handlers more than once per call
                // (AOSP sets pendingIdleHandlerCount = 0 here), and loop with
                // now=0 so a message an idler just posted is picked up promptly.
                pendingIdleHandlerCount = 0;
            }
        }
    }

    /** v1.0.575: NON-BLOCKING pull of the next DUE message (when <= now), or
     *  null if none is due. Unlike next() it never waits and never runs idle
     *  handlers. Used by Looper.macapkDrainMainDue so the OS-main render thread
     *  can keep the real main queue moving WHILE the macapk-activity main loop is
     *  blocked in a native lifecycle handshake (AGDK
     *  android_app_set_activity_state), so a cross-thread runOnUiThread post that
     *  a blocked android_main is waiting on (Minecraft's runNativeCallbackOnUiThread
     *  FutureTask) still runs. Skips a sync barrier exactly like next(). */
    Message macapkPollDue() {
        synchronized (this) {
            final long now = SystemClock.uptimeMillis();
            Message prevMsg = null;
            Message msg = mMessages;
            if (msg != null && msg.target == null && msg.callback == null) {
                do { prevMsg = msg; msg = msg.next; }
                while (msg != null && !msg.isAsynchronous());
            }
            if (msg != null && now >= msg.when) {
                mBlocked = false;
                if (prevMsg != null) prevMsg.next = msg.next;
                else                 mMessages = msg.next;
                msg.next = null;
                msg.markInUse();
                return msg;
            }
            return null;
        }
    }

    /** MAC.APK: the drain variant used by Looper.macapkDrainMainDue on the
     *  OS-main render thread. Identical to macapkPollDue but PASSES OVER
     *  owner-only messages (msg.target.mMacapkOwnerOnly) -- leaving them queued,
     *  in order -- so ViewCanvasDriver.doFrame / doSurfaceChanged, which mutate
     *  the view tree, are never dispatched off the owner thread. The AGDK
     *  runNativeCallbackOnUiThread FutureTask is NOT owner-only, so it still
     *  drains (the v1.0.575 deadlock fix is preserved). Under a sync barrier
     *  only asynchronous messages are eligible, and owner-only posts are
     *  synchronous, so the barrier candidate is returned immediately and the
     *  skip walk never advances past it. The owner loop keeps using
     *  macapkPollDue() (unchanged), so it still runs every message. */
    Message macapkPollDueSkippingOwnerOnly() {
        synchronized (this) {
            final long now = SystemClock.uptimeMillis();
            Message prevMsg = null;
            Message msg = mMessages;
            if (msg != null && msg.target == null && msg.callback == null) {
                do { prevMsg = msg; msg = msg.next; }
                while (msg != null && !msg.isAsynchronous());
            }
            while (msg != null && now >= msg.when) {
                if (msg.target == null || !msg.target.mMacapkOwnerOnly) {
                    mBlocked = false;
                    if (prevMsg != null) prevMsg.next = msg.next;
                    else                 mMessages = msg.next;
                    msg.next = null;
                    msg.markInUse();
                    return msg;
                }
                prevMsg = msg;
                msg = msg.next;
            }
            return null;
        }
    }

    // ---- TestLooperManager support (AOSP MessageQueue's package-private
    // pollForTest / peekWhenForTest / isBlockedOnSyncBarrier, API 36). All
    // three are documented "never blocks" and, unlike next()/macapkPollDue(),
    // they do NOT wait for a message to become due -- an instrumentation test
    // inspects messages scheduled into the future.

    /** Remove and return the head message (skipping a sync barrier the same
     *  way next() does), regardless of its `when`. Null when empty. */
    Message pollForTest() {
        synchronized (this) {
            Message prevMsg = null;
            Message msg = mMessages;
            if (msg != null && msg.target == null && msg.callback == null) {
                do { prevMsg = msg; msg = msg.next; }
                while (msg != null && !msg.isAsynchronous());
            }
            if (msg == null) return null;
            mBlocked = false;
            if (prevMsg != null) prevMsg.next = msg.next;
            else                 mMessages = msg.next;
            msg.next = null;
            msg.markInUse();
            return msg;
        }
    }

    /** The head message's `when`, without removing it; null when empty. */
    Long peekWhenForTest() {
        synchronized (this) {
            Message msg = mMessages;
            if (msg != null && msg.target == null && msg.callback == null) {
                do { msg = msg.next; } while (msg != null && !msg.isAsynchronous());
            }
            return (msg == null) ? null : Long.valueOf(msg.when);
        }
    }

    /** True when the head of the queue is a sync barrier and no asynchronous
     *  message sits behind it -- i.e. the loop cannot make progress until the
     *  barrier is removed. */
    boolean isBlockedOnSyncBarrier() {
        synchronized (this) {
            Message msg = mMessages;
            if (msg == null || msg.target != null || msg.callback != null) return false;
            Message m = msg.next;
            while (m != null && !m.isAsynchronous()) m = m.next;
            return m == null;
        }
    }

    /** Insert `msg` ordered by `when` and wake next() if it is blocked and
     *  the new message could change what it should do next. Mirrors AOSP
     *  enqueueMessage (without the tail-tracking flag branch / in-use
     *  assertions we do not model). Returns false if the queue is quitting.
     *  Permissive on target: AOSP throws when target==null (a barrier must
     *  be posted via postSyncBarrier), but our internal posts carry a
     *  callback with a sentinel target, so the only target-less messages
     *  that ever reach here are well-formed callback messages. */
    boolean enqueueMessage(Message msg, long when) {
        if (msg == null) return false;
        boolean wake = false;
        synchronized (this) {
            // AOSP enqueueMessage (:555-557) refuses a message that is already
            // queued or mid-delivery, because the intrusive `next` pointer can
            // only put it in one place: re-sending a live message would splice
            // the queue into a cycle. HDL.introspect.resendInUse.
            if (msg.isInUse()) {
                throw new IllegalStateException(msg + " This message is already in use.");
            }
            if (mQuitting) {
                // AOSP (:559-565) logs, recycles the message and reports failure
                // -- "sending message to a Handler on a dead thread". The
                // recycle is safe here precisely because it is not in use.
                msg.recycle();
                return false;
            }
            msg.markInUse();
            msg.when = when;
            Message p = mMessages;
            boolean needWake;
            if (p == null || when == 0 || when < p.when) {
                // New head: wake if the loop is blocked.
                msg.next = p;
                mMessages = msg;
                needWake = mBlocked;
            } else {
                // Insert at tail or middle. Only need to wake when a barrier
                // sits at the head and this is the earliest async message.
                needWake = mBlocked && p.target == null && p.callback == null
                           && msg.isAsynchronous();
                Message prev;
                for (;;) {
                    prev = p;
                    p = p.next;
                    if (p == null || when < p.when) break;
                    if (needWake && p.isAsynchronous()) needWake = false;
                }
                msg.next = p;       // invariant: p == prev.next
                prev.next = msg;
            }
            if (needWake) notifyAll();
            wake = true;
        }
        // Outside the monitor: the waker may be a NATIVE call (see setWaker),
        // and holding this queue's lock across it would block every other
        // enqueuer for its duration.
        if (wake) fireWaker();
        return true;
    }

    /** Wake hook for a queue whose loop does NOT block on this monitor.
     *
     *  The main queue blocks inside next()'s wait(), so notifyAll() is its wake
     *  primitive and this stays null. A PER-THREAD looper is different: its
     *  loop has to block in the native ALooper poll instead, so that thread's
     *  registered fds (Unity's vsync pipe) are serviced on their own thread --
     *  that is what NativeLooper exists for. Real Android has exactly this
     *  shape: MessageQueue.nativePollOnce IS the ALooper poll and nativeWake IS
     *  the wake, so routing our wake through the same pipe is the faithful
     *  arrangement rather than a workaround.
     *
     *  Safety: the native wake is EDGE-LATCHED (a wake written before the next
     *  poll still wakes it), which is what lets the per-thread loop compute a
     *  timeout, drop this monitor, and poll in separate steps. Object.notify
     *  has no such latch, which is why next() may NOT be restructured the same
     *  way -- see next()'s own javadoc.
     *
     *  mWaker MUST stay volatile: Looper.prepare() assigns the looper's native
     *  pointer and only THEN publishes the waker here, so the volatile
     *  write/read pair is what guarantees an enqueuing thread that sees a
     *  non-null waker also sees the pointer it will dereference. */
    private volatile Runnable mWaker;
    void setWaker(Runnable w) { mWaker = w; }
    private void fireWaker() {
        Runnable w = mWaker;
        if (w != null) {
            try { w.run(); } catch (Throwable ignored) { /* poll timeout covers it */ }
        }
    }

    /** How long a caller that blocks OUTSIDE this monitor may sleep before it
     *  must look again: -1 = nothing queued (block until woken), 0 = something
     *  is due right now (do not block), n = at most n ms until the head comes
     *  due. This is AOSP's nextPollTimeoutMillis, lifted out of next() so the
     *  per-thread loop can feed it to the native poll. Skips a sync barrier to
     *  the first async message exactly like next(). Reports 0 while quitting so
     *  the loop notices promptly instead of sleeping on a dead queue. */
    int nextPollTimeoutMillis() {
        synchronized (this) {
            if (mQuitting) return 0;
            final long now = SystemClock.uptimeMillis();
            Message msg = mMessages;
            if (msg != null && msg.target == null && msg.callback == null) {
                do { msg = msg.next; } while (msg != null && !msg.isAsynchronous());
            }
            if (msg == null) return -1;
            if (now >= msg.when) return 0;
            return (int) Math.min(msg.when - now, Integer.MAX_VALUE);
        }
    }

    boolean isQuitting() {
        synchronized (this) { return mQuitting; }
    }

    /** Monitor-based block, for a per-thread looper that has NO native looper
     *  bound (the defensive fallback -- prepare() normally binds one). Re-checks
     *  under the monitor before waiting, so a wake that landed between the
     *  caller's timeout computation and here cannot be lost, and sets mBlocked
     *  so enqueueMessage's needWake test notifies us. */
    void awaitOnMonitor(int timeoutMillis) {
        synchronized (this) {
            if (nextPollTimeoutMillis() == 0) return;
            mBlocked = true;
            try {
                wait(timeoutMillis < 0 ? 0 : timeoutMillis);
            } catch (InterruptedException ignored) {
            } finally {
                mBlocked = false;
            }
        }
    }

    /** Run the registered idle handlers once, the way next() does at its own
     *  blocking point, and report whether any ran (the caller must then re-check
     *  the queue, because a handler is allowed to post). Exists so the
     *  per-thread loop -- which cannot call next(), see setWaker -- still
     *  honours addIdleHandler instead of registering handlers that never fire.
     *  Handlers run WITHOUT the monitor held so they may post / add / remove. */
    boolean runIdleHandlers() {
        final IdleHandler[] snapshot;
        final int count;
        synchronized (this) {
            count = mIdleHandlers.size();
            if (count <= 0) return false;
            if (mPendingIdleHandlers == null) {
                mPendingIdleHandlers = new IdleHandler[Math.max(count, 4)];
            }
            snapshot = mIdleHandlers.toArray(mPendingIdleHandlers);
            mPendingIdleHandlers = snapshot;
        }
        for (int i = 0; i < count; i++) {
            final IdleHandler idler = snapshot[i];
            snapshot[i] = null;   // release the reference
            if (idler == null) continue;
            boolean keep = false;
            try {
                keep = idler.queueIdle();
            } catch (Throwable t) {
                // Match next(): a misbehaving idle handler must not tear the
                // loop down.
            }
            if (!keep) {
                synchronized (this) { mIdleHandlers.remove(idler); }
            }
        }
        return true;
    }

    /** Legacy package-private name kept for any caller of the old stub. */
    boolean enqueue(Message m, long when) { return enqueueMessage(m, when); }

    /** Enqueue an internal callback to run on the main loop ASAP. Used by
     *  the Cocoa event source (per-frame heartbeat) and future internal
     *  frame messages. Wraps `r` in a callback message with the sentinel
     *  target so next() does not mistake it for a sync barrier. */
    void macapkPost(Runnable r) {
        if (r == null) return;
        Message m = Message.obtain(internalTarget(), r);
        enqueueMessage(m, SystemClock.uptimeMillis());
    }

    /** Remove all pending callbacks identical to `r` (AOSP
     *  removeCallbacks semantics: Runnable identity). */
    // The three removal walks below all follow AOSP's shape: strip the matching
    // run at the FRONT, then splice matches out of the tail, recycling every
    // message unlinked. The recycle is not bookkeeping for its own sake -- it
    // releases the message's obj/target/callback references (so a removed
    // message cannot pin an Activity) and leaves FLAG_IN_USE set, which is what
    // makes re-sending a message that was already removed fail loudly instead
    // of corrupting the list.

    /** Remove all pending callbacks identical to `r` (AOSP
     *  removeCallbacks semantics: Runnable identity). */
    void removeCallbacks(Runnable r) {
        if (r == null) return;
        synchronized (this) {
            Message p = mMessages;
            while (p != null && p.callback == r) {
                Message n = p.next;
                mMessages = n;
                p.next = null;
                p.recycleUnchecked();
                p = n;
            }
            while (p != null) {
                Message n = p.next;
                if (n != null && n.callback == r) {
                    Message nn = n.next;
                    n.next = null;
                    n.recycleUnchecked();
                    p.next = nn;
                    continue;
                }
                p = n;
            }
        }
    }

    /** Remove all pending messages with `what` from `target`. */
    void removeMessages(Handler target, int what, Object object) {
        if (target == null) return;
        synchronized (this) {
            Message p = mMessages;
            while (p != null && p.target == target && p.what == what
                   && (object == null || p.obj == object)) {
                Message n = p.next;
                mMessages = n;
                p.next = null;
                p.recycleUnchecked();
                p = n;
            }
            while (p != null) {
                Message n = p.next;
                if (n != null && n.target == target && n.what == what
                    && (object == null || n.obj == object)) {
                    Message nn = n.next;
                    n.next = null;
                    n.recycleUnchecked();
                    p.next = nn;
                    continue;
                }
                p = n;
            }
        }
    }

    /** Remove pending callbacks identical to `r` that ALSO carry obj==token
     *  (AOSP removeCallbacks(Runnable, Object)). A null token matches any,
     *  which is why Handler routes that case to removeCallbacks(r) instead. */
    void removeCallbacks(Handler target, Runnable r, Object token) {
        if (r == null) return;
        synchronized (this) {
            Message p = mMessages;
            while (p != null && p.callback == r
                   && (target == null || p.target == target)
                   && (token == null || p.obj == token)) {
                Message n = p.next;
                mMessages = n;
                p.next = null;
                p.recycleUnchecked();
                p = n;
            }
            while (p != null) {
                Message n = p.next;
                if (n != null && n.callback == r
                    && (target == null || n.target == target)
                    && (token == null || n.obj == token)) {
                    Message nn = n.next;
                    n.next = null;
                    n.recycleUnchecked();
                    p.next = nn;
                    continue;
                }
                p = n;
            }
        }
    }

    /** Remove every pending message/callback posted by `target` (AOSP
     *  removeCallbacksAndMessages): token==null removes all of target's,
     *  else only those whose obj==token. */
    void removeCallbacksAndMessages(Handler target, Object token) {
        if (target == null) return;
        synchronized (this) {
            Message p = mMessages;
            while (p != null && p.target == target
                   && (token == null || p.obj == token)) {
                Message n = p.next;
                mMessages = n;
                p.next = null;
                p.recycleUnchecked();
                p = n;
            }
            while (p != null) {
                Message n = p.next;
                if (n != null && n.target == target
                    && (token == null || n.obj == token)) {
                    Message nn = n.next;
                    n.next = null;
                    n.recycleUnchecked();
                    p.next = nn;
                    continue;
                }
                p = n;
            }
        }
    }

    boolean hasMessages(Handler target, int what, Object object) {
        if (target == null) return false;
        synchronized (this) {
            for (Message p = mMessages; p != null; p = p.next) {
                if (p.target == target && p.what == what
                    && (object == null || p.obj == object)) return true;
            }
            return false;
        }
    }

    boolean hasCallback(Runnable r) {
        if (r == null) return false;
        synchronized (this) {
            for (Message p = mMessages; p != null; p = p.next) {
                if (p.callback == r) return true;
            }
            return false;
        }
    }

    /** Stop the loop. next() returns null once the queue is observed
     *  quitting (after the current head drains). next() still returns any
     *  already-due message before reporting null, so in practice this drains
     *  due work then stops -- preserved byte-for-byte from prior behavior. */
    void quit() {
        // AOSP checks this BEFORE taking the lock or mutating anything, so a
        // refused quit leaves the queue exactly as it was.
        if (!mQuitAllowed) {
            throw new IllegalStateException("Main thread not allowed to quit.");
        }
        boolean wake = false;
        synchronized (this) {
            // AOSP quit(boolean) (:425-445) is idempotent -- a second quit()
            // returns without re-running the teardown (HDL.quit.quitTwice) --
            // and the UNSAFE quit DISCARDS every pending message. Ours left them
            // queued, so a message still pending across a quit stayed
            // observable to hasMessages() (HDL.quit.pendingAfter).
            if (mQuitting) return;
            mQuitting = true;
            removeAllMessagesLocked();
            notifyAll();
            wake = true;
        }
        if (wake) fireWaker();
    }

    /** AOSP removeAllMessagesLocked (:876-884): drop and recycle everything. */
    private void removeAllMessagesLocked() {
        Message p = mMessages;
        while (p != null) {
            Message n = p.next;
            p.next = null;
            p.recycleUnchecked();
            p = n;
        }
        mMessages = null;
    }

    /** AOSP removeAllFutureMessagesLocked (:886-912): keep the already-due
     *  prefix so it still dispatches, drop and recycle everything after it. */
    private void removeAllFutureMessagesLocked() {
        final long now = SystemClock.uptimeMillis();
        Message p = mMessages;
        if (p == null) return;
        if (p.when > now) {
            removeAllMessagesLocked();
            return;
        }
        Message n;
        for (;;) {
            n = p.next;
            if (n == null) return;
            if (n.when > now) break;
            p = n;
        }
        p.next = null;
        do {
            p = n;
            n = p.next;
            p.next = null;
            p.recycleUnchecked();
        } while (n != null);
    }

    /** AOSP MessageQueue.quit(true) -- the "safe" quit Looper.quitSafely uses:
     *  stop accepting new messages but let already-due (non-delayed) messages
     *  drain first; only FUTURE (delayed) messages are discarded. next() then
     *  dispatches the remaining due messages and returns null once they are
     *  gone. v1.0.464 (subsystem-04 close-list): Looper.quitSafely previously
     *  aliased the unsafe quit(), dropping in-flight non-delayed work. */
    void quitSafely() {
        if (!mQuitAllowed) {
            throw new IllegalStateException("Main thread not allowed to quit.");
        }
        boolean wake = false;
        synchronized (this) {
            if (mQuitting) return;
            mQuitting = true;
            removeAllFutureMessagesLocked();
            notifyAll();
            wake = true;
        }
        if (wake) fireWaker();
    }

    /** AOSP MessageQueue.postSyncBarrier() (hidden, @UnsupportedAppUsage).
     *  Inserts a barrier (a message with a null target) at the given time so
     *  next() will then skip every SYNCHRONOUS message after it and only
     *  dispatch asynchronous ones. Used by ViewRootImpl to defer ordinary UI
     *  work until the next vsync while still letting async input/animation
     *  messages through. The barrier carries a unique token in arg1 so it can
     *  be removed later. Returns the token. Mirrors AOSP behavior:
     *  frameworks/base/.../MessageQueue.postSyncBarrier(long when).
     *
     *  Note our next() treats a barrier as "target==null AND callback==null"
     *  (internal callback posts carry a callback, so they are not barriers);
     *  a barrier message therefore has no target and no callback. */
    public int postSyncBarrier() {
        return postSyncBarrier(SystemClock.uptimeMillis());
    }
    private int postSyncBarrier(long when) {
        synchronized (this) {
            final int token = mNextBarrierToken++;
            // A barrier is a message with NO target and NO callback (so next()
            // skips synchronous messages after it) carrying the token in arg1.
            final Message msg = Message.obtain();
            // AOSP marks the barrier in use as it inserts it (postSyncBarrier),
            // because it goes straight into the list without passing through
            // enqueueMessage.
            msg.markInUse();
            msg.target = null;
            msg.callback = null;
            msg.arg1 = token;
            msg.when = when;
            // Insert ordered by `when`, like a normal message; the barrier
            // itself is never dispatched (next() walks past it).
            Message prev = null;
            Message p = mMessages;
            if (when != 0) {
                while (p != null && p.when <= when) { prev = p; p = p.next; }
            }
            if (prev != null) { msg.next = p; prev.next = msg; }
            else              { msg.next = p; mMessages = msg; }
            return token;
        }
    }

    /** AOSP MessageQueue.removeSyncBarrier(int token): remove the barrier with
     *  the given token. If removing it un-blocks the head and the loop is
     *  blocked, wake it. Throws IllegalStateException if the barrier is not
     *  found (AOSP throws the same -- a token must only be removed once). */
    public void removeSyncBarrier(int token) {
        synchronized (this) {
            Message prev = null;
            Message p = mMessages;
            while (p != null
                   && (p.target != null || p.callback != null || p.arg1 != token)) {
                prev = p;
                p = p.next;
            }
            if (p == null) {
                throw new IllegalStateException(
                    "The specified message queue synchronization "
                    + "barrier token has not been posted or has already been removed.");
            }
            final boolean needWake;
            if (prev != null) {
                prev.next = p.next;
                needWake = false;
            } else {
                mMessages = p.next;
                needWake = mMessages == null || mMessages.target != null;
            }
            p.next = null;
            p.recycleUnchecked();   // AOSP disposes the barrier it just removed
            if (needWake && mBlocked) notifyAll();
        }
    }
}
