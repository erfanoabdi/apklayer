package android.accessibilityservice;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * API 30+ description of a gesture the platform recognised on behalf of an
 * accessibility service, delivered to AccessibilityService.onGesture().
 *
 * Mac.apk never recognises accessibility gestures (nothing enables touch
 * exploration), so we never construct one -- but the CONSTRUCTOR is public in
 * AOSP and a service's own tests build these, so the value object is real: what
 * you put in is what the getters return.
 *
 * Source: frameworks/base/core/java/android/accessibilityservice/AccessibilityGestureEvent.java
 */
public final class AccessibilityGestureEvent implements Parcelable {

    private final int mGestureId;
    private final int mDisplayId;
    private final List<MotionEvent> mMotionEvents = new ArrayList<MotionEvent>();

    public AccessibilityGestureEvent(int gestureId, int displayId,
            List<MotionEvent> motionEvents) {
        mGestureId = gestureId;
        mDisplayId = displayId;
        if (motionEvents != null) mMotionEvents.addAll(motionEvents);
    }

    public int getDisplayId() { return mDisplayId; }

    public int getGestureId() { return mGestureId; }

    public List<MotionEvent> getMotionEvents() { return mMotionEvents; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mGestureId);
        dest.writeInt(mDisplayId);
        dest.writeTypedList(mMotionEvents);
    }

    @Override public String toString() {
        return "AccessibilityGestureEvent[gestureId="
                + gestureIdToString(mGestureId)
                + ", displayId=" + mDisplayId
                + ", motionEvents=" + mMotionEvents.size() + "]";
    }

    /**
     * AOSP's switch also names two @hide ids (GESTURE_PASSTHROUGH,
     * GESTURE_TOUCH_EXPLORATION) that no app can reference; every id an app CAN
     * name is a public AccessibilityService.GESTURE_* constant and is covered
     * here. Unknown ids fall through to Integer.toHexString, exactly as AOSP.
     */
    public static String gestureIdToString(int id) {
        switch (id) {
            case AccessibilityService.GESTURE_UNKNOWN: return "GESTURE_UNKNOWN";
            case AccessibilityService.GESTURE_SWIPE_UP: return "GESTURE_SWIPE_UP";
            case AccessibilityService.GESTURE_SWIPE_DOWN: return "GESTURE_SWIPE_DOWN";
            case AccessibilityService.GESTURE_SWIPE_LEFT: return "GESTURE_SWIPE_LEFT";
            case AccessibilityService.GESTURE_SWIPE_RIGHT: return "GESTURE_SWIPE_RIGHT";
            case AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT:
                return "GESTURE_SWIPE_LEFT_AND_RIGHT";
            case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT:
                return "GESTURE_SWIPE_RIGHT_AND_LEFT";
            case AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN:
                return "GESTURE_SWIPE_UP_AND_DOWN";
            case AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP:
                return "GESTURE_SWIPE_DOWN_AND_UP";
            case AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP:
                return "GESTURE_SWIPE_LEFT_AND_UP";
            case AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN:
                return "GESTURE_SWIPE_LEFT_AND_DOWN";
            case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP:
                return "GESTURE_SWIPE_RIGHT_AND_UP";
            case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN:
                return "GESTURE_SWIPE_RIGHT_AND_DOWN";
            case AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT:
                return "GESTURE_SWIPE_UP_AND_LEFT";
            case AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT:
                return "GESTURE_SWIPE_UP_AND_RIGHT";
            case AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT:
                return "GESTURE_SWIPE_DOWN_AND_LEFT";
            case AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT:
                return "GESTURE_SWIPE_DOWN_AND_RIGHT";
            case AccessibilityService.GESTURE_DOUBLE_TAP: return "GESTURE_DOUBLE_TAP";
            case AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD:
                return "GESTURE_DOUBLE_TAP_AND_HOLD";
            case AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP:
                return "GESTURE_2_FINGER_SINGLE_TAP";
            case AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP:
                return "GESTURE_2_FINGER_DOUBLE_TAP";
            case AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP:
                return "GESTURE_2_FINGER_TRIPLE_TAP";
            case AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP:
                return "GESTURE_3_FINGER_SINGLE_TAP";
            case AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP:
                return "GESTURE_3_FINGER_DOUBLE_TAP";
            case AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP:
                return "GESTURE_3_FINGER_TRIPLE_TAP";
            case AccessibilityService.GESTURE_2_FINGER_SWIPE_UP:
                return "GESTURE_2_FINGER_SWIPE_UP";
            case AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN:
                return "GESTURE_2_FINGER_SWIPE_DOWN";
            case AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT:
                return "GESTURE_2_FINGER_SWIPE_LEFT";
            case AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT:
                return "GESTURE_2_FINGER_SWIPE_RIGHT";
            case AccessibilityService.GESTURE_3_FINGER_SWIPE_UP:
                return "GESTURE_3_FINGER_SWIPE_UP";
            case AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN:
                return "GESTURE_3_FINGER_SWIPE_DOWN";
            case AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT:
                return "GESTURE_3_FINGER_SWIPE_LEFT";
            case AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT:
                return "GESTURE_3_FINGER_SWIPE_RIGHT";
            case AccessibilityService.GESTURE_4_FINGER_SWIPE_UP:
                return "GESTURE_4_FINGER_SWIPE_UP";
            case AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN:
                return "GESTURE_4_FINGER_SWIPE_DOWN";
            case AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT:
                return "GESTURE_4_FINGER_SWIPE_LEFT";
            case AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT:
                return "GESTURE_4_FINGER_SWIPE_RIGHT";
            case AccessibilityService.GESTURE_4_FINGER_SINGLE_TAP:
                return "GESTURE_4_FINGER_SINGLE_TAP";
            case AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP:
                return "GESTURE_4_FINGER_DOUBLE_TAP";
            case AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP:
                return "GESTURE_4_FINGER_TRIPLE_TAP";
            case AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD:
                return "GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD";
            case AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD:
                return "GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD";
            case AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD:
                return "GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD";
            case AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD:
                return "GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD";
            case AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD:
                return "GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD";
            case AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD:
                return "GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD";
            default: return Integer.toHexString(id);
        }
    }

    public static final Creator<AccessibilityGestureEvent> CREATOR =
            new Creator<AccessibilityGestureEvent>() {
        @Override public AccessibilityGestureEvent createFromParcel(Parcel parcel) {
            int gestureId = parcel.readInt();
            int displayId = parcel.readInt();
            List<MotionEvent> events =
                    parcel.createTypedArrayList(MotionEvent.CREATOR);
            return new AccessibilityGestureEvent(gestureId, displayId, events);
        }
        @Override public AccessibilityGestureEvent[] newArray(int size) {
            return new AccessibilityGestureEvent[size];
        }
    };
}
