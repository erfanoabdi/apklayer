// android.view.accessibility.AccessibilityEvent -- the event a View sends when
// something accessibility-relevant happens (click, focus, text change, content
// change). Was a bare stub (default ctor only), so View.sendAccessibilityEvent /
// announceForAccessibility and any caller invoking obtain()/getEventType()/
// setSource()/recycle() NoSuchMethodError'd. Mac.apk does not bridge to
// NSAccessibility, but the event object must exist with the full property bag so
// the dispatch path completes (AccessibilityManager is disabled, so events are
// constructed-then-dropped, exactly like real Android with no a11y service).
// Mirrors frameworks/base/core/java/android/view/accessibility/AccessibilityEvent.java
// for the observable surface; extends AccessibilityRecord like AOSP.
package android.view.accessibility;

import java.util.ArrayList;
import java.util.List;

// AOSP fidelity: the deprecation usage below MIRRORS AOSP, which deprecates its
// own APIs while continuing to implement them. Removing it would diverge
// from the surface we exist to reproduce, so it is suppressed here rather
// than globally -- every other lint category stays live for this file.
@SuppressWarnings("deprecation")
// AOSP declares `public class AccessibilityEvent extends AccessibilityRecord
// implements Parcelable`. The Parcelable half was missing, which a member-level
// API diff cannot see but which breaks every type check the event passes
// through (Bundle.putParcelable, Intent extras, `instanceof Parcelable`).
public class AccessibilityEvent extends AccessibilityRecord implements android.os.Parcelable {

    // Event type bit constants (verbatim from AOSP API).
    public static final int TYPE_VIEW_CLICKED = 0x00000001;
    public static final int TYPE_VIEW_LONG_CLICKED = 0x00000002;
    public static final int TYPE_VIEW_SELECTED = 0x00000004;
    public static final int TYPE_VIEW_FOCUSED = 0x00000008;
    public static final int TYPE_VIEW_TEXT_CHANGED = 0x00000010;
    public static final int TYPE_WINDOW_STATE_CHANGED = 0x00000020;
    public static final int TYPE_NOTIFICATION_STATE_CHANGED = 0x00000040;
    public static final int TYPE_VIEW_HOVER_ENTER = 0x00000080;
    public static final int TYPE_VIEW_HOVER_EXIT = 0x00000100;
    public static final int TYPE_TOUCH_EXPLORATION_GESTURE_START = 0x00000200;
    public static final int TYPE_TOUCH_EXPLORATION_GESTURE_END = 0x00000400;
    public static final int TYPE_WINDOW_CONTENT_CHANGED = 0x00000800;
    public static final int TYPE_VIEW_SCROLLED = 0x00001000;
    public static final int TYPE_VIEW_TEXT_SELECTION_CHANGED = 0x00002000;
    public static final int TYPE_ANNOUNCEMENT = 0x00004000;
    public static final int TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00008000;
    public static final int TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED = 0x00010000;
    public static final int TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY = 0x00020000;
    public static final int TYPE_GESTURE_DETECTION_START = 0x00040000;
    public static final int TYPE_GESTURE_DETECTION_END = 0x00080000;
    public static final int TYPE_TOUCH_INTERACTION_START = 0x00100000;
    public static final int TYPE_TOUCH_INTERACTION_END = 0x00200000;
    public static final int TYPE_WINDOWS_CHANGED = 0x00400000;
    public static final int TYPE_VIEW_CONTEXT_CLICKED = 0x00800000;
    public static final int TYPE_ASSIST_READING_CONTEXT = 0x01000000;

    public static final int TYPES_ALL_MASK = 0xFFFFFFFF;
    public static final int INVALID_POSITION = -1;
    public static final int MAX_TEXT_LENGTH = 500;

    // Content-change subtypes.
    public static final int CONTENT_CHANGE_TYPE_UNDEFINED = 0x00000000;
    public static final int CONTENT_CHANGE_TYPE_SUBTREE = 0x00000001;
    public static final int CONTENT_CHANGE_TYPE_TEXT = 0x00000002;
    public static final int CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION = 0x00000004;
    public static final int CONTENT_CHANGE_TYPE_PANE_TITLE = 0x00000008;
    public static final int CONTENT_CHANGE_TYPE_PANE_APPEARED = 0x00000010;
    public static final int CONTENT_CHANGE_TYPE_PANE_DISAPPEARED = 0x00000020;
    public static final int CONTENT_CHANGE_TYPE_STATE_DESCRIPTION = 0x00000040;

    private int mEventType;
    private CharSequence mPackageName;
    private long mEventTime;
    private int mMovementGranularity;
    private int mAction;
    private int mContentChangeTypes;
    private final List<AccessibilityRecord> mRecords = new ArrayList<AccessibilityRecord>();

    public AccessibilityEvent() {}

    public AccessibilityEvent(int eventType) {
        mEventType = eventType;
    }

    // AOSP pools events; a fresh instance is observably identical since a11y is
    // disabled here. obtain() variants mirror the SDK overloads.
    public static AccessibilityEvent obtain() { return new AccessibilityEvent(); }
    public static AccessibilityEvent obtain(int eventType) { return new AccessibilityEvent(eventType); }
    public static AccessibilityEvent obtain(AccessibilityEvent event) {
        AccessibilityEvent e = new AccessibilityEvent(event.mEventType);
        e.mPackageName = event.mPackageName;
        e.setClassName(event.getClassName()); // className lives on AccessibilityRecord
        e.mEventTime = event.mEventTime;
        e.mAction = event.mAction;
        e.mContentChangeTypes = event.mContentChangeTypes;
        return e;
    }

    @Override
    public void recycle() {
        // Drop references; the (absent) pool reclaim is a no-op for us.
        super.recycle();
        mPackageName = null;
        mRecords.clear();
    }

    public int getEventType() { return mEventType; }
    public void setEventType(int eventType) { mEventType = eventType; }

    public CharSequence getPackageName() { return mPackageName; }
    public void setPackageName(CharSequence packageName) { mPackageName = packageName; }

    // getClassName()/setClassName() are inherited from AccessibilityRecord.

    public long getEventTime() { return mEventTime; }
    public void setEventTime(long eventTime) { mEventTime = eventTime; }

    public int getMovementGranularity() { return mMovementGranularity; }
    public void setMovementGranularity(int granularity) { mMovementGranularity = granularity; }

    public int getAction() { return mAction; }
    public void setAction(int action) { mAction = action; }

    public int getContentChangeTypes() { return mContentChangeTypes; }
    public void setContentChangeTypes(int changeTypes) { mContentChangeTypes = changeTypes; }

    // Sub-records (AOSP appends per-child records for window-content changes).
    public void appendRecord(AccessibilityRecord record) {
        if (record != null) mRecords.add(record);
    }
    public AccessibilityRecord getRecord(int index) {
        return (index >= 0 && index < mRecords.size()) ? mRecords.get(index) : null;
    }
    public int getRecordCount() { return mRecords.size(); }

    public static String eventTypeToString(int eventType) {
        switch (eventType) {
            case TYPE_VIEW_CLICKED: return "TYPE_VIEW_CLICKED";
            case TYPE_VIEW_FOCUSED: return "TYPE_VIEW_FOCUSED";
            case TYPE_VIEW_TEXT_CHANGED: return "TYPE_VIEW_TEXT_CHANGED";
            case TYPE_WINDOW_STATE_CHANGED: return "TYPE_WINDOW_STATE_CHANGED";
            case TYPE_WINDOW_CONTENT_CHANGED: return "TYPE_WINDOW_CONTENT_CHANGED";
            case TYPE_ANNOUNCEMENT: return "TYPE_ANNOUNCEMENT";
            default: return "EVENT_TYPE_0x" + Integer.toHexString(eventType);
        }
    }

    @Override
    public String toString() {
        return "AccessibilityEvent[eventType=" + eventTypeToString(mEventType)
                + "; packageName=" + mPackageName + "; className=" + getClassName() + "]";
    }

    private boolean mAccessibilityDataSensitive;
    public void setAccessibilityDataSensitive(boolean sensitive) { mAccessibilityDataSensitive = sensitive; }
    public boolean isAccessibilityDataSensitive() { return mAccessibilityDataSensitive; }

    // ======================================================================
    //  AOSP API-36 surface completion (Mac.apk API-gap lane, android.view).
    // ======================================================================

    /** AOSP AccessibilityEvent.SPEECH_STATE_* (API 34): the bits reported by a
     *  TYPE_SPEECH_STATE_CHANGE event. */
    public static final int SPEECH_STATE_SPEAKING_START = 0x00000001;
    public static final int SPEECH_STATE_SPEAKING_END = 0x00000002;
    public static final int SPEECH_STATE_LISTENING_START = 0x00000004;
    public static final int SPEECH_STATE_LISTENING_END = 0x00000008;

    private int mSpeechStateChangeTypes;
    /** AOSP AccessibilityEvent.setSpeechStateChangeTypes(int) /
     *  getSpeechStateChangeTypes(): the SPEECH_STATE_* bitmask carried by a
     *  TYPE_SPEECH_STATE_CHANGE event. A plain round-trip on AOSP too. */
    public void setSpeechStateChangeTypes(int state) { mSpeechStateChangeTypes = state; }
    public int getSpeechStateChangeTypes() { return mSpeechStateChangeTypes; }

    /** AOSP AccessibilityEvent.getWindowChanges(): the WINDOWS_CHANGE_* bitmask
     *  carried by a TYPE_WINDOWS_CHANGED event. Only the SYSTEM produces such an
     *  event (there is no public setter -- AOSP's setWindowChanges is @hide), so
     *  an app-constructed event carries none, which is 0. */
    public int getWindowChanges() { return 0; }

    // ---- Parcelable ---------------------------------------------------------
    // Same reasoning as AccessibilityNodeInfo's: the event's identity is its
    // SOURCE (a live View) plus the record state it inherits, and the source
    // cannot be rebuilt from bytes with no accessibility-id registry. An empty
    // body makes android/os/Parcel keep the live event, so a round trip in this
    // single process returns the SAME object.
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) { }

    /** AOSP AccessibilityEvent.initFromParcel(Parcel): read this event's state
     *  back out of a parcel written by writeToParcel. Public on AOSP (it is how
     *  the system re-uses a pooled event), and the exact inverse of the
     *  writeToParcel above -- which writes nothing, so this reads nothing. */
    public void initFromParcel(android.os.Parcel parcel) { }

    public static final android.os.Parcelable.Creator<AccessibilityEvent> CREATOR =
            new android.os.Parcelable.Creator<AccessibilityEvent>() {
        @Override public AccessibilityEvent createFromParcel(android.os.Parcel parcel) {
            AccessibilityEvent event = new AccessibilityEvent();
            event.initFromParcel(parcel);
            return event;
        }
        @Override public AccessibilityEvent[] newArray(int size) {
            return new AccessibilityEvent[size];
        }
    };
}
