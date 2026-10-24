// android.util.SparseIntArray -- int->int sparse map, ported from
//   frameworks/base/core/java/android/util/SparseIntArray.java (android-11.0.0_r48)
//
// This used to be a LinkedHashMap, i.e. INSERTION-ordered, with a comment
// claiming int-order semantics were not needed. They are: keyAt(0) is
// contractually the SMALLEST key (the javadoc promises ascending order), and
// callers walk keyAt/valueAt expecting a sorted merge. Insertion order also made
// removeAt(i) remove a different entry than AOSP would, and made indexOfKey
// answer -1 for every miss instead of ~insertionPoint.
//
// Unlike SparseArray, the PRIMITIVE-valued sparse classes have no DELETED
// sentinel and no gc(): delete()/removeAt() compact immediately with
// System.arraycopy (:131-136). "DELETED" appears ten times in SparseArray.java
// and zero times here, and that difference is deliberate.
//
// Out-of-range keyAt/valueAt/setValueAt throw ArrayIndexOutOfBoundsException,
// AOSP's behaviour for apps targeting Q and later; a negative index falls
// through to the raw array access and throws the same class.
package android.util;

import java.util.Arrays;

public class SparseIntArray implements Cloneable {
    private int[] mKeys;
    private int[] mValues;
    private int mSize;

    public SparseIntArray() {
        this(10);
    }

    public SparseIntArray(int initialCapacity) {
        if (initialCapacity <= 0) {
            mKeys = new int[0];
            mValues = new int[0];
        } else {
            mKeys = new int[initialCapacity];
            mValues = new int[initialCapacity];
        }
        mSize = 0;
    }

    @Override
    public SparseIntArray clone() {
        SparseIntArray clone = null;
        try {
            clone = (SparseIntArray) super.clone();
            clone.mKeys = mKeys.clone();
            clone.mValues = mValues.clone();
        } catch (CloneNotSupportedException cnse) {
            /* ignore */
        }
        return clone;
    }

    public int get(int key) {
        return get(key, 0);
    }

    public int get(int key, int valueIfKeyNotFound) {
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

    public void put(int key, int value) {
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

    public int valueAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return mValues[index];
    }

    public void setValueAt(int index, int value) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        mValues[index] = value;
    }

    public int indexOfKey(int key) {
        return ContainerHelpers.binarySearch(mKeys, mSize, key);
    }

    public int indexOfValue(int value) {
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

    public void append(int key, int value) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            put(key, value);
            return;
        }

        mKeys = ContainerHelpers.append(mKeys, mSize, key);
        mValues = ContainerHelpers.append(mValues, mSize, value);
        mSize++;
    }

    /** @hide in AOSP -- returns null (not an empty array) when the map is
     *  empty, which is the documented shape callers branch on. */
    public int[] copyKeys() {
        if (size() == 0) {
            return null;
        }
        return Arrays.copyOf(mKeys, size());
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
            int value = valueAt(i);
            buffer.append(value);
        }
        buffer.append('}');
        return buffer.toString();
    }
}
