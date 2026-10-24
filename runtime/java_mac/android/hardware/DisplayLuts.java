// android.hardware.DisplayLuts -- the colour lookup table(s) an app attaches to
// a SurfaceControl so the compositor applies them to that layer.
//
// Mirrors frameworks/base/core/java/android/hardware/DisplayLuts.java
// (android16-release).
//
// Mac.apk's compositor has no LUT stage, so a DisplayLuts an app builds is
// never applied -- OverlayProperties.getLutProperties() reports an empty array,
// which is the device saying "I apply no LUTs", and an app that checks it
// first will not attach one.
//
// The VALIDATION is implemented in full and is the reason this is not a bare
// stub. Every check below fires on any device, before the compositor is
// involved, and they encode real invariants an app gets wrong: a 3D LUT buffer
// must have three channels and a whole-number cube root, and set(first, second)
// demands 1D-then-3D IN THAT ORDER. Silently accepting a malformed LUT here
// would turn a clear IllegalArgumentException into a mystery on a device.
package android.hardware;

import java.util.ArrayList;
import java.util.List;

public final class DisplayLuts {

    private final List<Entry> mEntries = new ArrayList<>();
    private final List<Integer> mOffsets = new ArrayList<>();
    private int mTotalLength;

    public DisplayLuts() { }

    public static class Entry {
        private static final int LUT_LENGTH_LIMIT = 100000;

        private final float[] mBuffer;
        private final int mDimension;
        private final int mSamplingKey;
        private final int mSize;

        public Entry(float[] buffer, int dimension, int samplingKey) {
            if (buffer == null || buffer.length < 1) {
                throw new IllegalArgumentException("The buffer cannot be empty!");
            }
            if (buffer.length >= LUT_LENGTH_LIMIT) {
                throw new IllegalArgumentException("The lut length is too big to handle!");
            }
            if (dimension != LutProperties.ONE_DIMENSION
                    && dimension != LutProperties.THREE_DIMENSION) {
                throw new IllegalArgumentException("The dimension should be either 1D or 3D!");
            }

            if (dimension == LutProperties.THREE_DIMENSION) {
                if (buffer.length <= 3) {
                    throw new IllegalArgumentException(
                            "The 3d lut size of each dimension should be over 1!");
                }
                int lengthPerChannel = buffer.length;
                if (lengthPerChannel % 3 != 0) {
                    throw new IllegalArgumentException(
                            "The lut buffer of 3dlut should have 3 channels!");
                }
                lengthPerChannel /= 3;
                double size = Math.cbrt(lengthPerChannel);
                if (size == (int) size) {
                    mSize = (int) size;
                } else {
                    throw new IllegalArgumentException(
                            "Cannot get the cube root of the 3d lut buffer!");
                }
            } else {
                mSize = buffer.length;
            }

            mBuffer = buffer;
            mDimension = dimension;
            mSamplingKey = samplingKey;
        }

        public float[] getBuffer() { return mBuffer; }
        public int getDimension() { return mDimension; }
        public int getSamplingKey() { return mSamplingKey; }
        /* package */ int getSize() { return mSize; }
    }

    private void addEntry(Entry entry) {
        mEntries.add(entry);
        mOffsets.add(mTotalLength);
        mTotalLength += entry.getBuffer().length;
    }

    private void clear() {
        mOffsets.clear();
        mTotalLength = 0;
        mEntries.clear();
    }

    /** AOSP throws IllegalArgumentException, NOT NullPointerException, for a
     *  null entry -- despite the parameter being @NonNull. Mirrored. */
    public void set(Entry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("The entry is null!");
        }
        clear();
        addEntry(entry);
    }

    /** The 1D-then-3D ordering is mandatory: the compositor applies them in
     *  sequence and a 3D LUT fed into a 1D stage is meaningless. */
    public void set(Entry first, Entry second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("The entry is null!");
        }
        if (first.getDimension() != LutProperties.ONE_DIMENSION
                || second.getDimension() != LutProperties.THREE_DIMENSION) {
            throw new IllegalArgumentException("The entries should be 1D and 3D in order!");
        }
        clear();
        addEntry(first);
        addEntry(second);
    }
}
