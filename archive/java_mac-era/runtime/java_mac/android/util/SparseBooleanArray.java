// android.util.SparseBooleanArray -- int->boolean sparse map, ported from
//   frameworks/base/core/java/android/util/SparseBooleanArray.java (android-11.0.0_r48)
//
// Was a TreeMap<Integer,Boolean>. That got ascending keys right by accident but
// lost three things: indexOfKey answered -1 instead of ~insertionPoint,
// toString printed java.util.Map's "{k=v}" via Integer/Boolean boxing rather
// than the class's own builder, and equals/hashCode were Object identity --
// this is the ONLY sparse class that defines them, and AbsListView's checked-
// item state is compared with equals().
//
// The hashCode is deliberately reproduced WARTS AND ALL (:283-289):
//     hashCode = 31 * hashCode + mKeys[i] | (mValues[i] ? 1 : 0)
// Java binds + tighter than |, so the value bit is OR-ed into the running total
// rather than mixed into it. For any key whose partial sum already has bit 0
// set, the boolean cannot change the hash at all, and two arrays differing only
// in that value hash identically. That is a real, observable AOSP property; an
// implementation that "fixes" it by parenthesising diverges.
package android.util;

public class SparseBooleanArray implements Cloneable {
    private int[] mKeys;
    private boolean[] mValues;
    private int mSize;

    public SparseBooleanArray() {
        this(10);
    }

    public SparseBooleanArray(int initialCapacity) {
        if (initialCapacity <= 0) {
            mKeys = new int[0];
            mValues = new boolean[0];
        } else {
            mKeys = new int[initialCapacity];
            mValues = new boolean[initialCapacity];
        }
        mSize = 0;
    }

    @Override
    public SparseBooleanArray clone() {
        SparseBooleanArray clone = null;
        try {
            clone = (SparseBooleanArray) super.clone();
            clone.mKeys = mKeys.clone();
            clone.mValues = mValues.clone();
        } catch (CloneNotSupportedException cnse) {
            /* ignore */
        }
        return clone;
    }

    public boolean get(int key) {
        return get(key, false);
    }

    public boolean get(int key, boolean valueIfKeyNotFound) {
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

    /** @hide in AOSP. */
    public void removeAt(int index) {
        System.arraycopy(mKeys, index + 1, mKeys, index, mSize - (index + 1));
        System.arraycopy(mValues, index + 1, mValues, index, mSize - (index + 1));
        mSize--;
    }

    public void put(int key, boolean value) {
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

    public boolean valueAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return mValues[index];
    }

    /** @hide in AOSP. */
    public void setValueAt(int index, boolean value) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        mValues[index] = value;
    }

    /** @hide in AOSP. */
    public void setKeyAt(int index, int key) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        mKeys[index] = key;
    }

    public int indexOfKey(int key) {
        return ContainerHelpers.binarySearch(mKeys, mSize, key);
    }

    public int indexOfValue(boolean value) {
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

    public void append(int key, boolean value) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            put(key, value);
            return;
        }

        mKeys = ContainerHelpers.append(mKeys, mSize, key);
        mValues = ContainerHelpers.append(mValues, mSize, value);
        mSize++;
    }

    @Override
    public int hashCode() {
        int hashCode = mSize;
        for (int i = 0; i < mSize; i++) {
            // Precedence is AOSP's, not a typo: + binds tighter than |, so the
            // value bit is OR-ed into the accumulator. See the class comment.
            hashCode = 31 * hashCode + mKeys[i] | (mValues[i] ? 1 : 0);
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }

        if (!(that instanceof SparseBooleanArray)) {
            return false;
        }

        SparseBooleanArray other = (SparseBooleanArray) that;
        if (mSize != other.mSize) {
            return false;
        }

        for (int i = 0; i < mSize; i++) {
            if (mKeys[i] != other.mKeys[i]) {
                return false;
            }
            if (mValues[i] != other.mValues[i]) {
                return false;
            }
        }
        return true;
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
            boolean value = valueAt(i);
            buffer.append(value);
        }
        buffer.append('}');
        return buffer.toString();
    }
}
