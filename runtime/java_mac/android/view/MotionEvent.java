// android.view.MotionEvent -- the universal touch / pointer event.
//
// The internal model mirrors AOSP's native MotionEvent (frameworks/native
// libs/input/Input.cpp) rather than inventing one, because almost every
// observable oddity of this class falls out of that model:
//
//   * Coordinates are stored RAW (as the device reported them) plus a single
//     global (xOffset, yOffset). getX() is raw + offset; getRawX() is the
//     stored value. offsetLocation() only moves the offset (Input.cpp:461),
//     which is why a ViewGroup offsetting an event into child-local space
//     leaves getRawX() reporting window coordinates.
//   * An event is a LIST of samples, oldest first, and the last one is the
//     current sample. getHistorySize() is samples-1; addBatch() appends
//     (Input.cpp:388). A historical accessor validates its position against
//     that size and THROWS, it does not fall back to the current sample.
//   * Pointer indices are validated, not clamped: android_view_MotionEvent.cpp
//     :157 validatePointerIndex throws IllegalArgumentException. Clamping
//     answers a plausible number forever and makes a multi-touch gesture
//     silently track the wrong finger.
//
// Pinned against android-11.0.0_r48 (API 30), which is the platform the
// framework answer-differential's golden was recorded on.
package android.view;

import android.graphics.Matrix;
import android.os.Parcel;
import android.os.Parcelable;

public class MotionEvent extends InputEvent implements Parcelable {
    public static final int ACTION_DOWN          = 0;
    public static final int ACTION_UP            = 1;
    public static final int ACTION_MOVE          = 2;
    public static final int ACTION_CANCEL        = 3;
    public static final int ACTION_OUTSIDE       = 4;
    public static final int ACTION_POINTER_DOWN  = 5;
    public static final int ACTION_POINTER_UP    = 6;
    public static final int ACTION_HOVER_MOVE    = 7;
    public static final int ACTION_SCROLL        = 8;
    public static final int ACTION_HOVER_ENTER   = 9;
    public static final int ACTION_HOVER_EXIT    = 10;
    public static final int ACTION_BUTTON_PRESS  = 11;
    public static final int ACTION_BUTTON_RELEASE = 12;

    public static final int ACTION_MASK            = 0xff;
    public static final int ACTION_POINTER_INDEX_MASK = 0xff00;
    public static final int ACTION_POINTER_INDEX_SHIFT = 8;

    // Sentinel + button-state masks (AOSP MotionEvent). androidx
    // ViewDragHelper (the drag engine behind DrawerLayout / ViewPager /
    // SwipeRefreshLayout) reads INVALID_POINTER_ID; Compose pointer input
    // and androidx MotionEventCompat decode getButtonState() via BUTTON_*.
    public static final int INVALID_POINTER_ID = -1;
    public static final int BUTTON_PRIMARY   = 1;
    public static final int BUTTON_SECONDARY = 2;
    public static final int BUTTON_TERTIARY  = 4;
    public static final int BUTTON_BACK      = 8;
    public static final int BUTTON_FORWARD   = 16;
    public static final int BUTTON_STYLUS_PRIMARY   = 32;
    public static final int BUTTON_STYLUS_SECONDARY = 64;

    // Tool types (AOSP MotionEvent:1441-1469).
    public static final int TOOL_TYPE_UNKNOWN = 0;
    public static final int TOOL_TYPE_FINGER  = 1;
    public static final int TOOL_TYPE_STYLUS  = 2;
    public static final int TOOL_TYPE_MOUSE   = 3;
    public static final int TOOL_TYPE_ERASER  = 4;

    // Edge flags (AOSP MotionEvent:507-522).
    public static final int EDGE_TOP    = 0x00000001;
    public static final int EDGE_BOTTOM = 0x00000002;
    public static final int EDGE_LEFT   = 0x00000004;
    public static final int EDGE_RIGHT  = 0x00000008;

    // Event flags (AOSP MotionEvent:444-502).
    public static final int FLAG_WINDOW_IS_OBSCURED = 0x1;
    public static final int FLAG_WINDOW_IS_PARTIALLY_OBSCURED = 0x2;
    public static final int FLAG_HOVER_EXIT_PENDING = 0x4;
    public static final int FLAG_IS_GENERATED_GESTURE = 0x8;
    public static final int FLAG_TARGET_ACCESSIBILITY_FOCUS = 0x40000000;
    public static final int FLAG_TAINTED = 0x80000000;
    // FLAG_CANCELED is an API-33 constant; android-11 has no such flag and
    // NOTHING here sets it. It used to be OR'd in by obtain() and toggled by
    // setAction(), which made every ACTION_CANCEL we produced answer
    // getFlags()==0x20 where a real API-30 device answers 0x0. Declared only
    // because the value is stable across platform versions and a guest
    // compiled against a newer SDK inlines it; never written.
    public static final int FLAG_CANCELED = 0x20;

    public static final int CLASSIFICATION_NONE = 0;
    public static final int CLASSIFICATION_AMBIGUOUS_GESTURE = 1;
    public static final int CLASSIFICATION_DEEP_PRESS = 2;

    // The AOSP axis id space (frameworks/base/core/java/android/view/
    // MotionEvent.java:547-1241). Values are the wire ids the platform uses,
    // so they are not ours to choose. android-11 stops at AXIS_GENERIC_16;
    // the AXIS_GESTURE_* block (48-52) arrived later and is deliberately
    // absent so axisToString()/axisFromString() answer the way API 30 does.
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_PRESSURE = 2;
    public static final int AXIS_SIZE = 3;
    public static final int AXIS_TOUCH_MAJOR = 4;
    public static final int AXIS_TOUCH_MINOR = 5;
    public static final int AXIS_TOOL_MAJOR = 6;
    public static final int AXIS_TOOL_MINOR = 7;
    public static final int AXIS_ORIENTATION = 8;
    public static final int AXIS_VSCROLL = 9;
    public static final int AXIS_HSCROLL = 10;
    public static final int AXIS_Z = 11;
    public static final int AXIS_RX = 12;
    public static final int AXIS_RY = 13;
    public static final int AXIS_RZ = 14;
    public static final int AXIS_HAT_X = 15;
    public static final int AXIS_HAT_Y = 16;
    public static final int AXIS_LTRIGGER = 17;
    public static final int AXIS_RTRIGGER = 18;
    public static final int AXIS_THROTTLE = 19;
    public static final int AXIS_RUDDER = 20;
    public static final int AXIS_WHEEL = 21;
    public static final int AXIS_GAS = 22;
    public static final int AXIS_BRAKE = 23;
    public static final int AXIS_DISTANCE = 24;
    public static final int AXIS_TILT = 25;
    public static final int AXIS_SCROLL = 26;
    public static final int AXIS_RELATIVE_X = 27;
    public static final int AXIS_RELATIVE_Y = 28;
    public static final int AXIS_GENERIC_1 = 32;
    public static final int AXIS_GENERIC_2 = 33;
    public static final int AXIS_GENERIC_3 = 34;
    public static final int AXIS_GENERIC_4 = 35;
    public static final int AXIS_GENERIC_5 = 36;
    public static final int AXIS_GENERIC_6 = 37;
    public static final int AXIS_GENERIC_7 = 38;
    public static final int AXIS_GENERIC_8 = 39;
    public static final int AXIS_GENERIC_9 = 40;
    public static final int AXIS_GENERIC_10 = 41;
    public static final int AXIS_GENERIC_11 = 42;
    public static final int AXIS_GENERIC_12 = 43;
    public static final int AXIS_GENERIC_13 = 44;
    public static final int AXIS_GENERIC_14 = 45;
    public static final int AXIS_GENERIC_15 = 46;
    public static final int AXIS_GENERIC_16 = 47;

    private int action;
    private int flags;
    private int source;
    private int metaState;
    private int buttonState;
    /** AOSP mActionButton (Input.h MotionEvent::mActionButton): the button that
     *  changed state for ACTION_BUTTON_PRESS / ACTION_BUTTON_RELEASE, 0 for every
     *  other action. CursorInputMapper sets it on exactly those two actions. */
    private int actionButton;
    private int deviceId;
    private int edgeFlags;
    private float xPrecision = 1f, yPrecision = 1f;
    private long downTime;

    // Per-pointer descriptors; these do not vary across samples.
    private int pointerCount = 1;
    private int[] ids       = new int[]{0};
    private int[] toolTypes = new int[]{TOOL_TYPE_UNKNOWN};

    // The sample list, oldest first. samples[s][p] holds pointer p's RAW axis
    // values at sample s; the LAST entry is the current sample and everything
    // before it is history (Input.cpp:388, :456). sampleTimes is parallel.
    private PointerCoords[][] samples;
    private long[] sampleTimes;

    // The single global location offset. offsetLocation/setLocation move only
    // this; transform() rewrites it plus every stored sample.
    private float xOffset, yOffset;

    private MotionEvent() {}

    // Gated touch diagnostics. MACAPK_TRACE_TOUCH=1 logs the first few
    // multi-pointer accessor reads so we can see exactly how far into an
    // engine's touch pipeline (e.g. libGDX AndroidTouchHandler, which reads
    // getActionMasked/getPointerCount/getPointerId/getX(i)/getY(i)) a
    // dispatched event actually penetrates. Capped so it never spams a
    // running app; inert unless the env var is set.
    private static final boolean TT =
        "1".equals(System.getenv("MACAPK_TRACE_TOUCH"));
    private static int ttBudget = 60;
    private static void tt(String m) {
        if (!TT) return;
        if (ttBudget <= 0) return;
        ttBudget--;
        com.macapk.loader.NativeLog.i("MotionEvent", m);
    }

    // ------------------------------------------------------------------
    // internal helpers
    // ------------------------------------------------------------------

    /** The current (newest) sample. */
    private PointerCoords[] cur() { return samples[samples.length - 1]; }

    /** android_view_MotionEvent.cpp:157. Every indexed accessor runs this
     *  before touching storage; a clamp would answer for the wrong finger. */
    private void checkPointerIndex(int pointerIndex) {
        if (pointerIndex < 0 || pointerIndex >= pointerCount) {
            throw new IllegalArgumentException("pointerIndex out of range");
        }
    }

    /** android_view_MotionEvent.cpp:166. */
    private void checkHistoryPos(int historyPos) {
        if (historyPos < 0 || historyPos >= getHistorySize()) {
            throw new IllegalArgumentException("historyPos out of range");
        }
    }

    /** MotionEvent::getAxisValue (Input.cpp:418): the stored value, with the
     *  global offset folded in for X and Y only. Out-of-range axis ids answer
     *  0 here because native PointerCoords::getAxisValue range-checks and
     *  returns 0; only the Java PointerCoords struct throws, so a bad axis
     *  must not be forwarded into it. */
    private float axisValue(PointerCoords c, int axis) {
        if (axis < 0 || axis > 63) return 0f;
        float v = c.getAxisValue(axis);
        if (axis == AXIS_X) return v + xOffset;
        if (axis == AXIS_Y) return v + yOffset;
        return v;
    }

    /** MotionEvent::getRawAxisValue (Input.cpp:414): no offset, ever. */
    private static float rawAxisValue(PointerCoords c, int axis) {
        if (axis < 0 || axis > 63) return 0f;
        return c.getAxisValue(axis);
    }

    /** Allocate a fresh sample list holding exactly one sample. */
    private void initSamples(long eventTimeMillis, int n) {
        pointerCount = n;
        ids = new int[n];
        toolTypes = new int[n];
        samples = new PointerCoords[1][];
        samples[0] = new PointerCoords[n];
        for (int i = 0; i < n; i++) samples[0][i] = new PointerCoords();
        sampleTimes = new long[]{ eventTimeMillis };
    }

    // ------------------------------------------------------------------
    // obtain
    // ------------------------------------------------------------------

    /** AOSP MotionEvent.java:1953 -> :1830 -> :1870. The subset factory: the
     *  values it does NOT take are pressure/size 1.0, x/y precision 1.0,
     *  deviceId 0, edgeFlags 0, buttonState 0, flags 0 -- and, crucially,
     *  source SOURCE_UNKNOWN with pp[0].clear() leaving toolType
     *  TOOL_TYPE_UNKNOWN. Mac.apk used to default this path to
     *  SOURCE_TOUCHSCREEN/TOOL_TYPE_FINGER; that is not what Android answers,
     *  so a synthesizer that needs a touchscreen source must say so (see
     *  TouchDispatcher.touchEvent, which builds the host-mouse stream through
     *  the multi-pointer factory for exactly this reason). */
    public static MotionEvent obtain(long downTime, long eventTime, int action,
            float x, float y, int metaState) {
        return obtain(downTime, eventTime, action, x, y, 1.0f, 1.0f,
                metaState, 1.0f, 1.0f, 0, 0);
    }

    /** AOSP legacy single-pointer factory (MotionEvent.java:1830), which
     *  hands the real body InputDevice.SOURCE_UNKNOWN. */
    public static MotionEvent obtain(long downTime, long eventTime, int action,
            float x, float y, float pressure, float size, int metaState,
            float xPrecision, float yPrecision, int deviceId, int edgeFlags) {
        return obtain(downTime, eventTime, action, x, y, pressure, size, metaState,
                xPrecision, yPrecision, deviceId, edgeFlags,
                InputDevice.SOURCE_UNKNOWN, 0 /* DEFAULT_DISPLAY */);
    }

    /** AOSP MotionEvent.java:1870 (the @hide form that takes a source). The
     *  single-pointer body: pp[0].clear() then id=0, pc[0].clear() then
     *  x/y/pressure/size, so every other axis is 0. displayId is accepted for
     *  signature parity and ignored: Mac.apk is single-display. */
    public static MotionEvent obtain(long downTime, long eventTime, int action,
            float x, float y, float pressure, float size, int metaState,
            float xPrecision, float yPrecision, int deviceId, int edgeFlags,
            int source, int displayId) {
        MotionEvent e = new MotionEvent();
        e.initSamples(eventTime, 1);
        e.ids[0] = 0;
        e.toolTypes[0] = TOOL_TYPE_UNKNOWN;
        PointerCoords c = e.samples[0][0];
        c.x = x; c.y = y; c.pressure = pressure; c.size = size;
        e.downTime = downTime;
        e.action = action;
        e.metaState = metaState;
        e.xPrecision = xPrecision; e.yPrecision = yPrecision;
        e.deviceId = deviceId; e.edgeFlags = edgeFlags;
        e.source = source;
        return e;
    }

    /** AOSP MotionEvent.java:1931, deprecated. It IGNORES pointerCount and
     *  delegates straight to the single-pointer form, so getPointerCount()
     *  answers 1 no matter what was asked for. Honouring the argument reads
     *  "more correct" and is wrong. */
    @Deprecated
    public static MotionEvent obtain(long downTime, long eventTime, int action,
            int pointerCount, float x, float y, float pressure, float size,
            int metaState, float xPrecision, float yPrecision, int deviceId,
            int edgeFlags) {
        return obtain(downTime, eventTime, action, x, y, pressure, size,
                metaState, xPrecision, yPrecision, deviceId, edgeFlags);
    }

    /** AOSP MotionEvent.java:1784, deprecated. Builds PointerProperties by
     *  clear()ing each one and setting only the id, so every toolType comes
     *  out TOOL_TYPE_UNKNOWN, and it forces buttonState to 0 regardless of
     *  what the caller wanted. */
    @Deprecated
    public static MotionEvent obtain(long downTime, long eventTime, int action,
            int pointerCount, int[] pointerIds, PointerCoords[] pointerCoords,
            int metaState, float xPrecision, float yPrecision, int deviceId,
            int edgeFlags, int source, int flags) {
        PointerProperties[] pp = null;
        if (pointerIds != null && pointerCount >= 1
                && pointerIds.length >= pointerCount) {
            pp = new PointerProperties[pointerCount];
            for (int i = 0; i < pointerCount; i++) {
                pp[i] = new PointerProperties();
                pp[i].clear();
                pp[i].id = pointerIds[i];
            }
        }
        return obtain(downTime, eventTime, action, pointerCount, pp,
                pointerCoords, metaState, 0, xPrecision, yPrecision, deviceId,
                edgeFlags, source, flags);
    }

    /** AOSP multi-pointer obtain (MotionEvent.java:1697). The caller's source
     *  is stored VERBATIM, including 0: substituting a touchscreen source for
     *  a caller that explicitly asked for SOURCE_UNKNOWN rewrites the event
     *  the caller built.
     *
     *  Argument validation mirrors android_view_MotionEvent.cpp:333-386 and
     *  is deliberately asymmetric, because AOSP's is: a bad pointerCount or a
     *  null/short array raises IllegalArgumentException; a null COORDS entry
     *  raises NullPointerException; and a null PROPERTIES entry raises
     *  nothing at all -- nativeInitialize does a bare `goto Error`, so
     *  initialize() reports failure and obtain() RETURNS NULL. */
    public static MotionEvent obtain(long downTime, long eventTime, int action,
            int pointerCount, PointerProperties[] pointerProperties,
            PointerCoords[] pointerCoords, int metaState, int buttonState,
            float xPrecision, float yPrecision, int deviceId, int edgeFlags,
            int source, int flags) {
        if (pointerCount < 1) {
            throw new IllegalArgumentException("pointerCount must be at least 1");
        }
        if (pointerProperties == null) {
            throw new IllegalArgumentException("pointerProperties array must not be null");
        }
        if (pointerProperties.length < pointerCount) {
            throw new IllegalArgumentException(
                    "pointerProperties array must be large enough to hold all pointers");
        }
        if (pointerCoords == null) {
            throw new IllegalArgumentException("pointerCoords array must not be null");
        }
        if (pointerCoords.length < pointerCount) {
            throw new IllegalArgumentException(
                    "pointerCoords array must be large enough to hold all pointers");
        }
        for (int i = 0; i < pointerCount; i++) {
            // A null properties entry is the AOSP silent-failure path.
            if (pointerProperties[i] == null) return null;
            if (pointerCoords[i] == null) {
                throw new NullPointerException("pointerCoords");
            }
        }

        MotionEvent e = new MotionEvent();
        e.initSamples(eventTime, pointerCount);
        for (int i = 0; i < pointerCount; i++) {
            e.ids[i] = pointerProperties[i].id;
            e.toolTypes[i] = pointerProperties[i].toolType;
            // AOSP copies the caller's PointerCoords wholesale (the caller may
            // reuse the array afterwards); that copy is what carries every
            // axis beyond x/y/pressure into the event.
            e.samples[0][i].copyFrom(pointerCoords[i]);
        }
        e.downTime = downTime;
        e.action = action;
        e.metaState = metaState; e.buttonState = buttonState;
        e.xPrecision = xPrecision; e.yPrecision = yPrecision;
        e.deviceId = deviceId; e.edgeFlags = edgeFlags;
        e.source = source;
        e.flags = flags;
        return e;
    }

    /** AOSP MotionEvent.obtain(long, long, int, int, PointerProperties[],
     *  PointerCoords[], int, int, float, float, int, int, int, int, int, int)
     *  (MotionEvent.java:2007, API 34) -- the 14-arg form above plus displayId
     *  and classification, inserted in AOSP's order: ..., source, DISPLAY_ID,
     *  flags, CLASSIFICATION. AOSP returns null when the underlying initialize()
     *  fails, which is why the return is @Nullable.
     *
     *  Mac.apk presents ONE display (Display.DEFAULT_DISPLAY) and reports
     *  CLASSIFICATION_NONE for every event (getClassification() above), so both
     *  extra arguments are validated and then have no further effect -- the same
     *  as passing DEFAULT_DISPLAY and CLASSIFICATION_NONE, which is what every
     *  single-display device passes. Delegating to the 14-arg overload keeps the
     *  two from ever diverging. */
    public static MotionEvent obtain(long downTime, long eventTime, int action,
            int pointerCount, PointerProperties[] pointerProperties,
            PointerCoords[] pointerCoords, int metaState, int buttonState,
            float xPrecision, float yPrecision, int deviceId, int edgeFlags,
            int source, int displayId, int flags, int classification) {
        return obtain(downTime, eventTime, action, pointerCount, pointerProperties,
                pointerCoords, metaState, buttonState, xPrecision, yPrecision,
                deviceId, edgeFlags, source, flags);
    }

    /** AOSP MotionEvent.java:1962: an explicit IllegalArgumentException, not
     *  the NullPointerException a body that just dereferences would raise. */
    public static MotionEvent obtain(MotionEvent other) {
        if (other == null) {
            throw new IllegalArgumentException("other motion event must not be null");
        }
        return other.copyOf(true);
    }

    /** AOSP MotionEvent.java:1976: a copy with the historical batch dropped
     *  but the current sample (and its timestamp) kept -- Input.cpp:353-385
     *  copyFrom(other, keepHistory=false). */
    public static MotionEvent obtainNoHistory(MotionEvent other) {
        if (other == null) {
            throw new IllegalArgumentException("other motion event must not be null");
        }
        return other.copyOf(false);
    }

    private MotionEvent copyOf(boolean keepHistory) {
        MotionEvent e = new MotionEvent();
        e.action = action; e.flags = flags; e.source = source;
        e.metaState = metaState; e.buttonState = buttonState;
        e.actionButton = actionButton;
        e.deviceId = deviceId; e.edgeFlags = edgeFlags;
        e.xPrecision = xPrecision; e.yPrecision = yPrecision;
        e.downTime = downTime;
        e.xOffset = xOffset; e.yOffset = yOffset;
        e.pointerCount = pointerCount;
        e.ids = ids.clone();
        e.toolTypes = toolTypes.clone();
        int first = keepHistory ? 0 : samples.length - 1;
        int n = samples.length - first;
        e.samples = new PointerCoords[n][];
        e.sampleTimes = new long[n];
        for (int s = 0; s < n; s++) {
            // A copy must not alias the source's storage; AOSP's nativeCopy
            // duplicates the samples.
            e.samples[s] = new PointerCoords[pointerCount];
            for (int i = 0; i < pointerCount; i++) {
                e.samples[s][i] = new PointerCoords();
                e.samples[s][i].copyFrom(samples[first + s][i]);
            }
            e.sampleTimes[s] = sampleTimes[first + s];
        }
        return e;
    }

    /** AOSP pools recycled events. We do not: recycle() is called on our own
     *  dispatch paths while a guest may still hold the reference, and handing
     *  the same event out twice is a far worse bug than not pooling. */
    public void recycle() {}

    // ------------------------------------------------------------------
    // event-level accessors
    // ------------------------------------------------------------------

    public int  getAction()           { return action; }
    public int  getActionMasked()     { int a = action & ACTION_MASK; tt("getActionMasked=" + a); return a; }
    public int  getActionIndex()      { return (action & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT; }
    public int  getActionButton()     { return actionButton; }
    /** AOSP @hide MotionEvent.setActionButton / setButtonState (MotionEvent.java),
     *  used by the input pipeline to build BUTTON_PRESS / BUTTON_RELEASE. */
    public final void setActionButton(int button) { actionButton = button; }
    /** AOSP @hide isHoverExitPending / setHoverExitPending (FLAG_HOVER_EXIT_PENDING):
     *  ViewGroup.dispatchHoverEvent marks a HOVER_MOVE that is about to be
     *  followed by an EXIT, so the receiving group does not hover itself. */
    /** A copy owned by ViewGroup.dispatchHoverEvent (its synthesized
     *  ENTER/EXIT are written into it); mirrors obtainMotionEventNoHistoryOrSelf. */
    public boolean macapkIsHoverCopy;
    public static MotionEvent macapkHoverCopy(MotionEvent e) {
        MotionEvent c = obtainNoHistory(e);
        c.macapkIsHoverCopy = true;
        return c;
    }
    public boolean isHoverExitPending() { return (flags & FLAG_HOVER_EXIT_PENDING) != 0; }
    public void setHoverExitPending(boolean pending) {
        if (pending) flags |= FLAG_HOVER_EXIT_PENDING; else flags &= ~FLAG_HOVER_EXIT_PENDING;
    }
    public final void setButtonState(int buttonState) { this.buttonState = buttonState; }
    public int  getFlags()            { return flags; }
    public int  getClassification()   { return CLASSIFICATION_NONE; }
    public int  getPointerCount()     { tt("getPointerCount=" + pointerCount); return pointerCount; }
    public long getEventTime()        { return sampleTimes[sampleTimes.length - 1]; }
    public long getDownTime()         { return downTime; }
    public long getEventTimeNanos()   { return getEventTime() * 1000000L; }
    public int  getMetaState()        { return metaState; }
    public int  getButtonState()      { return buttonState; }
    public int  getEdgeFlags()        { return edgeFlags; }
    public float getXPrecision()      { return xPrecision; }
    public float getYPrecision()      { return yPrecision; }
    /** AOSP MotionEvent.java:2779: samples minus the current one. */
    public int  getHistorySize()      { return samples.length - 1; }

    // InputEvent overrides -- load-bearing for Unity's nativeInjectEvent,
    // which classifies the event by source before routing it to the engine
    // input system.
    @Override public int getSource()  { return source; }
    public void setSource(int s)      { this.source = s; }
    @Override public int getDeviceId() { return deviceId; }

    /** AOSP MotionEvent.java:3205. */
    public final void setEdgeFlags(int flags) { this.edgeFlags = flags; }

    /** AOSP MotionEvent.java:3212 -> nativeSetAction. It stores the action
     *  word and nothing else; android-11 has no FLAG_CANCELED to keep in
     *  step, so a body that toggles a flag here answers 0x20 where the
     *  platform answers 0x0. */
    public final void setAction(int action) { this.action = action; }

    /** AOSP MotionEvent.java:3754. An ALL-of test, and a documented false for
     *  0 -- an any-of body answers true for BUTTON_PRIMARY|BUTTON_TERTIARY
     *  when only PRIMARY is down. */
    public final boolean isButtonPressed(int button) {
        if (button == 0) return false;
        return (getButtonState() & button) == button;
    }

    // ------------------------------------------------------------------
    // per-pointer accessors (current sample)
    // ------------------------------------------------------------------

    public float getX()  { return axisValue(cur()[0], AXIS_X); }
    public float getY()  { return axisValue(cur()[0], AXIS_Y); }
    public float getX(int pointerIndex) {
        checkPointerIndex(pointerIndex);
        float v = axisValue(cur()[pointerIndex], AXIS_X);
        tt("getX(" + pointerIndex + ")=" + v); return v;
    }
    public float getY(int pointerIndex) {
        checkPointerIndex(pointerIndex);
        float v = axisValue(cur()[pointerIndex], AXIS_Y);
        tt("getY(" + pointerIndex + ")=" + v); return v;
    }
    public float getRawX() { return rawAxisValue(cur()[0], AXIS_X); }
    public float getRawY() { return rawAxisValue(cur()[0], AXIS_Y); }
    public float getRawX(int pointerIndex) {
        checkPointerIndex(pointerIndex);
        return rawAxisValue(cur()[pointerIndex], AXIS_X);
    }
    public float getRawY(int pointerIndex) {
        checkPointerIndex(pointerIndex);
        return rawAxisValue(cur()[pointerIndex], AXIS_Y);
    }

    public float getPressure()   { return axisValue(cur()[0], AXIS_PRESSURE); }
    public float getSize()       { return axisValue(cur()[0], AXIS_SIZE); }
    public float getTouchMajor() { return axisValue(cur()[0], AXIS_TOUCH_MAJOR); }
    public float getTouchMinor() { return axisValue(cur()[0], AXIS_TOUCH_MINOR); }
    public float getToolMajor()  { return axisValue(cur()[0], AXIS_TOOL_MAJOR); }
    public float getToolMinor()  { return axisValue(cur()[0], AXIS_TOOL_MINOR); }
    public float getOrientation() { return axisValue(cur()[0], AXIS_ORIENTATION); }
    public float getAxisValue(int axis) { return axisValue(cur()[0], axis); }

    public float getPressure(int pointerIndex)   { return perPointer(pointerIndex, AXIS_PRESSURE); }
    public float getSize(int pointerIndex)       { return perPointer(pointerIndex, AXIS_SIZE); }
    public float getTouchMajor(int pointerIndex) { return perPointer(pointerIndex, AXIS_TOUCH_MAJOR); }
    public float getTouchMinor(int pointerIndex) { return perPointer(pointerIndex, AXIS_TOUCH_MINOR); }
    public float getToolMajor(int pointerIndex)  { return perPointer(pointerIndex, AXIS_TOOL_MAJOR); }
    public float getToolMinor(int pointerIndex)  { return perPointer(pointerIndex, AXIS_TOOL_MINOR); }
    public float getOrientation(int pointerIndex) { return perPointer(pointerIndex, AXIS_ORIENTATION); }
    public float getAxisValue(int axis, int pointerIndex) { return perPointer(pointerIndex, axis); }
    private float perPointer(int pointerIndex, int axis) {
        checkPointerIndex(pointerIndex);
        return axisValue(cur()[pointerIndex], axis);
    }

    public int getPointerId(int pointerIndex) {
        checkPointerIndex(pointerIndex);
        int v = ids[pointerIndex]; tt("getPointerId(" + pointerIndex + ")=" + v); return v;
    }
    public int getToolType(int pointerIndex) {
        checkPointerIndex(pointerIndex);
        return toolTypes[pointerIndex];
    }
    /** AOSP MotionEvent.findPointerIndex: no validation, -1 when absent, and
     *  the FIRST match when ids repeat. */
    public int findPointerIndex(int pointerId) {
        for (int i = 0; i < pointerCount; i++) {
            if (ids[i] == pointerId) { tt("findPointerIndex(" + pointerId + ")=" + i); return i; }
        }
        tt("findPointerIndex(" + pointerId + ")=-1"); return -1;
    }

    /** Read pointer coords at an index into the caller's struct. AOSP fills
     *  EVERY axis from the stored sample and then adds the location offset to
     *  x/y (pointerCoordsFromNative), so the out-param reports the same
     *  position getX() does. */
    public void getPointerCoords(int pointerIndex, PointerCoords outPointerCoords) {
        checkPointerIndex(pointerIndex);
        if (outPointerCoords == null) {
            throw new IllegalArgumentException("pointerCoords must not be null");
        }
        outPointerCoords.copyFrom(cur()[pointerIndex]);
        outPointerCoords.x += xOffset;
        outPointerCoords.y += yOffset;
    }

    public void getPointerProperties(int pointerIndex, PointerProperties outPointerProperties) {
        checkPointerIndex(pointerIndex);
        if (outPointerProperties == null) {
            throw new IllegalArgumentException("pointerProperties must not be null");
        }
        outPointerProperties.id = ids[pointerIndex];
        outPointerProperties.toolType = toolTypes[pointerIndex];
    }

    // ------------------------------------------------------------------
    // historical samples
    // ------------------------------------------------------------------
    //
    // These must EXIST regardless of whether we ever batch: AGDK's
    // GameActivity.initializeNativeCode caches getHistoricalAxisValue(III)F
    // and its siblings as JNI method ids during native init, and a missing one
    // throws NoSuchMethodError that aborts onCreate for every AGDK app.

    public long getHistoricalEventTime(int pos) {
        checkHistoryPos(pos);
        return sampleTimes[pos];
    }
    /** AOSP MotionEvent.getHistoricalEventTimeNanos(int):3262 (API 34): the same
     *  historical sample time at NANOSECOND resolution. This tree stores sample
     *  times in milliseconds (sampleTimes, fed from the Cocoa bridge's
     *  millisecond timestamps), so the nanosecond form is that value scaled --
     *  exactly what a device reports when its own source clock has millisecond
     *  granularity. */
    public long getHistoricalEventTimeNanos(int pos) {
        checkHistoryPos(pos);
        return sampleTimes[pos] * 1000000L;
    }
    public float getHistoricalX(int pos) { return hist(0, pos, AXIS_X); }
    public float getHistoricalY(int pos) { return hist(0, pos, AXIS_Y); }
    public float getHistoricalPressure(int pos)   { return hist(0, pos, AXIS_PRESSURE); }
    public float getHistoricalSize(int pos)       { return hist(0, pos, AXIS_SIZE); }
    public float getHistoricalTouchMajor(int pos) { return hist(0, pos, AXIS_TOUCH_MAJOR); }
    public float getHistoricalTouchMinor(int pos) { return hist(0, pos, AXIS_TOUCH_MINOR); }
    public float getHistoricalToolMajor(int pos)  { return hist(0, pos, AXIS_TOOL_MAJOR); }
    public float getHistoricalToolMinor(int pos)  { return hist(0, pos, AXIS_TOOL_MINOR); }
    public float getHistoricalOrientation(int pos) { return hist(0, pos, AXIS_ORIENTATION); }
    public float getHistoricalAxisValue(int axis, int pos) { return hist(0, pos, axis); }

    public float getHistoricalX(int pointerIndex, int pos) { return hist(pointerIndex, pos, AXIS_X); }
    public float getHistoricalY(int pointerIndex, int pos) { return hist(pointerIndex, pos, AXIS_Y); }
    public float getHistoricalPressure(int pointerIndex, int pos)   { return hist(pointerIndex, pos, AXIS_PRESSURE); }
    public float getHistoricalSize(int pointerIndex, int pos)       { return hist(pointerIndex, pos, AXIS_SIZE); }
    public float getHistoricalTouchMajor(int pointerIndex, int pos) { return hist(pointerIndex, pos, AXIS_TOUCH_MAJOR); }
    public float getHistoricalTouchMinor(int pointerIndex, int pos) { return hist(pointerIndex, pos, AXIS_TOUCH_MINOR); }
    public float getHistoricalToolMajor(int pointerIndex, int pos)  { return hist(pointerIndex, pos, AXIS_TOOL_MAJOR); }
    public float getHistoricalToolMinor(int pointerIndex, int pos)  { return hist(pointerIndex, pos, AXIS_TOOL_MINOR); }
    public float getHistoricalOrientation(int pointerIndex, int pos) { return hist(pointerIndex, pos, AXIS_ORIENTATION); }
    public float getHistoricalAxisValue(int axis, int pointerIndex, int pos) {
        return hist(pointerIndex, pos, axis);
    }
    /** Validation order matches the native accessor: pointer index first,
     *  then history position. */
    private float hist(int pointerIndex, int pos, int axis) {
        checkPointerIndex(pointerIndex);
        checkHistoryPos(pos);
        return axisValue(samples[pos][pointerIndex], axis);
    }

    public void getHistoricalPointerCoords(int pointerIndex, int pos,
            PointerCoords outPointerCoords) {
        checkPointerIndex(pointerIndex);
        if (outPointerCoords == null) {
            throw new IllegalArgumentException("pointerCoords must not be null");
        }
        checkHistoryPos(pos);
        outPointerCoords.copyFrom(samples[pos][pointerIndex]);
        outPointerCoords.x += xOffset;
        outPointerCoords.y += yOffset;
    }

    /** AOSP MotionEvent.java:3267. The current sample becomes history and the
     *  supplied values become current; metaState is OR'd into the event's
     *  (android_view_MotionEvent.cpp nativeAddBatch). Note the struct is
     *  clear()ed first, so every axis the caller did not name resets to 0 --
     *  and AOSP does NOT enforce the "ACTION_MOVE only" the javadoc claims. */
    public final void addBatch(long eventTime, float x, float y,
            float pressure, float size, int metaState) {
        PointerCoords c = new PointerCoords();
        c.x = x; c.y = y; c.pressure = pressure; c.size = size;
        addBatch(eventTime, new PointerCoords[]{ c }, metaState);
    }

    /** AOSP MotionEvent.java:3293. */
    public final void addBatch(long eventTime, PointerCoords[] pointerCoords, int metaState) {
        if (pointerCoords == null) {
            throw new IllegalArgumentException("pointerCoords array must not be null");
        }
        if (pointerCoords.length < pointerCount) {
            throw new IllegalArgumentException(
                    "pointerCoords array must be large enough to hold all pointers");
        }
        PointerCoords[] added = new PointerCoords[pointerCount];
        for (int i = 0; i < pointerCount; i++) {
            if (pointerCoords[i] == null) throw new NullPointerException("pointerCoords");
            added[i] = new PointerCoords();
            added[i].copyFrom(pointerCoords[i]);
            // The caller supplies coordinates in the event's CURRENT space, so
            // strip the offset back out before storing (nativeAddBatch passes
            // getXOffset()/getYOffset() into pointerCoordsToNative).
            added[i].x -= xOffset;
            added[i].y -= yOffset;
        }
        int n = samples.length;
        PointerCoords[][] grown = new PointerCoords[n + 1][];
        long[] grownTimes = new long[n + 1];
        System.arraycopy(samples, 0, grown, 0, n);
        System.arraycopy(sampleTimes, 0, grownTimes, 0, n);
        grown[n] = added;
        grownTimes[n] = eventTime;
        samples = grown;
        sampleTimes = grownTimes;
        this.metaState |= metaState;
    }

    // ------------------------------------------------------------------
    // location
    // ------------------------------------------------------------------

    /** MotionEvent::offsetLocation (Input.cpp:461) moves only the offset, so
     *  every pointer's x/y shifts and no pointer's RAW does. A body that also
     *  moved raw would make getRawX() report child-local coordinates once a
     *  ViewGroup had offset the event into a child. */
    public final void offsetLocation(float deltaX, float deltaY) {
        if (deltaX != 0.0f || deltaY != 0.0f) {
            xOffset += deltaX;
            yOffset += deltaY;
        }
    }

    /** AOSP MotionEvent.java:3234: offsetLocation by the delta from pointer
     *  0's current position, so every other pointer rides the same rigid
     *  translation. */
    public final void setLocation(float x, float y) {
        offsetLocation(x - getX(), y - getY());
    }

    /** MotionEvent::transform (Input.cpp:510). Pointer 0's RAW position is
     *  preserved by construction -- the new offset is derived from it -- and
     *  then EVERY sample of every pointer is pushed through the matrix and
     *  re-based on that offset, so pointer 1's raw DOES move while pointer
     *  0's does not. Orientation is rotated by transformAngle; android-11 has
     *  no opt-in flag gating that. */
    public final void transform(Matrix matrix) {
        if (matrix == null) {
            throw new IllegalArgumentException("matrix must not be null");
        }
        final float oldXOffset = xOffset;
        final float oldYOffset = yOffset;
        final float rawX = cur()[0].x;
        final float rawY = cur()[0].y;
        float[] pt = new float[]{ rawX + oldXOffset, rawY + oldYOffset };
        matrix.mapPoints(pt);
        xOffset = pt[0] - rawX;
        yOffset = pt[1] - rawY;

        // How the origin moves, so orientation vectors can be un-translated.
        float[] origin = new float[]{ 0f, 0f };
        matrix.mapPoints(origin);
        final float originX = origin[0], originY = origin[1];

        float[] p = new float[2];
        for (int s = 0; s < samples.length; s++) {
            for (int i = 0; i < pointerCount; i++) {
                PointerCoords c = samples[s][i];
                p[0] = c.x + oldXOffset;
                p[1] = c.y + oldYOffset;
                matrix.mapPoints(p);
                c.x = p[0] - xOffset;
                c.y = p[1] - yOffset;
                c.orientation = transformAngle(matrix, c.orientation, originX, originY);
            }
        }
    }

    /** Input.cpp:490. Build a unit vector at the given clockwise-from-vertical
     *  angle (down is +Y, right is +X), push it through the matrix, subtract
     *  the transformed origin, and read the angle back -- then fold it into
     *  [-pi/2, pi/2]. The comparisons are done in double exactly as the C is,
     *  because a result that lands one float-ulp above pi/2 is precisely what
     *  turns a 90-degree rotation's answer from +1.5708 into -1.5708. */
    private static float transformAngle(Matrix matrix, float angleRadians,
            float originX, float originY) {
        float[] v = new float[]{
            (float) Math.sin(angleRadians),
            (float) -Math.cos(angleRadians),
        };
        matrix.mapPoints(v);
        float x = v[0] - originX;
        float y = v[1] - originY;
        float result = (float) Math.atan2(x, -y);
        if (result < -(Math.PI / 2)) {
            result = (float) (result + Math.PI);
        } else if (result > (Math.PI / 2)) {
            result = (float) (result - Math.PI);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // symbolic names
    // ------------------------------------------------------------------

    private static final String LABEL_PREFIX = "AXIS_";

    /** AOSP MotionEvent.java:3601. */
    public static String actionToString(int action) {
        switch (action) {
            case ACTION_DOWN: return "ACTION_DOWN";
            case ACTION_UP: return "ACTION_UP";
            case ACTION_CANCEL: return "ACTION_CANCEL";
            case ACTION_OUTSIDE: return "ACTION_OUTSIDE";
            case ACTION_MOVE: return "ACTION_MOVE";
            case ACTION_HOVER_MOVE: return "ACTION_HOVER_MOVE";
            case ACTION_SCROLL: return "ACTION_SCROLL";
            case ACTION_HOVER_ENTER: return "ACTION_HOVER_ENTER";
            case ACTION_HOVER_EXIT: return "ACTION_HOVER_EXIT";
            case ACTION_BUTTON_PRESS: return "ACTION_BUTTON_PRESS";
            case ACTION_BUTTON_RELEASE: return "ACTION_BUTTON_RELEASE";
            default: break;
        }
        int index = (action & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT;
        switch (action & ACTION_MASK) {
            case ACTION_POINTER_DOWN: return "ACTION_POINTER_DOWN(" + index + ")";
            case ACTION_POINTER_UP: return "ACTION_POINTER_UP(" + index + ")";
            default: return Integer.toString(action);
        }
    }

    /** The native axis label table (InputEventLabels.h AXES[]), whose entries
     *  are exactly the AXIS_* constant names with the prefix stripped. Only
     *  the axes android-11 knows about are listed; a gap answers numerically. */
    private static final String[] AXIS_LABELS = new String[48];
    static {
        AXIS_LABELS[AXIS_X] = "X";
        AXIS_LABELS[AXIS_Y] = "Y";
        AXIS_LABELS[AXIS_PRESSURE] = "PRESSURE";
        AXIS_LABELS[AXIS_SIZE] = "SIZE";
        AXIS_LABELS[AXIS_TOUCH_MAJOR] = "TOUCH_MAJOR";
        AXIS_LABELS[AXIS_TOUCH_MINOR] = "TOUCH_MINOR";
        AXIS_LABELS[AXIS_TOOL_MAJOR] = "TOOL_MAJOR";
        AXIS_LABELS[AXIS_TOOL_MINOR] = "TOOL_MINOR";
        AXIS_LABELS[AXIS_ORIENTATION] = "ORIENTATION";
        AXIS_LABELS[AXIS_VSCROLL] = "VSCROLL";
        AXIS_LABELS[AXIS_HSCROLL] = "HSCROLL";
        AXIS_LABELS[AXIS_Z] = "Z";
        AXIS_LABELS[AXIS_RX] = "RX";
        AXIS_LABELS[AXIS_RY] = "RY";
        AXIS_LABELS[AXIS_RZ] = "RZ";
        AXIS_LABELS[AXIS_HAT_X] = "HAT_X";
        AXIS_LABELS[AXIS_HAT_Y] = "HAT_Y";
        AXIS_LABELS[AXIS_LTRIGGER] = "LTRIGGER";
        AXIS_LABELS[AXIS_RTRIGGER] = "RTRIGGER";
        AXIS_LABELS[AXIS_THROTTLE] = "THROTTLE";
        AXIS_LABELS[AXIS_RUDDER] = "RUDDER";
        AXIS_LABELS[AXIS_WHEEL] = "WHEEL";
        AXIS_LABELS[AXIS_GAS] = "GAS";
        AXIS_LABELS[AXIS_BRAKE] = "BRAKE";
        AXIS_LABELS[AXIS_DISTANCE] = "DISTANCE";
        AXIS_LABELS[AXIS_TILT] = "TILT";
        AXIS_LABELS[AXIS_SCROLL] = "SCROLL";
        AXIS_LABELS[AXIS_RELATIVE_X] = "RELATIVE_X";
        AXIS_LABELS[AXIS_RELATIVE_Y] = "RELATIVE_Y";
        for (int i = 1; i <= 16; i++) AXIS_LABELS[AXIS_GENERIC_1 + i - 1] = "GENERIC_" + i;
    }

    /** AOSP MotionEvent.java:3644. An unknown axis has no label, and the Java
     *  body falls back to the decimal form. */
    public static String axisToString(int axis) {
        String symbolicName = axisLabel(axis);
        return symbolicName != null ? LABEL_PREFIX + symbolicName : Integer.toString(axis);
    }

    private static String axisLabel(int axis) {
        if (axis < 0 || axis >= AXIS_LABELS.length) return null;
        return AXIS_LABELS[axis];
    }

    /** AOSP MotionEvent.java:3657. Three behaviours worth spelling out, all of
     *  which the platform really has:
     *   - a null argument NPEs, because the body calls startsWith on it;
     *   - the prefix is STRIPPED IN PLACE, so "AXIS_0" ends up parsed as the
     *     decimal 0 rather than rejected;
     *   - a prefixed but unknown name answers 0, not -1, because the native
     *     lookup walks the AXES table and returns the TERMINATOR entry's
     *     value on a miss (InputEventLabels.h:415-423,
     *     `return list->value;` past `{ nullptr, 0 }`). Since 0 >= 0 the Java
     *     body accepts it and never reaches the decimal fallback. */
    public static int axisFromString(String symbolicName) {
        if (symbolicName.startsWith(LABEL_PREFIX)) {
            symbolicName = symbolicName.substring(LABEL_PREFIX.length());
            int axis = axisByLabel(symbolicName);
            if (axis >= 0) {
                return axis;
            }
        }
        try {
            return Integer.parseInt(symbolicName, 10);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static int axisByLabel(String label) {
        for (int i = 0; i < AXIS_LABELS.length; i++) {
            if (label.equals(AXIS_LABELS[i])) return i;
        }
        return 0;   // the sentinel's value; see axisFromString
    }

    // ------------------------------------------------------------------
    // descriptor structs
    // ------------------------------------------------------------------

    // AOSP MotionEvent.PointerProperties / PointerCoords -- the per-pointer
    // descriptors the multi-pointer obtain() overload consumes. Public mutable
    // fields, matching the framework so code that builds events (AndroidX test
    // input, some gesture libraries) constructs them the same way.
    public static final class PointerProperties {
        public int id;
        public int toolType;
        public PointerProperties() { clear(); }
        public PointerProperties(PointerProperties other) { copyFrom(other); }
        public void copyFrom(PointerProperties other) {
            if (other != null) { id = other.id; toolType = other.toolType; }
        }
        public void clear() { id = INVALID_POINTER_ID; toolType = TOOL_TYPE_UNKNOWN; }
        /** AOSP MotionEvent.java:4163: VALUE equality. Inheriting
         *  Object.equals makes a gesture library that de-dups pointers treat
         *  two descriptions of the same finger as two fingers. */
        @Override public boolean equals(Object other) {
            if (other instanceof PointerProperties) {
                PointerProperties o = (PointerProperties) other;
                return id == o.id && toolType == o.toolType;
            }
            return false;
        }
        @Override public int hashCode() { return id | (toolType << 8); }
    }

    public static final class PointerCoords {
        /** AOSP MotionEvent.PointerCoords.isResampled():4470 (API 34): was this
         *  sample SYNTHESISED by the platform's input resampler rather than
         *  reported by the digitiser? Mac.apk feeds every sample straight from a
         *  Cocoa NSEvent (cocoa_bridge.mm) and runs no resampler, so every
         *  sample is real -- the same answer a device gives with resampling
         *  disabled. */
        public boolean isResampled() { return false; }

        private static final int INITIAL_PACKED_AXIS_VALUES = 8;
        // Sparse storage for every axis that does not have a named field. AOSP
        // keeps a 64-bit presence mask plus a densely packed value array, in
        // ascending axis order, so an axis is one bit-count away from its slot.
        // Mirrors frameworks/base/core/java/android/view/MotionEvent.java:4269.
        private long mPackedAxisBits;
        private float[] mPackedAxisValues;

        // Every field starts at the Java default. AOSP does the same: a fresh
        // struct has pressure 0 and size 0, and clear() puts them back there.
        // This file used to seed both to 1f ("a finger is always touching"),
        // which answered differently from the platform everywhere a caller
        // built an event from a struct it did not fully populate. Synthesizers
        // that want a normal finger contact now say pressure = 1f explicitly,
        // which is what a real touchscreen driver reports.
        public float x, y, pressure, size;
        public float touchMajor, touchMinor, toolMajor, toolMinor, orientation;
        // AOSP-11 keeps AXIS_RELATIVE_X/Y in the packed store rather than in
        // named fields; these are equivalent through get/setAxisValue and are
        // kept because existing Mac.apk code reads them by name.
        public float relativeX, relativeY;
        public PointerCoords() {}
        public PointerCoords(PointerCoords other) { copyFrom(other); }
        public void copyFrom(PointerCoords other) {
            if (other == null) return;
            final long bits = other.mPackedAxisBits;
            mPackedAxisBits = bits;
            if (bits != 0) {
                final float[] otherValues = other.mPackedAxisValues;
                final int count = Long.bitCount(bits);
                float[] values = mPackedAxisValues;
                if (values == null || count > values.length) {
                    values = new float[otherValues.length];
                    mPackedAxisValues = values;
                }
                System.arraycopy(otherValues, 0, values, 0, count);
            }
            x = other.x; y = other.y; pressure = other.pressure; size = other.size;
            touchMajor = other.touchMajor; touchMinor = other.touchMinor;
            toolMajor = other.toolMajor; toolMinor = other.toolMinor; orientation = other.orientation;
            relativeX = other.relativeX; relativeY = other.relativeY;
        }
        /** AOSP MotionEvent.java:3933: resets ALL axes to zero. */
        public void clear() {
            mPackedAxisBits = 0;
            x = y = pressure = size = 0f;
            touchMajor = touchMinor = toolMajor = toolMinor = orientation = 0f;
            relativeX = relativeY = 0f;
        }
        public float getAxisValue(int axis) {
            switch (axis) {
                case AXIS_X: return x;
                case AXIS_Y: return y;
                case AXIS_PRESSURE: return pressure;
                case AXIS_SIZE: return size;
                case AXIS_TOUCH_MAJOR: return touchMajor;
                case AXIS_TOUCH_MINOR: return touchMinor;
                case AXIS_TOOL_MAJOR: return toolMajor;
                case AXIS_TOOL_MINOR: return toolMinor;
                case AXIS_ORIENTATION: return orientation;
                case AXIS_RELATIVE_X: return relativeX;
                case AXIS_RELATIVE_Y: return relativeY;
                default: {
                    if (axis < 0 || axis > 63) {
                        throw new IllegalArgumentException("Axis out of range.");
                    }
                    final long bits = mPackedAxisBits;
                    final long axisBit = 0x8000000000000000L >>> axis;
                    if ((bits & axisBit) == 0) {
                        return 0;
                    }
                    final int index = Long.bitCount(bits & ~(0xFFFFFFFFFFFFFFFFL >>> axis));
                    return mPackedAxisValues[index];
                }
            }
        }
        public void setAxisValue(int axis, float value) {
            switch (axis) {
                case AXIS_X: x = value; break;
                case AXIS_Y: y = value; break;
                case AXIS_PRESSURE: pressure = value; break;
                case AXIS_SIZE: size = value; break;
                case AXIS_TOUCH_MAJOR: touchMajor = value; break;
                case AXIS_TOUCH_MINOR: touchMinor = value; break;
                case AXIS_TOOL_MAJOR: toolMajor = value; break;
                case AXIS_TOOL_MINOR: toolMinor = value; break;
                case AXIS_ORIENTATION: orientation = value; break;
                case AXIS_RELATIVE_X: relativeX = value; break;
                case AXIS_RELATIVE_Y: relativeY = value; break;
                default: {
                    if (axis < 0 || axis > 63) {
                        throw new IllegalArgumentException("Axis out of range.");
                    }
                    final long bits = mPackedAxisBits;
                    final long axisBit = 0x8000000000000000L >>> axis;
                    final int index = Long.bitCount(bits & ~(0xFFFFFFFFFFFFFFFFL >>> axis));
                    float[] values = mPackedAxisValues;
                    if ((bits & axisBit) == 0) {
                        if (values == null) {
                            values = new float[INITIAL_PACKED_AXIS_VALUES];
                            mPackedAxisValues = values;
                        } else {
                            final int count = Long.bitCount(bits);
                            if (count < values.length) {
                                if (index != count) {
                                    System.arraycopy(values, index, values, index + 1,
                                            count - index);
                                }
                            } else {
                                float[] newValues = new float[count * 2];
                                System.arraycopy(values, 0, newValues, 0, index);
                                System.arraycopy(values, index, newValues, index + 1,
                                        count - index);
                                values = newValues;
                                mPackedAxisValues = values;
                            }
                        }
                        mPackedAxisBits = bits | axisBit;
                    }
                    values[index] = value;
                }
            }
        }

        /** Flatten every axis this struct carries: the 64-bit presence mask,
         *  then one float per set bit in ascending axis order (the packed
         *  store's own layout, so no per-axis key is needed), then the named
         *  fields. Package-private and hand-rolled because AOSP's PointerCoords
         *  has no Java parcel methods at all -- MotionEvent's whole payload
         *  goes through nativeWriteToParcel there. The mask is written even
         *  when zero so the reader's branch matches the writer's. */
        void writeToParcel(Parcel out) {
            final long bits = mPackedAxisBits;
            out.writeLong(bits);
            if (bits != 0) {
                final int count = Long.bitCount(bits);
                for (int i = 0; i < count; i++) out.writeFloat(mPackedAxisValues[i]);
            }
            out.writeFloat(x);
            out.writeFloat(y);
            out.writeFloat(pressure);
            out.writeFloat(size);
            out.writeFloat(touchMajor);
            out.writeFloat(touchMinor);
            out.writeFloat(toolMajor);
            out.writeFloat(toolMinor);
            out.writeFloat(orientation);
            out.writeFloat(relativeX);
            out.writeFloat(relativeY);
        }

        void readFromParcel(Parcel in) {
            final long bits = in.readLong();
            mPackedAxisBits = bits;
            if (bits != 0) {
                final int count = Long.bitCount(bits);
                // Allocate exactly what the mask says. setAxisValue grows this
                // array on demand, so a tight fit here is not a later cost.
                float[] values = new float[count];
                for (int i = 0; i < count; i++) values[i] = in.readFloat();
                mPackedAxisValues = values;
            } else {
                mPackedAxisValues = null;
            }
            x = in.readFloat();
            y = in.readFloat();
            pressure = in.readFloat();
            size = in.readFloat();
            touchMajor = in.readFloat();
            touchMinor = in.readFloat();
            toolMajor = in.readFloat();
            toolMinor = in.readFloat();
            orientation = in.readFloat();
            relativeX = in.readFloat();
            relativeY = in.readFloat();
        }
    }

    // ---------------------------------------------------------------------
    // Parcelable.
    //
    // ⚠ Until v1.0.940 this was an empty body plus a CREATOR that answered a
    // synthetic ACTION_CANCEL at (0,0). That never showed while android.os
    // .Parcel faked marshalling with a side list of live objects, because the
    // reader got the SAME event instance back. Now that Parcel marshals
    // for real, an app that parcels a MotionEvent (gesture recorders, IME
    // handwriting, accessibility replay, anything that stashes the last event
    // in onSaveInstanceState) would silently receive a cancel at the origin.
    //
    // AOSP CANNOT be mirrored line for line here: its writeToParcel is
    // `out.writeInt(PARCEL_TOKEN_MOTION_EVENT); nativeWriteToParcel(mNativePtr,
    // out);` (frameworks/base .../view/MotionEvent.java:3786-3789), so the
    // layout lives in native MotionEvent::writeToParcel, and it encodes AOSP's
    // data model -- a flat vector of samples over PointerProperties -- which is
    // not the model this file uses (PointerCoords[sample][pointer] plus
    // parallel id/toolType arrays and a single location offset). Byte identity
    // is therefore impossible, and it is not required: Parcel.marshall's own
    // javadoc disclaims cross-version byte compatibility, and there is no
    // second process here to hand bytes to. What IS required is that our writer
    // and our reader agree, and that nothing observable is dropped.
    //
    // So the format below is ours, ordered to follow the native one as closely
    // as the model allows: event-level scalars, then the per-pointer
    // descriptors, then the sample list oldest-first with its time.
    //
    //   token, deviceId, source, action, flags, edgeFlags, metaState,
    //   buttonState, xPrecision, yPrecision, xOffset, yOffset, downTime,
    //   pointerCount, {id, toolType} * pointerCount,
    //   sampleCount, { sampleTime, coords * pointerCount } * sampleCount
    //
    // xOffset/yOffset are written SEPARATELY from the samples, and the samples
    // are written RAW, because that is how they are stored: getX() adds the
    // offset and getRawX() does not, so flattening them together would make
    // the two answer the same value after a round trip.
    private static final int PARCEL_TOKEN_MOTION_EVENT = 2;

    @Override public int describeContents() { return 0; }

    /** Parameter named parcelableFlags, not AOSP's `flags`: this class has its
     *  own `flags` field (the FLAG_* event bitmask) and shadowing it would hide
     *  which of the two is being written. */
    @Override public void writeToParcel(Parcel dest, int parcelableFlags) {
        dest.writeInt(PARCEL_TOKEN_MOTION_EVENT);

        dest.writeInt(deviceId);
        dest.writeInt(source);
        dest.writeInt(action);
        dest.writeInt(this.flags);
        dest.writeInt(edgeFlags);
        dest.writeInt(metaState);
        dest.writeInt(buttonState);
        dest.writeFloat(xPrecision);
        dest.writeFloat(yPrecision);
        dest.writeFloat(xOffset);
        dest.writeFloat(yOffset);
        dest.writeLong(downTime);

        final int np = pointerCount;
        dest.writeInt(np);
        for (int i = 0; i < np; i++) {
            dest.writeInt(ids[i]);
            dest.writeInt(toolTypes[i]);
        }

        final int ns = samples.length;
        dest.writeInt(ns);
        for (int s = 0; s < ns; s++) {
            dest.writeLong(sampleTimes[s]);
            for (int i = 0; i < np; i++) samples[s][i].writeToParcel(dest);
        }
    }

    public static final Creator<MotionEvent> CREATOR = new Creator<MotionEvent>() {
        @Override public MotionEvent createFromParcel(Parcel in) {
            in.readInt();   // PARCEL_TOKEN_MOTION_EVENT; we already know the type
            MotionEvent e = new MotionEvent();
            e.deviceId    = in.readInt();
            e.source      = in.readInt();
            e.action      = in.readInt();
            e.flags       = in.readInt();
            e.edgeFlags   = in.readInt();
            e.metaState   = in.readInt();
            e.buttonState = in.readInt();
            e.xPrecision  = in.readFloat();
            e.yPrecision  = in.readFloat();
            e.xOffset     = in.readFloat();
            e.yOffset     = in.readFloat();
            e.downTime    = in.readLong();

            final int np = in.readInt();
            e.pointerCount = np;
            e.ids = new int[np];
            e.toolTypes = new int[np];
            for (int i = 0; i < np; i++) {
                e.ids[i] = in.readInt();
                e.toolTypes[i] = in.readInt();
            }

            final int ns = in.readInt();
            e.samples = new PointerCoords[ns][];
            e.sampleTimes = new long[ns];
            for (int s = 0; s < ns; s++) {
                e.sampleTimes[s] = in.readLong();
                e.samples[s] = new PointerCoords[np];
                for (int i = 0; i < np; i++) {
                    PointerCoords c = new PointerCoords();
                    c.readFromParcel(in);
                    e.samples[s][i] = c;
                }
            }
            return e;
        }
        @Override public MotionEvent[] newArray(int size) { return new MotionEvent[size]; }
    };
}
