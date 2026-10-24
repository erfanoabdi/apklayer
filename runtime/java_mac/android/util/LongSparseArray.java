// android.util.LongSparseArray<E> -- long-key sparse map, ported from
//   frameworks/base/core/java/android/util/LongSparseArray.java (android-11.0.0_r48)
//
// Structurally SparseArray with long keys: the SAME private DELETED sentinel and
// the SAME deferred gc (:133-145, :183-206), the same pre-gc bound check in
// keyAt (:270-281), the same ~insertionPoint miss and the same
// append-degrades-to-put rule (:418-422). See SparseArray.java's header for why
// the deferral is observable rather than an implementation detail.
//
// Was a TreeMap<Long,E>, which as well as making delete eager also risked
// narrowing: the keys this class exists for straddle the int boundary in both
// directions, so any structure that hashes or stores the key through an int
// answers correctly for small keys and collides for large ones.
package android.util;

public class LongSparseArray<E> implements Cloneable {
    private static final Object DELETED = new Object();
    private boolean mGarbage = false;

    private long[] mKeys;
    private Object[] mValues;
    private int mSize;

    public LongSparseArray() {
        this(10);
    }

    public LongSparseArray(int initialCapacity) {
        if (initialCapacity <= 0) {
            mKeys = new long[0];
            mValues = new Object[0];
        } else {
            mValues = new Object[initialCapacity];
            mKeys = new long[initialCapacity];
        }
        mSize = 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public LongSparseArray<E> clone() {
        LongSparseArray<E> clone = null;
        try {
            clone = (LongSparseArray<E>) super.clone();
            clone.mKeys = mKeys.clone();
            clone.mValues = mValues.clone();
        } catch (CloneNotSupportedException cnse) {
            /* ignore */
        }
        return clone;
    }

    public E get(long key) {
        return get(key, null);
    }

    @SuppressWarnings("unchecked")
    public E get(long key, E valueIfKeyNotFound) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i < 0 || mValues[i] == DELETED) {
            return valueIfKeyNotFound;
        } else {
            return (E) mValues[i];
        }
    }

    public void delete(long key) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            if (mValues[i] != DELETED) {
                mValues[i] = DELETED;
                mGarbage = true;
            }
        }
    }

    /** Alias for {@link #delete(long)}. */
    public void remove(long key) {
        delete(key);
    }

    public void removeAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        if (mValues[index] != DELETED) {
            mValues[index] = DELETED;
            mGarbage = true;
        }
    }

    private void gc() {
        int n = mSize;
        int o = 0;
        long[] keys = mKeys;
        Object[] values = mValues;

        for (int i = 0; i < n; i++) {
            Object val = values[i];

            if (val != DELETED) {
                if (i != o) {
                    keys[o] = keys[i];
                    values[o] = val;
                    values[i] = null;
                }

                o++;
            }
        }

        mGarbage = false;
        mSize = o;
    }

    public void put(long key, E value) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            mValues[i] = value;
        } else {
            i = ~i;

            if (i < mSize && mValues[i] == DELETED) {
                mKeys[i] = key;
                mValues[i] = value;
                return;
            }

            if (mGarbage && mSize >= mKeys.length) {
                gc();

                // Search again because indices may have changed.
                i = ~ContainerHelpers.binarySearch(mKeys, mSize, key);
            }

            mKeys = ContainerHelpers.insert(mKeys, mSize, i, key);
            mValues = ContainerHelpers.insert(mValues, mSize, i, value);
            mSize++;
        }
    }

    public int size() {
        if (mGarbage) {
            gc();
        }

        return mSize;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public long keyAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        if (mGarbage) {
            gc();
        }

        return mKeys[index];
    }

    @SuppressWarnings("unchecked")
    public E valueAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        if (mGarbage) {
            gc();
        }

        return (E) mValues[index];
    }

    public void setValueAt(int index, E value) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        if (mGarbage) {
            gc();
        }

        mValues[index] = value;
    }

    public int indexOfKey(long key) {
        if (mGarbage) {
            gc();
        }

        return ContainerHelpers.binarySearch(mKeys, mSize, key);
    }

    /** Identity comparison, not equals -- same rule as SparseArray.indexOfValue. */
    public int indexOfValue(E value) {
        if (mGarbage) {
            gc();
        }

        for (int i = 0; i < mSize; i++) {
            if (mValues[i] == value) {
                return i;
            }
        }

        return -1;
    }

    /** @hide in AOSP; equals()-based counterpart of indexOfValue. */
    public int indexOfValueByValue(E value) {
        if (mGarbage) {
            gc();
        }

        for (int i = 0; i < mSize; i++) {
            if (value == null) {
                if (mValues[i] == null) {
                    return i;
                }
            } else {
                if (value.equals(mValues[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    public boolean containsKey(long key) {
        return indexOfKey(key) >= 0;
    }

    /** Mac.apk convenience alias kept from the previous implementation; AOSP's
     *  LongSparseArray has no `contains`, but in-tree callers used it. */
    public boolean contains(long key) {
        return indexOfKey(key) >= 0;
    }

    public void clear() {
        int n = mSize;
        Object[] values = mValues;

        for (int i = 0; i < n; i++) {
            values[i] = null;
        }

        mSize = 0;
        mGarbage = false;
    }

    public void append(long key, E value) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            put(key, value);
            return;
        }

        if (mGarbage && mSize >= mKeys.length) {
            gc();
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
            long key = keyAt(i);
            buffer.append(key);
            buffer.append('=');
            Object value = valueAt(i);
            if (value != this) {
                buffer.append(value);
            } else {
                buffer.append("(this Map)");
            }
        }
        buffer.append('}');
        return buffer.toString();
    }
}
