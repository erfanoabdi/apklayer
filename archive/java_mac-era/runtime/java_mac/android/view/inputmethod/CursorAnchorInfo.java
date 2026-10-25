// android.view.inputmethod.CursorAnchorInfo -- AOSP value object carrying the
// caret / composing-text / per-character geometry an IME needs to place its
// candidate window and accent popovers next to the text being edited.
// Source: frameworks/base/core/java/android/view/inputmethod/CursorAnchorInfo.java
//
// Was a hollow stub: the class had no fields and no getters, and every Builder
// setter discarded its argument, so build() always produced an empty object.
// That silently destroyed every coordinate an editor reported, which makes it
// impossible to ever wire real caret geometry into the host text-input client
// (the whole point of the class). It also failed AndroidX / IME-aware custom
// widgets that read a CursorAnchorInfo back after building one.
//
// The state is now real and mirrors AOSP's observable behavior exactly:
// the Builder's defaults (-1 selection/composing indices, NaN insertion
// markers, identity matrix), build()'s IllegalArgumentException when
// positional parameters are given without a transformation matrix, and
// getCharacterBounds returning a fresh RectF (null for an index that was
// never added). Character bounds are held in the same ascending-key sparse
// form AOSP's (hidden) SparseRectFArray uses, implemented here as a private
// helper so no new framework type is required.
//
// EditorBoundsInfo / TextAppearanceInfo are not modeled in Mac.apk yet, so
// those two setters keep their Object parameter (as they were) and their
// getters are omitted rather than fabricating a type; see the note in
// setEditorBoundsInfo.
package android.view.inputmethod;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CursorAnchorInfo implements Parcelable {
    /** The insertion marker or character bounds have at least one visible region. */
    public static final int FLAG_HAS_VISIBLE_REGION = 0x01;
    /** The insertion marker or character bounds have at least one invisible region. */
    public static final int FLAG_HAS_INVISIBLE_REGION = 0x02;
    /** The insertion marker or character bounds is placed at right-to-left (RTL) character. */
    public static final int FLAG_IS_RTL = 0x04;

    private final int mHashCode;
    private final int mSelectionStart;
    private final int mSelectionEnd;
    private final int mComposingTextStart;
    private final CharSequence mComposingText;
    private final int mInsertionMarkerFlags;
    private final float mInsertionMarkerHorizontal;
    private final float mInsertionMarkerTop;
    private final float mInsertionMarkerBaseline;
    private final float mInsertionMarkerBottom;
    private final CharacterBounds mCharacterBoundsArray;
    private final EditorBoundsInfo mEditorBoundsInfo;
    private final float[] mMatrixValues;
    private final float[] mVisibleLineBounds;
    private final TextAppearanceInfo mTextAppearanceInfo;

    private CursorAnchorInfo(int selectionStart, int selectionEnd, int composingTextStart,
            CharSequence composingText, int insertionMarkerFlags,
            float insertionMarkerHorizontal, float insertionMarkerTop,
            float insertionMarkerBaseline, float insertionMarkerBottom,
            CharacterBounds characterBoundsArray, EditorBoundsInfo editorBoundsInfo,
            float[] matrixValues, float[] visibleLineBounds,
            TextAppearanceInfo textAppearanceInfo) {
        mSelectionStart = selectionStart;
        mSelectionEnd = selectionEnd;
        mComposingTextStart = composingTextStart;
        mComposingText = composingText;
        mInsertionMarkerFlags = insertionMarkerFlags;
        mInsertionMarkerHorizontal = insertionMarkerHorizontal;
        mInsertionMarkerTop = insertionMarkerTop;
        mInsertionMarkerBaseline = insertionMarkerBaseline;
        mInsertionMarkerBottom = insertionMarkerBottom;
        mCharacterBoundsArray = characterBoundsArray;
        mEditorBoundsInfo = editorBoundsInfo;
        mMatrixValues = matrixValues;
        mVisibleLineBounds = visibleLineBounds;
        mTextAppearanceInfo = textAppearanceInfo;

        // AOSP: "To keep hash function simple, we only use some complex objects
        // for hash." Mirrored so hashCode()/equals() agree with real Android.
        int hashCode = Objects.hashCode(mComposingText);
        hashCode *= 31;
        hashCode += Arrays.hashCode(mMatrixValues);
        mHashCode = hashCode;
    }

    /**
     * AOSP declares a public Parcel constructor. Mac.apk runs the editor and the
     * IME surface in one process, so a CursorAnchorInfo is never marshalled and
     * there is nothing to read back; the object is constructed in the same state
     * a Builder with nothing set would produce.
     */
    public CursorAnchorInfo(Parcel source) {
        this(-1, -1, -1, null, 0, Float.NaN, Float.NaN, Float.NaN, Float.NaN,
             null, null, identityMatrixValues(), new float[0], null);
    }

    private static float[] identityMatrixValues() {
        final float[] values = new float[9];
        Matrix.IDENTITY_MATRIX.getValues(values);
        return values;
    }

    /** Returns the index where the selection starts, or -1 if there is no selection. */
    public int getSelectionStart() { return mSelectionStart; }

    /** Returns the index where the selection ends, or -1 if there is no selection. */
    public int getSelectionEnd() { return mSelectionEnd; }

    /** Returns the index where the composing text starts, or -1 if there is none. */
    public int getComposingTextStart() { return mComposingTextStart; }

    /** Returns the entire composing text, or null if there is no composition. */
    public CharSequence getComposingText() { return mComposingText; }

    /** Returns the flags of the insertion marker, or 0 if no flag is specified. */
    public int getInsertionMarkerFlags() { return mInsertionMarkerFlags; }

    /** Horizontal start of the insertion marker in local coordinates, NaN if unspecified. */
    public float getInsertionMarkerHorizontal() { return mInsertionMarkerHorizontal; }

    /** Top of the insertion marker in local coordinates, NaN if unspecified. */
    public float getInsertionMarkerTop() { return mInsertionMarkerTop; }

    /** Baseline of the insertion marker in local coordinates, NaN if unspecified. */
    public float getInsertionMarkerBaseline() { return mInsertionMarkerBaseline; }

    /** Bottom of the insertion marker in local coordinates, NaN if unspecified. */
    public float getInsertionMarkerBottom() { return mInsertionMarkerBottom; }

    /**
     * A new RectF for the character at {@code index} in local coordinates, or null
     * when no bounds were reported for that index (AOSP returns null for a miss).
     */
    public RectF getCharacterBounds(final int index) {
        if (mCharacterBoundsArray == null) {
            return null;
        }
        return mCharacterBoundsArray.get(index);
    }

    /** The flags of the character bounds at {@code index}, or 0 when there are none. */
    public int getCharacterBoundsFlags(final int index) {
        if (mCharacterBoundsArray == null) {
            return 0;
        }
        return mCharacterBoundsArray.getFlags(index, 0);
    }

    /** The visible line bounds reported by the editor, empty when none were added. */
    public List<RectF> getVisibleLineBounds() {
        if (mVisibleLineBounds == null) {
            return Collections.emptyList();
        }
        final List<RectF> result = new ArrayList<RectF>(mVisibleLineBounds.length / 4);
        for (int index = 0; index < mVisibleLineBounds.length;) {
            result.add(new RectF(mVisibleLineBounds[index++], mVisibleLineBounds[index++],
                                 mVisibleLineBounds[index++], mVisibleLineBounds[index++]));
        }
        return result;
    }

    /** The matrix that transforms local coordinates into screen coordinates. */
    /** AOSP CursorAnchorInfo.getEditorBoundsInfo() (API 33): the editor's own
     *  bounds and handwriting bounds, null when the editor supplied none. The
     *  value has ALWAYS been carried by the builder here (see
     *  Builder.setEditorBoundsInfo); the getter was the only piece missing, so
     *  an IME could set it and never read it back. */
    public EditorBoundsInfo getEditorBoundsInfo() { return mEditorBoundsInfo; }

    /** AOSP CursorAnchorInfo.getTextAppearanceInfo() (API 34): the editor's text
     *  appearance, null when the editor supplied none. Same story as
     *  getEditorBoundsInfo above. */
    public TextAppearanceInfo getTextAppearanceInfo() { return mTextAppearanceInfo; }

    public Matrix getMatrix() {
        final Matrix matrix = new Matrix();
        matrix.setValues(mMatrixValues);
        return matrix;
    }

    // AOSP compares NaN as equal to NaN here (the field's "unspecified" value),
    // which Float.compare would also do but == would not.
    private static boolean areSameFloatImpl(final float a, final float b) {
        if (Float.isNaN(a) && Float.isNaN(b)) {
            return true;
        }
        return a == b;
    }

    @Override public int hashCode() { return mHashCode; }

    @Override public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CursorAnchorInfo)) {
            return false;
        }
        final CursorAnchorInfo that = (CursorAnchorInfo) obj;
        if (hashCode() != that.hashCode()) {
            return false;
        }
        if (mSelectionStart != that.mSelectionStart || mSelectionEnd != that.mSelectionEnd) {
            return false;
        }
        if (mInsertionMarkerFlags != that.mInsertionMarkerFlags
                || !areSameFloatImpl(mInsertionMarkerHorizontal, that.mInsertionMarkerHorizontal)
                || !areSameFloatImpl(mInsertionMarkerTop, that.mInsertionMarkerTop)
                || !areSameFloatImpl(mInsertionMarkerBaseline, that.mInsertionMarkerBaseline)
                || !areSameFloatImpl(mInsertionMarkerBottom, that.mInsertionMarkerBottom)) {
            return false;
        }
        if (!Objects.equals(mCharacterBoundsArray, that.mCharacterBoundsArray)) {
            return false;
        }
        if (!Objects.equals(mEditorBoundsInfo, that.mEditorBoundsInfo)) {
            return false;
        }
        if (!Arrays.equals(mVisibleLineBounds, that.mVisibleLineBounds)) {
            return false;
        }
        if (mComposingTextStart != that.mComposingTextStart
                || !Objects.equals(mComposingText, that.mComposingText)) {
            return false;
        }
        // AOSP does not use Arrays.equals here, to keep its historical behavior
        // for NaN / 0.0f / -0.0f. Mirrored element-by-element with ==.
        if (mMatrixValues.length != that.mMatrixValues.length) {
            return false;
        }
        for (int i = 0; i < mMatrixValues.length; ++i) {
            if (mMatrixValues[i] != that.mMatrixValues[i]) {
                return false;
            }
        }
        if (!Objects.equals(mTextAppearanceInfo, that.mTextAppearanceInfo)) {
            return false;
        }
        return true;
    }

    @Override public String toString() {
        return "CursorAnchorInfo{mHashCode=" + mHashCode
                + " mSelection=" + mSelectionStart + "," + mSelectionEnd
                + " mComposingTextStart=" + mComposingTextStart
                + " mComposingText=" + mComposingText
                + " mInsertionMarkerFlags=" + mInsertionMarkerFlags
                + " mInsertionMarkerHorizontal=" + mInsertionMarkerHorizontal
                + " mInsertionMarkerTop=" + mInsertionMarkerTop
                + " mInsertionMarkerBaseline=" + mInsertionMarkerBaseline
                + " mInsertionMarkerBottom=" + mInsertionMarkerBottom
                + " mCharacterBoundsArray=" + mCharacterBoundsArray
                + " mEditorBoundsInfo=" + mEditorBoundsInfo
                + " mVisibleLineBounds=" + getVisibleLineBounds()
                + " mMatrix=" + Arrays.toString(mMatrixValues)
                + " mTextAppearanceInfo=" + mTextAppearanceInfo
                + "}";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        // No IME IPC in Mac.apk; the InputConnection and the IME surface live in
        // the same process, so a CursorAnchorInfo is never marshalled. No-op.
    }

    public static final Parcelable.Creator<CursorAnchorInfo> CREATOR =
        new Parcelable.Creator<CursorAnchorInfo>() {
            @Override public CursorAnchorInfo createFromParcel(Parcel in) {
                return new CursorAnchorInfo(in);
            }
            @Override public CursorAnchorInfo[] newArray(int size) {
                return new CursorAnchorInfo[size];
            }
        };

    /**
     * Builder for {@link CursorAnchorInfo}. Not designed to be thread-safe (AOSP).
     */
    public static final class Builder {
        private static final int LINE_BOUNDS_INITIAL_SIZE = 4;
        private int mSelectionStart = -1;
        private int mSelectionEnd = -1;
        private int mComposingTextStart = -1;
        private CharSequence mComposingText = null;
        private float mInsertionMarkerHorizontal = Float.NaN;
        private float mInsertionMarkerTop = Float.NaN;
        private float mInsertionMarkerBaseline = Float.NaN;
        private float mInsertionMarkerBottom = Float.NaN;
        private int mInsertionMarkerFlags = 0;
        private CharacterBounds.Builder mCharacterBoundsArrayBuilder = null;
        private EditorBoundsInfo mEditorBoundsInfo = null;
        private float[] mMatrixValues = null;
        private boolean mMatrixInitialized = false;
        private float[] mVisibleLineBounds = new float[LINE_BOUNDS_INITIAL_SIZE * 4];
        private int mVisibleLineBoundsCount = 0;
        private TextAppearanceInfo mTextAppearanceInfo = null;

        public Builder() {}

        public Builder setSelectionRange(final int newStart, final int newEnd) {
            mSelectionStart = newStart;
            mSelectionEnd = newEnd;
            return this;
        }

        public Builder setComposingText(final int composingTextStart,
                final CharSequence composingText) {
            mComposingTextStart = composingTextStart;
            if (composingText == null) {
                mComposingText = null;
            } else {
                // AOSP snapshots the sequence so a later edit of the editor's
                // Editable cannot mutate what the IME was told.
                mComposingText = new android.text.SpannedString(composingText);
            }
            return this;
        }

        public Builder setInsertionMarkerLocation(final float horizontalPosition,
                final float lineTop, final float lineBaseline, final float lineBottom,
                final int flags) {
            mInsertionMarkerHorizontal = horizontalPosition;
            mInsertionMarkerTop = lineTop;
            mInsertionMarkerBaseline = lineBaseline;
            mInsertionMarkerBottom = lineBottom;
            mInsertionMarkerFlags = flags;
            return this;
        }

        /**
         * @throws IllegalArgumentException if the index is negative, or not greater
         * than all of the previously supplied indices (AOSP's documented contract).
         */
        public Builder addCharacterBounds(final int index, final float left, final float top,
                final float right, final float bottom, final int flags) {
            if (index < 0) {
                throw new IllegalArgumentException("index must not be a negative integer.");
            }
            if (mCharacterBoundsArrayBuilder == null) {
                mCharacterBoundsArrayBuilder = new CharacterBounds.Builder();
            }
            mCharacterBoundsArrayBuilder.append(index, left, top, right, bottom, flags);
            return this;
        }

        /** AOSP CursorAnchorInfo.Builder.setEditorBoundsInfo(EditorBoundsInfo). */
        public Builder setEditorBoundsInfo(EditorBoundsInfo bounds) {
            mEditorBoundsInfo = bounds;
            return this;
        }

        public Builder setMatrix(final Matrix matrix) {
            if (mMatrixValues == null) {
                mMatrixValues = new float[9];
            }
            (matrix != null ? matrix : Matrix.IDENTITY_MATRIX).getValues(mMatrixValues);
            mMatrixInitialized = true;
            return this;
        }

        /** AOSP CursorAnchorInfo.Builder.setTextAppearanceInfo(TextAppearanceInfo). */
        public Builder setTextAppearanceInfo(TextAppearanceInfo textAppearanceInfo) {
            mTextAppearanceInfo = textAppearanceInfo;
            return this;
        }

        public Builder addVisibleLineBounds(float left, float top, float right, float bottom) {
            if (mVisibleLineBounds.length <= mVisibleLineBoundsCount + 4) {
                mVisibleLineBounds =
                        Arrays.copyOf(mVisibleLineBounds, (mVisibleLineBoundsCount + 4) * 2);
            }
            mVisibleLineBounds[mVisibleLineBoundsCount++] = left;
            mVisibleLineBounds[mVisibleLineBoundsCount++] = top;
            mVisibleLineBounds[mVisibleLineBoundsCount++] = right;
            mVisibleLineBounds[mVisibleLineBoundsCount++] = bottom;
            return this;
        }

        public Builder clearVisibleLineBounds() {
            // mVisibleLineBounds is copied in build(), so resetting the count is
            // enough and lets the array be reused (AOSP).
            mVisibleLineBoundsCount = 0;
            return this;
        }

        /**
         * @throws IllegalArgumentException if one or more positional parameters are
         * specified but the coordinate transformation matrix is not provided via
         * {@link #setMatrix(Matrix)}. Positional data without a matrix cannot be
         * mapped to the screen, so AOSP rejects it rather than silently dropping it.
         */
        public CursorAnchorInfo build() {
            if (!mMatrixInitialized) {
                final boolean hasCharacterBounds = (mCharacterBoundsArrayBuilder != null
                        && !mCharacterBoundsArrayBuilder.isEmpty());
                final boolean hasVisibleLineBounds = (mVisibleLineBounds != null
                        && mVisibleLineBoundsCount > 0);
                if (hasCharacterBounds
                        || hasVisibleLineBounds
                        || !Float.isNaN(mInsertionMarkerHorizontal)
                        || !Float.isNaN(mInsertionMarkerTop)
                        || !Float.isNaN(mInsertionMarkerBaseline)
                        || !Float.isNaN(mInsertionMarkerBottom)) {
                    throw new IllegalArgumentException("Coordinate transformation matrix is "
                            + "required when positional parameters are specified.");
                }
            }
            return CursorAnchorInfo.create(this);
        }

        /** Resets the internal state so this instance can build another info object. */
        public void reset() {
            mSelectionStart = -1;
            mSelectionEnd = -1;
            mComposingTextStart = -1;
            mComposingText = null;
            mInsertionMarkerFlags = 0;
            mInsertionMarkerHorizontal = Float.NaN;
            mInsertionMarkerTop = Float.NaN;
            mInsertionMarkerBaseline = Float.NaN;
            mInsertionMarkerBottom = Float.NaN;
            mMatrixInitialized = false;
            if (mCharacterBoundsArrayBuilder != null) {
                mCharacterBoundsArrayBuilder.reset();
            }
            mEditorBoundsInfo = null;
            clearVisibleLineBounds();
            mTextAppearanceInfo = null;
        }
    }

    private static CursorAnchorInfo create(Builder builder) {
        final CharacterBounds characterBoundsArray =
                builder.mCharacterBoundsArrayBuilder != null
                        ? builder.mCharacterBoundsArrayBuilder.build()
                        : null;
        final float[] matrixValues = new float[9];
        if (builder.mMatrixInitialized) {
            System.arraycopy(builder.mMatrixValues, 0, matrixValues, 0, 9);
        } else {
            Matrix.IDENTITY_MATRIX.getValues(matrixValues);
        }
        return new CursorAnchorInfo(builder.mSelectionStart, builder.mSelectionEnd,
                builder.mComposingTextStart, builder.mComposingText,
                builder.mInsertionMarkerFlags, builder.mInsertionMarkerHorizontal,
                builder.mInsertionMarkerTop, builder.mInsertionMarkerBaseline,
                builder.mInsertionMarkerBottom, characterBoundsArray,
                builder.mEditorBoundsInfo, matrixValues,
                Arrays.copyOf(builder.mVisibleLineBounds, builder.mVisibleLineBoundsCount),
                builder.mTextAppearanceInfo);
    }

    /**
     * The ascending-key sparse rect store AOSP keeps in its hidden
     * android.view.inputmethod.SparseRectFArray. Kept private here because it is
     * an implementation detail of CursorAnchorInfo's observable behavior, not a
     * framework type any app can name.
     */
    private static final class CharacterBounds {
        private final int[] mKeys;
        private final float[] mCoordinates;  // 4 floats (l, t, r, b) per key
        private final int[] mFlagsArray;

        private CharacterBounds(int[] keys, float[] coordinates, int[] flagsArray) {
            mKeys = keys;
            mCoordinates = coordinates;
            mFlagsArray = flagsArray;
        }

        // Keys are strictly ascending, so a binary search is exact.
        private int indexOf(final int key) {
            return Arrays.binarySearch(mKeys, key);
        }

        RectF get(final int key) {
            final int i = indexOf(key);
            if (i < 0) {
                return null;
            }
            final int base = i * 4;
            return new RectF(mCoordinates[base], mCoordinates[base + 1],
                             mCoordinates[base + 2], mCoordinates[base + 3]);
        }

        int getFlags(final int key, final int valueIfKeyNotFound) {
            final int i = indexOf(key);
            if (i < 0) {
                return valueIfKeyNotFound;
            }
            return mFlagsArray[i];
        }

        @Override public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CharacterBounds)) {
                return false;
            }
            final CharacterBounds that = (CharacterBounds) obj;
            return Arrays.equals(mKeys, that.mKeys)
                    && Arrays.equals(mCoordinates, that.mCoordinates)
                    && Arrays.equals(mFlagsArray, that.mFlagsArray);
        }

        @Override public int hashCode() {
            return Arrays.hashCode(mKeys) * 31 + Arrays.hashCode(mCoordinates);
        }

        @Override public String toString() {
            return "CharacterBounds{keys=" + Arrays.toString(mKeys)
                    + " coordinates=" + Arrays.toString(mCoordinates)
                    + " flags=" + Arrays.toString(mFlagsArray) + "}";
        }

        static final class Builder {
            private static final int INITIAL_SIZE = 16;
            private int[] mKeys = null;
            private float[] mCoordinates = null;
            private int[] mFlagsArray = null;
            private int mCount = 0;

            /**
             * @throws IllegalArgumentException if the key is not greater than every
             * key already appended (AOSP requires ascending order).
             */
            Builder append(final int key, final float left, final float top,
                    final float right, final float bottom, final int flags) {
                if (mCount != 0 && key <= mKeys[mCount - 1]) {
                    throw new IllegalArgumentException(
                            "key must be greater than all existing keys.");
                }
                if (mKeys == null) {
                    mKeys = new int[INITIAL_SIZE];
                    mCoordinates = new float[INITIAL_SIZE * 4];
                    mFlagsArray = new int[INITIAL_SIZE];
                } else if (mCount >= mKeys.length) {
                    final int newSize = mKeys.length * 2;
                    mKeys = Arrays.copyOf(mKeys, newSize);
                    mCoordinates = Arrays.copyOf(mCoordinates, newSize * 4);
                    mFlagsArray = Arrays.copyOf(mFlagsArray, newSize);
                }
                final int base = mCount * 4;
                mKeys[mCount] = key;
                mCoordinates[base] = left;
                mCoordinates[base + 1] = top;
                mCoordinates[base + 2] = right;
                mCoordinates[base + 3] = bottom;
                mFlagsArray[mCount] = flags;
                mCount++;
                return this;
            }

            boolean isEmpty() { return mCount == 0; }

            void reset() { mCount = 0; }

            CharacterBounds build() {
                if (mCount == 0) {
                    return null;
                }
                return new CharacterBounds(Arrays.copyOf(mKeys, mCount),
                        Arrays.copyOf(mCoordinates, mCount * 4),
                        Arrays.copyOf(mFlagsArray, mCount));
            }
        }
    }
}
