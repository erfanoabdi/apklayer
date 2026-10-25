// android.view.textclassifier.SelectionEvent -- a smart-selection telemetry
// event a widget reports back to the classifier so the model can learn.
// Source: frameworks/base
// core/java/android/view/textclassifier/SelectionEvent.java (android16-release).
//
// Mac.apk has no on-device model (TextClassifier.NO_OP), so
// TextClassifier.onSelectionEvent drops what it is given -- which is exactly
// what AOSP's NO_OP does. The type must exist because that method names it, and
// because a widget CONSTRUCTS one before calling.
package android.view.textclassifier;

public final class SelectionEvent implements android.os.Parcelable {
    public static final int ACTION_OVERTYPE = 100;
    public static final int ACTION_COPY = 101;
    public static final int ACTION_PASTE = 102;
    public static final int ACTION_CUT = 103;
    public static final int ACTION_SHARE = 104;
    public static final int ACTION_SMART_SHARE = 105;
    public static final int ACTION_DRAG = 106;
    public static final int ACTION_ABANDON = 107;
    public static final int ACTION_OTHER = 108;
    public static final int ACTION_SELECT_ALL = 200;
    public static final int ACTION_RESET = 201;

    public static final int EVENT_SELECTION_STARTED = 1;
    public static final int EVENT_SELECTION_MODIFIED = 2;
    public static final int EVENT_SMART_SELECTION_SINGLE = 3;
    public static final int EVENT_SMART_SELECTION_MULTI = 4;
    public static final int EVENT_AUTO_SELECTION = 5;

    public static final int INVOCATION_MANUAL = 1;
    public static final int INVOCATION_LINK = 2;
    public static final int INVOCATION_UNKNOWN = 0;

    private final int mAbsoluteStart;
    private final int mAbsoluteEnd;
    private final int mEventType;
    private final String mEntityType;
    private String mPackageName = "";
    private String mWidgetType = TextClassifier.WIDGET_TYPE_UNKNOWN;
    private String mWidgetVersion;
    private int mInvocationMethod = INVOCATION_UNKNOWN;
    private String mResultId;
    private long mEventTime;
    private long mDurationSinceSessionStart;
    private long mDurationSincePreviousEvent;
    private int mEventIndex;
    private TextClassificationSessionId mSessionId;
    private int mStart;
    private int mEnd;
    private int mSmartStart;
    private int mSmartEnd;

    private SelectionEvent(int start, int end, int eventType, String entityType,
            int invocationMethod, String resultId) {
        mAbsoluteStart = start;
        mAbsoluteEnd = end;
        mStart = start;
        mEnd = end;
        mSmartStart = start;
        mSmartEnd = end;
        mEventType = eventType;
        mEntityType = entityType != null ? entityType : TextClassifier.TYPE_UNKNOWN;
        mInvocationMethod = invocationMethod;
        mResultId = resultId;
        mEventTime = System.currentTimeMillis();
    }

    /** AOSP SelectionEvent.createSelectionStartedEvent(int invocationMethod, int start). */
    public static SelectionEvent createSelectionStartedEvent(int invocationMethod, int start) {
        return new SelectionEvent(start, start + 1, EVENT_SELECTION_STARTED,
                TextClassifier.TYPE_UNKNOWN, invocationMethod, null);
    }
    /** AOSP SelectionEvent.createSelectionModifiedEvent(int, int). */
    public static SelectionEvent createSelectionModifiedEvent(int start, int end) {
        checkRange(start, end);
        return new SelectionEvent(start, end, EVENT_SELECTION_MODIFIED,
                TextClassifier.TYPE_UNKNOWN, INVOCATION_UNKNOWN, null);
    }
    /** AOSP SelectionEvent.createSelectionModifiedEvent(int, int, TextClassification). */
    public static SelectionEvent createSelectionModifiedEvent(int start, int end,
            TextClassification classification) {
        checkRange(start, end);
        java.util.Objects.requireNonNull(classification);
        final String entityType = classification.getEntityCount() > 0
                ? classification.getEntity(0) : TextClassifier.TYPE_UNKNOWN;
        return new SelectionEvent(start, end, EVENT_SELECTION_MODIFIED, entityType,
                INVOCATION_UNKNOWN, classification.getId());
    }
    /** AOSP SelectionEvent.createSelectionModifiedEvent(int, int, TextSelection). */
    public static SelectionEvent createSelectionModifiedEvent(int start, int end,
            TextSelection selection) {
        checkRange(start, end);
        java.util.Objects.requireNonNull(selection);
        final String entityType = selection.getEntityCount() > 0
                ? selection.getEntity(0) : TextClassifier.TYPE_UNKNOWN;
        return new SelectionEvent(start, end, EVENT_AUTO_SELECTION, entityType,
                INVOCATION_UNKNOWN, selection.getId());
    }
    /** AOSP SelectionEvent.createSelectionActionEvent(int, int, int actionType). */
    public static SelectionEvent createSelectionActionEvent(int start, int end, int actionType) {
        checkRange(start, end);
        checkActionType(actionType);
        return new SelectionEvent(start, end, actionType, TextClassifier.TYPE_UNKNOWN,
                INVOCATION_UNKNOWN, null);
    }
    /** AOSP SelectionEvent.createSelectionActionEvent(int, int, int, TextClassification). */
    public static SelectionEvent createSelectionActionEvent(int start, int end, int actionType,
            TextClassification classification) {
        checkRange(start, end);
        checkActionType(actionType);
        java.util.Objects.requireNonNull(classification);
        final String entityType = classification.getEntityCount() > 0
                ? classification.getEntity(0) : TextClassifier.TYPE_UNKNOWN;
        return new SelectionEvent(start, end, actionType, entityType, INVOCATION_UNKNOWN,
                classification.getId());
    }

    /** AOSP SelectionEvent.isTerminal(int eventType): does this event END the
     *  selection session? */
    public static boolean isTerminal(int eventType) {
        switch (eventType) {
            case ACTION_OVERTYPE:
            case ACTION_COPY:
            case ACTION_PASTE:
            case ACTION_CUT:
            case ACTION_SHARE:
            case ACTION_SMART_SHARE:
            case ACTION_DRAG:
            case ACTION_ABANDON:
            case ACTION_OTHER:
                return true;
            default:
                return false;
        }
    }

    private static void checkRange(int start, int end) {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid range: " + start + ", " + end);
        }
    }
    private static void checkActionType(int actionType) {
        switch (actionType) {
            case ACTION_OVERTYPE: case ACTION_COPY: case ACTION_PASTE: case ACTION_CUT:
            case ACTION_SHARE: case ACTION_SMART_SHARE: case ACTION_DRAG:
            case ACTION_ABANDON: case ACTION_OTHER: case ACTION_SELECT_ALL:
            case ACTION_RESET:
                return;
            default:
                throw new IllegalArgumentException(
                        String.format(java.util.Locale.US, "%d is not an eventType", actionType));
        }
    }

    public int getEventType() { return mEventType; }
    public String getEntityType() { return mEntityType; }
    public String getPackageName() { return mPackageName; }
    public String getWidgetType() { return mWidgetType; }
    public String getWidgetVersion() { return mWidgetVersion; }
    public int getInvocationMethod() { return mInvocationMethod; }
    public String getResultId() { return mResultId; }
    public long getEventTime() { return mEventTime; }
    public long getDurationSinceSessionStart() { return mDurationSinceSessionStart; }
    public long getDurationSincePreviousEvent() { return mDurationSincePreviousEvent; }
    public int getEventIndex() { return mEventIndex; }
    public TextClassificationSessionId getSessionId() { return mSessionId; }
    public int getStart() { return mStart; }
    public int getEnd() { return mEnd; }
    public int getSmartStart() { return mSmartStart; }
    public int getSmartEnd() { return mSmartEnd; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mAbsoluteStart);
        dest.writeInt(mAbsoluteEnd);
        dest.writeInt(mEventType);
        dest.writeString(mEntityType);
        dest.writeInt(mInvocationMethod);
        dest.writeString(mResultId);
        dest.writeLong(mEventTime);
        dest.writeInt(mEventIndex);
        dest.writeInt(mStart);
        dest.writeInt(mEnd);
        dest.writeInt(mSmartStart);
        dest.writeInt(mSmartEnd);
    }
    public static final android.os.Parcelable.Creator<SelectionEvent> CREATOR =
            new android.os.Parcelable.Creator<SelectionEvent>() {
        @Override public SelectionEvent createFromParcel(android.os.Parcel in) {
            SelectionEvent e = new SelectionEvent(in.readInt(), in.readInt(), in.readInt(),
                    in.readString(), in.readInt(), in.readString());
            e.mEventTime = in.readLong();
            e.mEventIndex = in.readInt();
            e.mStart = in.readInt();
            e.mEnd = in.readInt();
            e.mSmartStart = in.readInt();
            e.mSmartEnd = in.readInt();
            return e;
        }
        @Override public SelectionEvent[] newArray(int size) { return new SelectionEvent[size]; }
    };
}
