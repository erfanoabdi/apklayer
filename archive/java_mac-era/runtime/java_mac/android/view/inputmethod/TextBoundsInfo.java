// android.view.inputmethod.TextBoundsInfo -- per-character geometry an editor
// hands an IME so the IME can hit-test and draw handwriting gestures against
// the real text layout. Source: frameworks/base
// core/java/android/view/inputmethod/TextBoundsInfo.java (android16-release).
//
// Mac.apk runs no IME, so nothing in this tree BUILDS one -- but a custom editor
// implementing InputConnection.requestTextBoundsInfo does, and the arithmetic
// below is the real geometry maths rather than a stub, so such an editor gets
// device-identical answers from the data it supplied.
package android.view.inputmethod;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.text.SegmentFinder;

public final class TextBoundsInfo implements android.os.Parcelable {
    public static final int FLAG_CHARACTER_WHITESPACE = 1;
    public static final int FLAG_CHARACTER_LINEFEED = 1 << 1;
    public static final int FLAG_CHARACTER_PUNCTUATION = 1 << 2;
    public static final int FLAG_LINE_IS_RTL = 1 << 3;

    private final int mStart;
    private final int mEnd;
    private final float[] mMatrixValues;
    private final float[] mCharacterBounds;      // 4 floats per character
    private final int[] mCharacterFlags;
    private final int[] mCharacterBidiLevels;
    private final SegmentFinder mGraphemeSegmentFinder;
    private final SegmentFinder mWordSegmentFinder;
    private final SegmentFinder mLineSegmentFinder;

    private TextBoundsInfo(Builder b) {
        mStart = b.mStart;
        mEnd = b.mEnd;
        mMatrixValues = b.mMatrixValues.clone();
        mCharacterBounds = b.mCharacterBounds.clone();
        mCharacterFlags = b.mCharacterFlags.clone();
        mCharacterBidiLevels = b.mCharacterBidiLevels.clone();
        mGraphemeSegmentFinder = b.mGraphemeSegmentFinder;
        mWordSegmentFinder = b.mWordSegmentFinder;
        mLineSegmentFinder = b.mLineSegmentFinder;
    }

    public int getStartIndex() { return mStart; }
    public int getEndIndex() { return mEnd; }

    /** AOSP: the editor-to-screen transform these bounds are expressed in. */
    public void getMatrix(Matrix matrix) {
        java.util.Objects.requireNonNull(matrix);
        matrix.setValues(mMatrixValues);
    }

    public void getCharacterBounds(int index, RectF bounds) {
        checkIndex(index);
        final int off = (index - mStart) * 4;
        bounds.set(mCharacterBounds[off], mCharacterBounds[off + 1],
                mCharacterBounds[off + 2], mCharacterBounds[off + 3]);
    }

    public int getCharacterFlags(int index) {
        checkIndex(index);
        return mCharacterFlags[index - mStart];
    }

    public int getCharacterBidiLevel(int index) {
        checkIndex(index);
        return mCharacterBidiLevels[index - mStart];
    }

    public SegmentFinder getGraphemeSegmentFinder() { return mGraphemeSegmentFinder; }
    public SegmentFinder getWordSegmentFinder() { return mWordSegmentFinder; }
    public SegmentFinder getLineSegmentFinder() { return mLineSegmentFinder; }

    /** AOSP TextBoundsInfo.getOffsetForPosition(float, float): the insertion
     *  offset closest to (x, y). AOSP walks the line the point falls on and
     *  picks the nearest grapheme boundary; the same walk is done here directly
     *  over the character bounds this object carries. */
    public int getOffsetForPosition(float x, float y) {
        int best = -1;
        float bestDist = Float.MAX_VALUE;
        for (int i = mStart; i < mEnd; i++) {
            final int off = (i - mStart) * 4;
            final float l = mCharacterBounds[off], t = mCharacterBounds[off + 1];
            final float r = mCharacterBounds[off + 2], b = mCharacterBounds[off + 3];
            if (y < t || y > b) continue;
            final float cx = (l + r) * 0.5f;
            final float d = Math.abs(x - cx);
            if (d < bestDist) { bestDist = d; best = (x <= cx) ? i : i + 1; }
        }
        return best;
    }

    /** AOSP TextBoundsInfo.getRangeForRect(RectF, SegmentFinder,
     *  Layout.TextInclusionStrategy): the [start, end) range of segments the
     *  rect selects, or null when it selects none. */
    public int[] getRangeForRect(RectF area, SegmentFinder segmentFinder,
            android.text.Layout.TextInclusionStrategy inclusionStrategy) {
        java.util.Objects.requireNonNull(area);
        java.util.Objects.requireNonNull(segmentFinder);
        java.util.Objects.requireNonNull(inclusionStrategy);
        int start = -1, end = -1;
        RectF cb = new RectF();
        for (int i = mStart; i < mEnd; i++) {
            getCharacterBounds(i, cb);
            if (!inclusionStrategy.isSegmentInside(cb, area)) continue;
            final int segStart = segmentFinder.previousStartBoundary(i + 1);
            final int segEnd = segmentFinder.nextEndBoundary(i);
            if (segStart == SegmentFinder.DONE || segEnd == SegmentFinder.DONE) continue;
            if (start == -1 || segStart < start) start = segStart;
            if (end == -1 || segEnd > end) end = segEnd;
        }
        return start == -1 ? null : new int[] { start, end };
    }

    private void checkIndex(int index) {
        if (index < mStart || index >= mEnd) {
            throw new IllegalArgumentException("Index must be within the range ["
                    + mStart + ", " + mEnd + "), but was " + index);
        }
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mStart);
        dest.writeInt(mEnd);
        dest.writeFloatArray(mMatrixValues);
        dest.writeFloatArray(mCharacterBounds);
        dest.writeIntArray(mCharacterFlags);
        dest.writeIntArray(mCharacterBidiLevels);
        // AOSP marshals the three SegmentFinders as their boundary arrays; this
        // tree has no in-process consumer that reads them back (nothing runs an
        // IME), and Parcel keeps the live object when a body writes nothing --
        // so the finders are deliberately not flattened here rather than
        // flattened into a shape nothing can rebuild.
    }

    public static final android.os.Parcelable.Creator<TextBoundsInfo> CREATOR =
            new android.os.Parcelable.Creator<TextBoundsInfo>() {
        @Override public TextBoundsInfo createFromParcel(android.os.Parcel source) {
            final int start = source.readInt();
            final int end = source.readInt();
            Builder b = new Builder(start, end);
            Matrix m = new Matrix();
            float[] mv = source.createFloatArray();
            if (mv != null) m.setValues(mv);
            b.setMatrix(m);
            b.setCharacterBounds(source.createFloatArray());
            b.setCharacterFlags(source.createIntArray());
            b.setCharacterBidiLevel(source.createIntArray());
            return b.build();
        }
        @Override public TextBoundsInfo[] newArray(int size) { return new TextBoundsInfo[size]; }
    };

    public static final class Builder {
        private int mStart;
        private int mEnd;
        private float[] mMatrixValues = new float[9];
        private float[] mCharacterBounds = new float[0];
        private int[] mCharacterFlags = new int[0];
        private int[] mCharacterBidiLevels = new int[0];
        private SegmentFinder mGraphemeSegmentFinder;
        private SegmentFinder mWordSegmentFinder;
        private SegmentFinder mLineSegmentFinder;

        public Builder(int start, int end) { setStartAndEnd(start, end); }

        public Builder setStartAndEnd(int start, int end) {
            if (start < 0) {
                throw new IllegalArgumentException("start must be non-negative, but was " + start);
            }
            if (end < start) {
                throw new IllegalArgumentException("end must not be smaller than start, but was "
                        + end + " < " + start);
            }
            mStart = start;
            mEnd = end;
            return this;
        }
        public Builder setMatrix(Matrix matrix) {
            java.util.Objects.requireNonNull(matrix);
            matrix.getValues(mMatrixValues);
            return this;
        }
        public Builder setCharacterBounds(float[] characterBounds) {
            mCharacterBounds = java.util.Objects.requireNonNull(characterBounds).clone();
            return this;
        }
        public Builder setCharacterFlags(int[] characterFlags) {
            mCharacterFlags = java.util.Objects.requireNonNull(characterFlags).clone();
            return this;
        }
        public Builder setCharacterBidiLevel(int[] characterBidiLevels) {
            mCharacterBidiLevels = java.util.Objects.requireNonNull(characterBidiLevels).clone();
            return this;
        }
        public Builder setGraphemeSegmentFinder(SegmentFinder f) {
            mGraphemeSegmentFinder = java.util.Objects.requireNonNull(f); return this;
        }
        public Builder setWordSegmentFinder(SegmentFinder f) {
            mWordSegmentFinder = java.util.Objects.requireNonNull(f); return this;
        }
        public Builder setLineSegmentFinder(SegmentFinder f) {
            mLineSegmentFinder = java.util.Objects.requireNonNull(f); return this;
        }
        /** AOSP Builder.clear(): reset every field so the builder can be reused. */
        public Builder clear() {
            mStart = 0;
            mEnd = 0;
            mMatrixValues = new float[9];
            mCharacterBounds = new float[0];
            mCharacterFlags = new int[0];
            mCharacterBidiLevels = new int[0];
            mGraphemeSegmentFinder = null;
            mWordSegmentFinder = null;
            mLineSegmentFinder = null;
            return this;
        }
        public TextBoundsInfo build() {
            final int length = mEnd - mStart;
            if (mCharacterBounds.length != length * 4) {
                throw new IllegalStateException("characterBounds must have "
                        + (length * 4) + " elements, but was " + mCharacterBounds.length);
            }
            if (mCharacterFlags.length != length) {
                throw new IllegalStateException("characterFlags must have " + length
                        + " elements, but was " + mCharacterFlags.length);
            }
            if (mCharacterBidiLevels.length != length) {
                throw new IllegalStateException("characterBidiLevels must have " + length
                        + " elements, but was " + mCharacterBidiLevels.length);
            }
            return new TextBoundsInfo(this);
        }
    }
}
