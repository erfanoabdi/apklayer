package android.accessibilityservice;

import android.graphics.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * A gesture an accessibility service asks the platform to inject, described as
 * up to MAX_STROKE_COUNT timed strokes along Paths.
 *
 * Mac.apk injects nothing on behalf of a service (AccessibilityService.
 * dispatchGesture returns false and never calls the callback), but the
 * description itself is a pure value object and is fully honest here: an app
 * that builds one and reads its strokes back gets exactly what it put in, and
 * the builder enforces AOSP's stroke-count and total-duration limits so an app
 * relying on the IllegalStateException still sees it.
 *
 * Source: frameworks/base/core/java/android/accessibilityservice/GestureDescription.java
 */
public final class GestureDescription {

    // Private in AOSP too; exposed only through the two static getters below.
    // frameworks/base/.../GestureDescription.java:43,48
    private static final int MAX_STROKE_COUNT = 20;
    private static final long MAX_GESTURE_DURATION_MS = 60 * 1000;

    private final List<StrokeDescription> mStrokes;
    private final int mDisplayId;

    private GestureDescription(List<StrokeDescription> strokes, int displayId) {
        mStrokes = strokes;
        mDisplayId = displayId;
    }

    public static int getMaxStrokeCount() { return MAX_STROKE_COUNT; }

    public static long getMaxGestureDuration() { return MAX_GESTURE_DURATION_MS; }

    public int getStrokeCount() { return mStrokes.size(); }

    public StrokeDescription getStroke(int index) { return mStrokes.get(index); }

    public int getDisplayId() { return mDisplayId; }

    /** Accumulates strokes; build() snapshots them so a later addStroke cannot
     *  mutate a description already handed out. */
    public static class Builder {
        private final List<StrokeDescription> mStrokes = new ArrayList<StrokeDescription>();
        // AOSP defaults to the default display. Mac.apk has exactly one window,
        // so android.view.Display.DEFAULT_DISPLAY (0) is the only valid answer.
        private int mDisplayId = android.view.Display.DEFAULT_DISPLAY;

        public Builder() { }

        public Builder addStroke(StrokeDescription strokeDescription) {
            if (mStrokes.size() >= MAX_STROKE_COUNT) {
                throw new IllegalStateException(
                        "Attempting to add too many strokes to a gesture");
            }
            mStrokes.add(strokeDescription);
            if (totalDuration() > MAX_GESTURE_DURATION_MS) {
                mStrokes.remove(mStrokes.size() - 1);
                throw new IllegalStateException(
                        "Gesture would exceed maximum duration with new stroke");
            }
            return this;
        }

        public Builder setDisplayId(int displayId) { mDisplayId = displayId; return this; }

        public GestureDescription build() {
            if (mStrokes.size() == 0) {
                throw new IllegalStateException("Gestures must have at least one stroke");
            }
            return new GestureDescription(
                    new ArrayList<StrokeDescription>(mStrokes), mDisplayId);
        }

        private long totalDuration() {
            long max = 0;
            for (int i = 0; i < mStrokes.size(); i++) {
                StrokeDescription s = mStrokes.get(i);
                long end = s.getStartTime() + s.getDuration();
                if (end > max) max = end;
            }
            return max;
        }
    }

    /** One finger's path through time. */
    public static class StrokeDescription {
        // AOSP: an id assigned when the stroke is queued for injection; -1 until
        // then. We never inject, so it stays INVALID_STROKE_ID, which is the same
        // value a not-yet-dispatched stroke carries on a device.
        private static final int INVALID_STROKE_ID = -1;

        private final Path mPath;
        private final long mStartTime;
        private final long mDuration;
        private final boolean mContinued;
        private final int mId = INVALID_STROKE_ID;
        private boolean mContinuedStrokeId;

        public StrokeDescription(Path path, long startTime, long duration) {
            this(path, startTime, duration, false);
        }

        public StrokeDescription(Path path, long startTime, long duration,
                boolean willContinue) {
            if (duration <= 0) {
                throw new IllegalArgumentException("Duration must be positive");
            }
            if (startTime < 0) {
                throw new IllegalArgumentException("Start time must not be negative");
            }
            mPath = path;
            mStartTime = startTime;
            mDuration = duration;
            mContinued = willContinue;
        }

        public Path getPath() { return mPath; }

        public long getStartTime() { return mStartTime; }

        public long getDuration() { return mDuration; }

        public boolean willContinue() { return mContinued; }

        /**
         * AOSP throws if this stroke did not declare willContinue. Mirror that:
         * the exception is the whole contract of the method, and an app that
         * builds a continued-stroke chain would otherwise silently get a stroke
         * that the platform will reject.
         */
        public StrokeDescription continueStroke(Path path, long startTime, long duration,
                boolean willContinue) {
            if (!mContinued) {
                throw new IllegalStateException(
                        "Only strokes marked willContinue can be continued");
            }
            StrokeDescription next =
                    new StrokeDescription(path, startTime, duration, willContinue);
            next.mContinuedStrokeId = true;
            return next;
        }

        /** @hide-shaped helper kept package-visible so the field is not dead. */
        boolean isContinuation() { return mContinuedStrokeId; }

        int getId() { return mId; }
    }
}
