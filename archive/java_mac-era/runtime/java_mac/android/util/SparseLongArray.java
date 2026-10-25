// android.util.SparseLongArray -- int->long sparse map, ported from
//   frameworks/base/core/java/android/util/SparseLongArray.java (android-11.0.0_r48)
//
// Was a LinkedHashMap (insertion-ordered, -1 for every indexOfKey miss, no
// toString override so it printed the Object identity string). Same eager,
// no-DELETED-sentinel shape as SparseIntArray -- see that file's header for why
// the primitive-valued classes differ from SparseArray.
package android.util;

public class SparseLongArray implements Cloneable {
    private int[] mKeys;
    private long[] mValues;
    private int mSize;

    public SparseLongArray() {
        this(10);
    }

    public SparseLongArray(int initialCapacity) {
        if (initialCapacity <= 0) {
            mKeys = new int[0];
            mValues = new long[0];
        } else {
            mValues = new long[initialCapacity];
            mKeys = new int[initialCapacity];
        }
        mSize = 0;
    }

    @Override
    public SparseLongArray clone() {
        SparseLongArray clone = null;
        try {
            clone = (SparseLongArray) super.clone();
            clone.mKeys = mKeys.clone();
            clone.mValues = mValues.clone();
        } catch (CloneNotSupportedException cnse) {
            /* ignore */
        }
        return clone;
    }

    public long get(int key) {
        return get(key, 0);
    }

    public long get(int key, long valueIfKeyNotFound) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i < 0) {
            return valueIfKeyNotFound;
        } else {
            return mValues[i];
        }
    }

    public void delete(int key) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            removeAt(i);
        }
    }

    public void removeAt(int index) {
        System.arraycopy(mKeys, index + 1, mKeys, index, mSize - (index + 1));
        System.arraycopy(mValues, index + 1, mValues, index, mSize - (index + 1));
        mSize--;
    }

    /** @hide in AOSP. The size is clamped, not validated. */
    public void removeAtRange(int index, int size) {
        size = Math.min(size, mSize - index);
        System.arraycopy(mKeys, index + size, mKeys, index, mSize - (index + size));
        System.arraycopy(mValues, index + size, mValues, index, mSize - (index + size));
        mSize -= size;
    }

    public void put(int key, long value) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            mValues[i] = value;
        } else {
            i = ~i;

            mKeys = ContainerHelpers.insert(mKeys, mSize, i, key);
            mValues = ContainerHelpers.insert(mValues, mSize, i, value);
            mSize++;
        }
    }

    public int size() {
        return mSize;
    }

    public int keyAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return mKeys[index];
    }

    public long valueAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return mValues[index];
    }

    /** @hide in AOSP, but harmless to expose and already relied on in-tree. */
    public void setValueAt(int index, long value) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        mValues[index] = value;
    }

    public int indexOfKey(int key) {
        return ContainerHelpers.binarySearch(mKeys, mSize, key);
    }

    public int indexOfValue(long value) {
        for (int i = 0; i < mSize; i++) {
            if (mValues[i] == value) {
                return i;
            }
        }
        return -1;
    }

    public void clear() {
        mSize = 0;
    }

    public void append(int key, long value) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            put(key, value);
            return;
        }

        mKeys = ContainerHelpers.append(mKeys, mSize, key);
        mValues = ContainerHelpers.append(mValues, mSize, value);
        mSize++;
    }

    @Override
    public String toString() {
        if (size() <= 0) {
            return "{}";
        }

        StringBuilder buffer = new StringBuilder(mSize * 28);
        buffer.append('{');
        for (int i = 0; i < mSize; i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            int key = keyAt(i);
            buffer.append(key);
            buffer.append('=');
            long value = valueAt(i);
            buffer.append(value);
        }
        buffer.append('}');
        return buffer.toString();
    }
}
