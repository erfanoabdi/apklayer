package android.view;
import android.content.ClipData;
import android.content.ClipDescription;
/** A drag-and-drop event delivered to a View's onDragEvent / OnDragListener.
 *  Generated in-process by android.view.MacApkDragSession (View.startDragAndDrop,
 *  2026-09-29) for drags inside the app's own window.
 *  Source: frameworks/base/core/java/android/view/DragEvent.java */
public class DragEvent implements android.os.Parcelable {
    public static final int ACTION_DRAG_STARTED  = 1;
    public static final int ACTION_DRAG_LOCATION = 2;
    public static final int ACTION_DROP          = 3;
    public static final int ACTION_DRAG_ENDED    = 4;
    public static final int ACTION_DRAG_ENTERED  = 5;
    public static final int ACTION_DRAG_EXITED   = 6;

    // Package-private as in AOSP (DragEvent.java: "int mAction; float mX, mY;
    // ClipDescription mClipDescription; ClipData mClipData; Object mLocalState;
    // boolean mDragResult; boolean mEventHandlerWasCalled;"): ViewGroup
    // .dispatchDragEvent localizes mX/mY per child and the root rewrites
    // mAction for ENTERED/EXITED, exactly as AOSP does.
    int mAction;
    float mX, mY;
    ClipData mClipData;
    ClipDescription mClipDescription;
    Object mLocalState;
    boolean mDragResult;
    /** AOSP: set by View.dispatchDragEvent; ViewRootImpl reads it to know a
     *  LOCATION reached no handler (then no view has drag focus). */
    boolean mEventHandlerWasCalled;

    public DragEvent() {}

    public int getAction() { return mAction; }
    public float getX() { return mX; }
    public float getY() { return mY; }
    public ClipData getClipData() { return mClipData; }
    public ClipDescription getClipDescription() { return mClipDescription; }
    public Object getLocalState() { return mLocalState; }
    public boolean getResult() { return mDragResult; }
    public void recycle() {}

    /** AOSP DragEvent.obtain(DragEvent source) (hidden): a copy. */
    public static DragEvent obtain(DragEvent source) {
        DragEvent e = obtain(source.mAction, source.mX, source.mY, source.mLocalState,
                source.mClipDescription, source.mClipData, source.mDragResult);
        e.mEventHandlerWasCalled = source.mEventHandlerWasCalled;
        return e;
    }

    @Override public String toString() {
        return "DragEvent{action=" + mAction + " @ (" + mX + ", " + mY + ") desc="
                + mClipDescription + " data=" + mClipData + " local=" + mLocalState
                + " result=" + mDragResult + "}";
    }

    /** Mac.apk-internal factory (not AOSP public surface) for the drag bridge. */
    public static DragEvent obtain(int action, float x, float y, Object localState,
            ClipDescription description, ClipData data, boolean result) {
        DragEvent e = new DragEvent();
        e.mAction = action; e.mX = x; e.mY = y; e.mLocalState = localState;
        e.mClipDescription = description; e.mClipData = data; e.mDragResult = result;
        return e;
    }

    // AOSP field order: action, x, y, offsetX, offsetY, flags, dragResult,
    // clipData(nullable), clipDescription(nullable), dragSurface(nullable),
    // dragAndDropPermissions(nullable) -- DragEvent.java L593-660. This class
    // does not track offsetX/offsetY/flags/dragSurface/permissions, so those
    // slots are simply absent. mLocalState is deliberately NOT written here
    // either, matching AOSP: it is same-process-only by design (an arbitrary
    // app Object, not a Parcelable contract), so both the real DragEvent and
    // this one correctly lose it across a Parcel boundary -- that is the
    // documented behaviour, not an omission.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mAction);
        dest.writeFloat(mX);
        dest.writeFloat(mY);
        dest.writeInt(mDragResult ? 1 : 0);
        dest.writeInt(mClipData != null ? 1 : 0);
        if (mClipData != null) mClipData.writeToParcel(dest, flags);
        dest.writeInt(mClipDescription != null ? 1 : 0);
        if (mClipDescription != null) mClipDescription.writeToParcel(dest, flags);
    }

    public static final android.os.Parcelable.Creator<DragEvent> CREATOR =
            new android.os.Parcelable.Creator<DragEvent>() {
        @Override public DragEvent createFromParcel(android.os.Parcel in) {
            DragEvent e = new DragEvent();
            e.mAction = in.readInt();
            e.mX = in.readFloat();
            e.mY = in.readFloat();
            e.mDragResult = in.readInt() != 0;
            if (in.readInt() != 0) e.mClipData = ClipData.CREATOR.createFromParcel(in);
            if (in.readInt() != 0) e.mClipDescription = ClipDescription.CREATOR.createFromParcel(in);
            return e;
        }
        @Override public DragEvent[] newArray(int size) { return new DragEvent[size]; }
    };
}
