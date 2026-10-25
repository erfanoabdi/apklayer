// android.hardware.camera2.params.Face -- one detected face, the element type
// of CaptureResult.STATISTICS_FACES.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/params/Face.java
// (aosp-mirror, android16-release). The two constructors are @hide in AOSP (the
// public route is Face.Builder, API 34); they are kept public here as AOSP
// declares them, and they validate exactly as AOSP does.
package android.hardware.camera2.params;

import android.graphics.Point;
import android.graphics.Rect;

public final class Face {

    /** AOSP Face.java:41 -- this device cannot track faces across frames. */
    public static final int ID_UNSUPPORTED = -1;
    /** AOSP :46 */
    public static final int SCORE_MIN = 1;
    /** AOSP :51 */
    public static final int SCORE_MAX = 100;

    private Rect mBounds;
    private int mScore;
    private int mId;
    private Point mLeftEye;
    private Point mRightEye;
    private Point mMouth;

    public Face(Rect bounds, int score, int id,
            Point leftEyePosition, Point rightEyePosition, Point mouthPosition) {
        init(bounds, score, id, leftEyePosition, rightEyePosition, mouthPosition);
    }

    public Face(Rect bounds, int score) {
        init(bounds, score, ID_UNSUPPORTED, null, null, null);
    }

    // AOSP :95. The landmark trio is all-or-nothing, and must be absent when the
    // id is ID_UNSUPPORTED -- a face without an id is a face the HAL did not
    // track, so it cannot have per-feature positions either.
    private void init(Rect bounds, int score, int id,
            Point leftEyePosition, Point rightEyePosition, Point mouthPosition) {
        checkNotNull("bounds", bounds);
        checkScore(score);
        checkId(id);
        if (id == ID_UNSUPPORTED) {
            checkNull("leftEyePosition", leftEyePosition);
            checkNull("rightEyePosition", rightEyePosition);
            checkNull("mouthPosition", mouthPosition);
        }
        checkFace(leftEyePosition, rightEyePosition, mouthPosition);
        mBounds = bounds;
        mScore = score;
        mId = id;
        mLeftEye = leftEyePosition;
        mRightEye = rightEyePosition;
        mMouth = mouthPosition;
    }

    public Rect getBounds() { return mBounds; }
    public int getScore() { return mScore; }
    public int getId() { return mId; }
    public Point getLeftEyePosition() { return mLeftEye; }
    public Point getRightEyePosition() { return mRightEye; }
    public Point getMouthPosition() { return mMouth; }

    @Override public String toString() {
        return String.format("{ bounds: %s, score: %s, id: %d, "
                        + "leftEyePosition: %s, rightEyePosition: %s, mouthPosition: %s }",
                mBounds, mScore, mId, mLeftEye, mRightEye, mMouth);
    }

    private static void checkNotNull(String name, Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException(name + " was required, but it was null");
        }
    }

    private static void checkNull(String name, Object obj) {
        if (obj != null) {
            throw new IllegalArgumentException(name + " was required to be null, but it wasn't");
        }
    }

    private static void checkScore(int score) {
        if (score < SCORE_MIN || score > SCORE_MAX) {
            throw new IllegalArgumentException("Confidence out of range");
        }
    }

    private static void checkId(int id) {
        if (id < 0 && id != ID_UNSUPPORTED) {
            throw new IllegalArgumentException("Id out of range");
        }
    }

    private static void checkFace(Point leftEyePosition, Point rightEyePosition,
            Point mouthPosition) {
        if (leftEyePosition != null || rightEyePosition != null || mouthPosition != null) {
            if (leftEyePosition == null || rightEyePosition == null || mouthPosition == null) {
                throw new IllegalArgumentException("If any of leftEyePosition, rightEyePosition, "
                        + "or mouthPosition are non-null, all three must be non-null.");
            }
        }
    }

    /** AOSP :265. API 34. Single-use: build() marks the builder spent. */
    public static final class Builder {
        private long mBuilderFieldsSet = 0L;
        private static final long FIELD_BUILT = 1 << 0;
        private static final long FIELD_BOUNDS = 1 << 1;
        private static final long FIELD_SCORE = 1 << 2;
        private static final long FIELD_ID = 1 << 3;
        private static final long FIELD_LEFT_EYE = 1 << 4;
        private static final long FIELD_RIGHT_EYE = 1 << 5;
        private static final long FIELD_MOUTH = 1 << 6;
        private static final String FIELD_NAME_BOUNDS = "bounds";
        private static final String FIELD_NAME_SCORE = "score";
        private static final String FIELD_NAME_LEFT_EYE = "left eye";
        private static final String FIELD_NAME_RIGHT_EYE = "right eye";
        private static final String FIELD_NAME_MOUTH = "mouth";

        private Rect mBounds = null;
        private int mScore = 0;
        private int mId = ID_UNSUPPORTED;
        private Point mLeftEye = null;
        private Point mRightEye = null;
        private Point mMouth = null;

        public Builder() {}

        public Builder(Face current) {
            mBounds = current.mBounds;
            mScore = current.mScore;
            mId = current.mId;
            mLeftEye = current.mLeftEye;
            mRightEye = current.mRightEye;
            mMouth = current.mMouth;
        }

        public Builder setBounds(Rect bounds) {
            checkNotUsed();
            mBuilderFieldsSet |= FIELD_BOUNDS;
            mBounds = bounds;
            return this;
        }

        public Builder setScore(int score) {
            checkNotUsed();
            checkScore(score);
            mBuilderFieldsSet |= FIELD_SCORE;
            mScore = score;
            return this;
        }

        public Builder setId(int id) {
            checkNotUsed();
            checkId(id);
            mBuilderFieldsSet |= FIELD_ID;
            mId = id;
            return this;
        }

        public Builder setLeftEyePosition(Point leftEyePosition) {
            checkNotUsed();
            mBuilderFieldsSet |= FIELD_LEFT_EYE;
            mLeftEye = leftEyePosition;
            return this;
        }

        public Builder setRightEyePosition(Point rightEyePosition) {
            checkNotUsed();
            mBuilderFieldsSet |= FIELD_RIGHT_EYE;
            mRightEye = rightEyePosition;
            return this;
        }

        public Builder setMouthPosition(Point mouthPosition) {
            checkNotUsed();
            mBuilderFieldsSet |= FIELD_MOUTH;
            mMouth = mouthPosition;
            return this;
        }

        public Face build() {
            checkNotUsed();
            checkFieldSet(FIELD_BOUNDS, FIELD_NAME_BOUNDS);
            checkFieldSet(FIELD_SCORE, FIELD_NAME_SCORE);
            if (mId == ID_UNSUPPORTED) {
                checkIdUnsupportedThenNull(mLeftEye, FIELD_NAME_LEFT_EYE);
                checkIdUnsupportedThenNull(mRightEye, FIELD_NAME_RIGHT_EYE);
                checkIdUnsupportedThenNull(mMouth, FIELD_NAME_MOUTH);
            }
            checkFace(mLeftEye, mRightEye, mMouth);
            mBuilderFieldsSet |= FIELD_BUILT;
            return new Face(mBounds, mScore, mId, mLeftEye, mRightEye, mMouth);
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & FIELD_BUILT) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }

        private void checkFieldSet(long field, String fieldName) {
            if ((mBuilderFieldsSet & field) == 0) {
                throw new IllegalStateException(
                        "Field \"" + fieldName + "\" must be set before building.");
            }
        }

        private void checkIdUnsupportedThenNull(Object obj, String fieldName) {
            if (obj != null) {
                throw new IllegalArgumentException("Field \"" + fieldName
                        + "\" must be unset or null if id is ID_UNSUPPORTED.");
            }
        }
    }
}
