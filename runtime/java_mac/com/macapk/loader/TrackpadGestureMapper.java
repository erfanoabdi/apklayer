package com.macapk.loader;

/** HOST TRACKPAD CONTACTS -> Android multi-pointer touch streams.
 *
 *  The macOS trackpad reports every finger as an indirect NSTouch (normalized
 *  position on the pad, plus a phase). Mac.apk used to turn none of them into
 *  pointers: a single finger is the mouse (unchanged here), a two-finger swipe
 *  becomes the synthesized scroll drag and a pinch the synthesized two-pointer
 *  pinch (cocoa_bridge.mm scrollWheel: / magnifyWithEvent:), and a two-finger
 *  HOLD produced nothing at all. So any app that needs two pointers DOWN at
 *  once -- PianOli's settings lock (AppConfigTrigger, two config keys held),
 *  "hold two fingers" gestures, a two-finger rotate -- was unreachable.
 *
 *  WHAT IS AOSP AND WHAT IS NOT (be exact -- the first draft of this header
 *  attributed all of it to AOSP, and review proved otherwise).
 *
 *  Taken from AOSP's TouchInputMapper pointer mode (frameworks/native
 *  services/inputflinger/reader/mapper/TouchInputMapper.cpp), for the
 *  CONSTANTS and the PLACEMENT of pointers:
 *   - InputReaderBase.h:289-294 `pointerGestureMultitouchSettleInterval(100 *
 *     1000000LL)` ("Time after the first touch points go down to settle on an
 *     initial centroid ... enough time to handle cases where the user puts
 *     down two fingers at almost but not quite exactly the same time"),
 *     `pointerGestureMultitouchMinDistance(15)` (pixels) and
 *     `pointerGestureZoomSpeedRatio(0.3f)` ("a full swipe of the touch pad
 *     diagonal in zoom mode will cover this portion of the display diagonal").
 *   - FREEFORM places one pointer per finger AROUND THE CURSOR:
 *       x = referenceGestureX + (pointer.x - referenceTouchX) * mPointerXZoomScale
 *     (referenceTouch = centroid of the touching fingers when the gesture
 *     (re)started, referenceGesture = the cursor) with
 *       mPointerXZoomScale = pointerGestureZoomSpeedRatio * displayDiagonal / rawDiagonal
 *     (TouchInputMapper.cpp:1070). Ids are the lowest free ids
 *     (markFirstUnmarkedBit), DOWN for the first and POINTER_DOWN for each
 *     later one, pointer index = rank of id.
 *   - A finger added before the gesture settles restarts it
 *     (TouchInputMapper.cpp:3086-3093 "Additional pointers have gone down but
 *     not yet settled. Reset the gesture.").
 *   - Motion of at least 15 display pixels is what AOSP reads as a SWIPE or
 *     FREEFORM rather than a press (:3165 `if (distOverThreshold >= 2)`). Here
 *     macOS's own recognizer owns those (scrollWheel: = a two-finger swipe,
 *     which AOSP's GestureConverter::handleScroll also delivers as ONE fake
 *     finger; magnifyWithEvent: = handlePinch's two fake fingers), so a contact
 *     that MOVES that far before it settles is left to macOS and never becomes
 *     a hold, and a recognized scroll/pinch arriving after a hold engaged
 *     CANCELS it (claim()), the way AOSP cancels a PRESS that turns into
 *     another gesture.
 *
 *  DELIBERATE DIFFERENCES FROM AOSP (Mac.apk choices, not AOSP behaviour):
 *   0a. TWO POINTERS FOR A STILL CONTACT. AOSP pointer mode treats a still
 *       two-finger contact as the PRESS hypothesis and reports ONE pointer, at
 *       the cursor (TouchInputMapper.cpp:3068-3072 "When there are two fingers
 *       we start with the PRESS hypothesis and we generate a down at the
 *       pointer location"; PRESS reports one pointer at referenceGesture,
 *       :3274-3290). FREEFORM (two pointers) needs at least two fingers moved
 *       past 15 px AND more than two fingers / a wide spread / divergent
 *       directions (:3165 and :3151-3231). Modern Android 14+ touchpads
 *       (gestures/GestureConverter.cpp) have no hold gesture at all and map a
 *       physical right click to AMOTION_EVENT_BUTTON_SECONDARY (:51-52). The
 *       brief needs two pointers DOWN for a still contact (PianOli's lock, a
 *       touchscreen contract), so a still contact is delivered the way a
 *       touchscreen would deliver it.
 *   0b. NOTHING IS EMITTED UNTIL THE 100 ms SETTLE. AOSP generates the PRESS
 *       down at once; its settle interval only resets the gesture when more
 *       fingers land. Waiting is ours: it is what keeps a two-finger swipe or
 *       pinch (macOS-recognized, a few frames later) from ever becoming a hold.
 *       The burst that ends the wait is stamped with the time the fingers
 *       LANDED (eventTime and downTime), so the wait never shortens the touch
 *       the app measures (GitHub #23).
 *   1.  Once a hold has engaged, every finger keeps its own pointer until IT
 *       lifts (NSTouch phase ended -> POINTER_UP / UP), which is the
 *       touchscreen contract (TouchInputMapper DIRECT mode). Pointer-mode
 *       FREEFORM instead finishes the whole gesture when one finger remains,
 *       which would make "hold one key, lift the other finger, put it down on
 *       a second key" impossible.
 *   2.  A host mouse button taking two resting fingers (a physical two-finger
 *       click, macOS's default secondary click) cancels / suppresses them
 *       instead of tapping the item under the cursor (claim()).
 *
 *  Pure state machine: no Android or AppKit types, so debug/trackpad_gate.sh
 *  drives it in a bare JVM. TouchDispatcher feeds it and delivers its output.
 *  Not thread-safe; TouchDispatcher serialises every call on its own lock. */
final class TrackpadGestureMapper {

    /** InputReaderBase.h pointerGestureMultitouchSettleInterval (100 ms). */
    static final long  SETTLE_MS = 100L;
    /** InputReaderBase.h pointerGestureMultitouchMinDistance (15 px). */
    static final float MIN_DISTANCE_PX = 15f;
    /** InputReaderBase.h pointerGestureZoomSpeedRatio (0.3). */
    static final float ZOOM_SPEED_RATIO = 0.3f;
    /** Pad size used when the host reports none (a 14" MacBook Pro trackpad
     *  is ~147 x 91 mm). Only ever a fallback; NSTouch.deviceSize is real. */
    static final float FALLBACK_PAD_W_MM = 147f, FALLBACK_PAD_H_MM = 91f;

    // NSTouchPhase, flattened by cocoa_bridge.mm (one value per contact).
    static final int PH_BEGAN = 0, PH_MOVED = 1, PH_STATIONARY = 2,
                     PH_ENDED = 3, PH_CANCELLED = 4;

    // MotionEvent action constants, duplicated so this class stays pure.
    static final int ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2, ACTION_CANCEL = 3,
                     ACTION_POINTER_DOWN = 5, ACTION_POINTER_UP = 6;

    /** One MotionEvent to deliver. Coordinates are SURFACE pixels (top-left
     *  origin), the space TouchDispatcher's host-mouse path uses. `index` is
     *  the pointer index the POINTER_DOWN/UP is about (0 otherwise).
     *  `eventTime` is when it physically happened in uptimeMillis, or 0 for
     *  "now" (stamped at delivery). */
    static final class Motion {
        final int action, index;
        final long downTime, eventTime;
        final int[] ids;
        final float[] xs, ys;
        Motion(int action, int index, long downTime, int[] ids, float[] xs, float[] ys) {
            this(action, index, downTime, 0L, ids, xs, ys);
        }
        Motion(int action, int index, long downTime, long eventTime, int[] ids, float[] xs, float[] ys) {
            this.action = action; this.index = index; this.downTime = downTime; this.eventTime = eventTime;
            this.ids = ids; this.xs = xs; this.ys = ys;
        }
        @Override public String toString() {
            StringBuilder b = new StringBuilder(name(action));
            if (action == ACTION_POINTER_DOWN || action == ACTION_POINTER_UP) b.append('@').append(index);
            b.append('[');
            for (int i = 0; i < ids.length; i++) {
                if (i > 0) b.append(' ');
                b.append(ids[i]).append(':').append(Math.round(xs[i])).append(',').append(Math.round(ys[i]));
            }
            return b.append(']').toString();
        }
        static String name(int a) {
            switch (a) {
                case ACTION_DOWN: return "DOWN";
                case ACTION_UP: return "UP";
                case ACTION_MOVE: return "MOVE";
                case ACTION_CANCEL: return "CANCEL";
                case ACTION_POINTER_DOWN: return "POINTER_DOWN";
                case ACTION_POINTER_UP: return "POINTER_UP";
                default: return "A" + a;
            }
        }
    }

    interface Sink { void motion(Motion m); }

    private static final int MAX = 10;
    // Contacts currently on the pad, in landing order.
    private final int[]   cId   = new int[MAX];
    private final float[] cX    = new float[MAX], cY = new float[MAX];   // normalized, y down
    private final float[] c0X   = new float[MAX], c0Y = new float[MAX];  // where it stood at the settle start
    private final int[]   cPtr  = new int[MAX];                          // pointer id, -1 = none
    private int n;

    private boolean engaged;      // a hold is live: contacts own pointers
    private boolean suppressed;   // macOS / the mouse claimed this contact session
    private boolean moving;       // moved past MIN_DISTANCE before settling
    private long    settleStart;  // when the current multi-finger set formed
    private long    downTime;     // the live stream's ACTION_DOWN time
    private boolean vetoed;       // the settle elapsed while the caller vetoed the hold

    // Geometry. ref* are fixed when the hold engages (AOSP's reference points).
    private float curX, curY, surfW, surfH, padW = FALLBACK_PAD_W_MM, padH = FALLBACK_PAD_H_MM;
    private float refTouchX, refTouchY, refCurX, refCurY;

    boolean engaged()    { return engaged; }
    boolean suppressed() { return suppressed; }
    int     contacts()   { return n; }

    /** A trackpad frame: every contact AppKit reports for this event (ended and
     *  cancelled ones included, with their final position). `allowEngage` is
     *  the caller's veto (a mouse button or synthesized drag is down, a
     *  floating window is up, no activity). Returns ms until onTick must run
     *  (a pending settle), or -1. */
    long onFrame(long now, int count, int[] ids, int[] phases, float[] nx, float[] ny,
                 float padWmm, float padHmm, float cursorX, float cursorY,
                 float surfaceW, float surfaceH, boolean allowEngage, Sink sink) {
        if (padWmm > 1f && padHmm > 1f) { padW = padWmm; padH = padHmm; }
        curX = cursorX; curY = cursorY; surfW = surfaceW; surfH = surfaceH;

        boolean anyCancelled = false, moved = false, ghost = false;
        for (int i = 0; i < count; i++) if (phases[i] == PH_CANCELLED) anyCancelled = true;

        // RECONCILE. Every AppKit touch event's touchesMatchingPhase:NSTouchPhaseAny
        // set carries ALL current touches (NSTouch.h NSTouchPhaseAny / NSTouchPhaseTouching
        // = began|moved|stationary; the ended ones ride along in the event that ends
        // them), so a contact this mapper still tracks that a frame does not name has
        // lost its end somewhere: the view that held it was replaced or removed, or
        // the window lost key without a touchesCancelled. Left alone it is a ghost
        // pointer for the rest of the session (contacts never reaches 0, so every
        // later hold is refused). One that owns a pointer ends the stream with
        // CANCEL (never an UP that reads as a finished tap); one that never did
        // is simply forgotten.
        final boolean[] gone = new boolean[MAX];
        // GATE-MUTANT(trackpad:reconcile) begin -- debug/trackpad_gate.sh C6 deletes this
        for (int j = 0; j < n; j++) {
            boolean seen = false;
            for (int i = 0; i < count; i++) if (ids[i] == cId[j]) { seen = true; break; }
            if (seen) continue;
            gone[j] = true;
            if (cPtr[j] >= 0) ghost = true;
        }
        // GATE-MUTANT(trackpad:reconcile) end
        if (anyCancelled || ghost) {
            // AppKit took the touches away (a system gesture claimed them, the
            // window lost key) or stopped reporting one. AOSP's answer to a
            // stream it cannot finish is ACTION_CANCEL, never an UP that would
            // read as a completed press.
            cancelStream(sink);
        }
        for (int j = n - 1; j >= 0; j--) {
            boolean cancelledNow = false;
            for (int i = 0; i < count; i++)
                if (phases[i] == PH_CANCELLED && ids[i] == cId[j]) { cancelledNow = true; break; }
            if (gone[j] || cancelledNow) remove(j);
        }
        if ((anyCancelled || ghost) && n > 0) suppressed = true;

        // Positions first, so an ended contact's UP carries where it lifted.
        for (int i = 0; i < count; i++) {
            int k = indexOf(ids[i]);
            if (k < 0) continue;
            if (cX[k] != nx[i] || cY[k] != ny[i]) {
                cX[k] = nx[i]; cY[k] = ny[i];
                if (cPtr[k] >= 0) moved = true;
            }
        }
        if (moved && engaged) sink.motion(build(ACTION_MOVE, 0, -1));

        // Lifts.
        for (int i = 0; i < count; i++) {
            if (phases[i] != PH_ENDED) continue;
            int k = indexOf(ids[i]);
            if (k < 0) continue;
            if (engaged && cPtr[k] >= 0) liftPointer(k, sink);
            remove(k);
        }

        // Landings.
        for (int i = 0; i < count; i++) {
            if (phases[i] == PH_ENDED || phases[i] == PH_CANCELLED) continue;
            if (indexOf(ids[i]) >= 0) continue;
            if (n >= MAX) break;
            int k = n++;
            cId[k] = ids[i]; cX[k] = nx[i]; cY[k] = ny[i]; cPtr[k] = -1;
            c0X[k] = cX[k]; c0Y[k] = cY[k];
            // ORPHAN. A touch's first report is always NSTouchPhaseBegan, so an
            // UNKNOWN contact arriving as moved / stationary is a finger whose start
            // this mapper never saw -- typically one still on the pad after a reset
            // (a view replaced or the window lost key sent an empty frame, which
            // dropped it). Tracked so its END is recognized, but never given a
            // pointer and never a hold: the contact is suppressed until every finger
            // lifts. Without it the fingers a user is still resting when the Activity
            // changes (PianOli's settings opening under a live hold) would re-settle
            // into a NEW hold on the new screen, and their lift would tap it.
            // GATE-MUTANT(trackpad:orphan) begin -- debug/trackpad_gate.sh C7 deletes this
            if (phases[i] != PH_BEGAN) { suppressed = true; continue; }
            // GATE-MUTANT(trackpad:orphan) end
            if (engaged) {
                // A finger joining a live hold gets its own pointer at once
                // (AOSP FREEFORM: "new mapping for touch id").
                cPtr[k] = freePointerId();
                sink.motion(build(ACTION_POINTER_DOWN, indexOfPointer(cPtr[k]), -1));
            } else {
                // Not settled yet: restart the settle with the new set, and
                // measure motion from here (the cursor finger moved freely
                // before it had company; that is not a gesture).
                settleStart = now;
                moving = false;
                vetoed = false;
                for (int j = 0; j < n; j++) { c0X[j] = cX[j]; c0Y[j] = cY[j]; }
            }
        }

        if (n == 0) { engaged = false; suppressed = false; moving = false; return -1; }
        if (!engaged && !suppressed && !moving && n >= 2) {
            final float k = zoomScale();
            for (int j = 0; j < n; j++) {
                float dx = (cX[j] - c0X[j]) * padW * k, dy = (cY[j] - c0Y[j]) * padH * k;
                if (Math.hypot(dx, dy) > MIN_DISTANCE_PX) { moving = true; break; }
            }
        }
        return maybeEngage(now, allowEngage, sink);
    }

    /** The settle timer fired. */
    long onTick(long now, boolean allowEngage, Sink sink) {
        return maybeEngage(now, allowEngage, sink);
    }

    /** Someone else owns these fingers now: cancel a live hold.
     *
     *  `systemGesture` = macOS recognized a scroll or a pinch on them, so the
     *  whole contact session is that gesture and must not turn into a hold
     *  later; it stays suppressed until every finger has lifted.
     *
     *  `false` = a host mouse BUTTON went down (left from dispatch(), right /
     *  other from the view's rightMouseDown: / otherMouseDown:). Two or more
     *  fingers on the pad when a button goes down are the click itself (a
     *  physical two-finger click is macOS's default SECONDARY click: both
     *  fingers rest first, then the pad presses), so the contact is suppressed
     *  just like a scroll -- without it the hold engages, the click cannot take
     *  it, and on lift the app sees POINTER_UP + UP: a primary TAP on whatever
     *  is under the cursor, which can be destructive. Modern AOSP never does
     *  that either: GestureConverter maps GESTURES_BUTTON_RIGHT to
     *  AMOTION_EVENT_BUTTON_SECONDARY and no gesture turns a resting contact
     *  into touch pointers (GestureConverter.cpp:51-52). With ONE finger down
     *  a button only vetoes a hold while it is held (the caller's allowEngage):
     *  that finger usually stays on the pad to steer the cursor, and
     *  suppressing on every click would make a later two-finger hold need every
     *  finger lifted first. A hold that is live is always cancelled. */
    void claim(Sink sink, boolean systemGesture) {
        // GATE-MUTANT(trackpad:claim) begin -- C3 deletes this
        final boolean was = engaged;
        final boolean twoFingersDown = n >= 2;
        cancelStream(sink);
        if (n > 0 && (systemGesture || was || twoFingersDown)) suppressed = true;
        // GATE-MUTANT(trackpad:claim) end
    }

    private long maybeEngage(long now, boolean allowEngage, Sink sink) {
        if (engaged || suppressed || moving || n < 2) return -1;
        // GATE-MUTANT(trackpad:settle) begin -- debug/trackpad_gate.sh C2 deletes this
        long left = settleStart + SETTLE_MS - now;
        if (left > 0) return left;
        // GATE-MUTANT(trackpad:settle) end
        // Vetoed (a mouse button is down, a floating window is up, no
        // activity): fingers that then stay perfectly still send no further
        // touch frame, so ask for another look. It ends on its own the moment a
        // finger lifts (n < 2), the contact is claimed, or it moves.
        if (!allowEngage) { vetoed = true; return SETTLE_MS; }
        // GATE-MUTANT(trackpad:engage) begin -- C1 deletes this: the pre-change
        // Mac.apk, where a two-finger contact delivered nothing at all.
        // Reference points, AOSP style: the cursor and the centroid of the
        // touching fingers at the moment the gesture settles.
        float sx = 0f, sy = 0f;
        for (int j = 0; j < n; j++) { sx += cX[j]; sy += cY[j]; }
        refTouchX = sx / n; refTouchY = sy / n;
        refCurX = curX; refCurY = curY;
        engaged = true;
        // WHEN the fingers went down (GitHub #23). The settle only delays what
        // we SEND; the contact itself began at settleStart, when the set formed
        // (a touchscreen stamps DOWN with the moment the finger landed, and
        // downTime is that DOWN's eventTime -- MotionEvent.getDownTime). Stamping
        // the engage instead shaved the 100 ms settle off every hold, so a 220 ms
        // rest reached the app as a 120 ms touch: under a 150 ms two-finger-tap
        // timeout. The burst is stamped settleStart; later events are stamped at
        // delivery. A hold the caller vetoed past its settle (a mouse button was
        // down, a dialog was up) starts NOW instead: back-dating it would put its
        // DOWN before the event that ended the veto (the mouse UP), and a
        // detector would read a negative gap between the two streams.
        long landed = now;
        // GATE-MUTANT(trackpad:landtime) begin -- debug/trackpad_gate.sh C8 deletes this
        if (!vetoed) landed = settleStart;
        // GATE-MUTANT(trackpad:landtime) end
        downTime = landed;
        // Ids and DOWN order follow landing order (the contact array already is).
        for (int j = 0; j < n; j++) {
            cPtr[j] = j;
            sink.motion(build(j == 0 ? ACTION_DOWN : ACTION_POINTER_DOWN, j, j + 1, landed));
        }
        // GATE-MUTANT(trackpad:engage) end
        return -1;
    }

    private void liftPointer(int k, Sink sink) {
        int live = livePointers();
        if (live <= 1) {
            sink.motion(build(ACTION_UP, 0, -1));
            engaged = false;
        } else {
            sink.motion(build(ACTION_POINTER_UP, indexOfPointer(cPtr[k]), -1));
        }
        cPtr[k] = -1;
    }

    private void cancelStream(Sink sink) {
        if (!engaged) return;
        if (livePointers() > 0) sink.motion(build(ACTION_CANCEL, 0, -1));
        for (int j = 0; j < n; j++) cPtr[j] = -1;
        engaged = false;
    }

    /** px of display per mm of pad: AOSP's mPointerXZoomScale. */
    private float zoomScale() {
        double display = Math.hypot(surfW, surfH), raw = Math.hypot(padW, padH);
        return raw > 0 ? (float) (ZOOM_SPEED_RATIO * display / raw) : 0f;
    }

    /** Pointers sorted by id (AOSP dispatches in id order, so index = rank).
     *  `limit` >= 0 builds over the first `limit` contacts that own pointers in
     *  landing order (the engage burst, where pointers appear one by one). */
    private Motion build(int action, int index, int limit) {
        return build(action, index, limit, 0L);
    }

    private Motion build(int action, int index, int limit, long eventTime) {
        final float k = zoomScale();
        int m = 0;
        int[] ids = new int[n]; float[] xs = new float[n], ys = new float[n];
        for (int j = 0; j < n; j++) {
            if (cPtr[j] < 0) continue;
            if (limit >= 0 && cPtr[j] >= limit) continue;
            float x = refCurX + (cX[j] - refTouchX) * padW * k;
            float y = refCurY + (cY[j] - refTouchY) * padH * k;
            ids[m] = cPtr[j];
            xs[m] = clamp(x, surfW);
            ys[m] = clamp(y, surfH);
            m++;
        }
        // insertion sort by id (n <= 10)
        for (int a = 1; a < m; a++) {
            int id = ids[a]; float x = xs[a], y = ys[a]; int b = a - 1;
            while (b >= 0 && ids[b] > id) { ids[b + 1] = ids[b]; xs[b + 1] = xs[b]; ys[b + 1] = ys[b]; b--; }
            ids[b + 1] = id; xs[b + 1] = x; ys[b + 1] = y;
        }
        return new Motion(action, index, downTime, eventTime,
                java.util.Arrays.copyOf(ids, m), java.util.Arrays.copyOf(xs, m),
                java.util.Arrays.copyOf(ys, m));
    }

    private static float clamp(float v, float extent) {
        if (extent <= 0f) return v;
        return v < 0f ? 0f : (v > extent - 1f ? extent - 1f : v);
    }

    private int indexOf(int id) {
        for (int j = 0; j < n; j++) if (cId[j] == id) return j;
        return -1;
    }

    private int livePointers() {
        int c = 0;
        for (int j = 0; j < n; j++) if (cPtr[j] >= 0) c++;
        return c;
    }

    /** Rank of pointer `id` among live pointer ids. */
    private int indexOfPointer(int id) {
        int r = 0;
        for (int j = 0; j < n; j++) if (cPtr[j] >= 0 && cPtr[j] < id) r++;
        return r;
    }

    private int freePointerId() {
        for (int id = 0; id < MAX; id++) {
            boolean taken = false;
            for (int j = 0; j < n; j++) if (cPtr[j] == id) { taken = true; break; }
            if (!taken) return id;
        }
        return MAX - 1;
    }

    private void remove(int k) {
        if (k < 0) return;
        for (int j = k; j < n - 1; j++) {
            cId[j] = cId[j + 1]; cX[j] = cX[j + 1]; cY[j] = cY[j + 1];
            c0X[j] = c0X[j + 1]; c0Y[j] = c0Y[j + 1]; cPtr[j] = cPtr[j + 1];
        }
        n--;
    }
}
