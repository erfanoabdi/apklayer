// android.util.SparseArray<E> -- int-key sparse map, ported from
//   frameworks/base/core/java/android/util/SparseArray.java (android-11.0.0_r48)
//
// This used to be a TreeMap wearing SparseArray's name. That got the easy half
// of the contract right (ascending keys) and the hard half wrong, because
// SparseArray's deletion is DEFERRED, not eager, and the deferral is OBSERVABLE:
//
//   - delete()/remove()/removeAt() do NOT remove anything. They store a private
//     DELETED sentinel into mValues and set mGarbage (:143-152, :187-197).
//   - gc() (:215-241) compacts, and is called from size(), keyAt(), valueAt(),
//     setValueAt(), indexOfKey() and indexOfValue() -- but NOT from get(), which
//     reads the sentinel directly (:131-138).
//   - gc() copies mValues down and nulls the vacated VALUE slot, but leaves the
//     KEY tail intact (:223-235); keyAt() range-checks against the PRE-gc mSize
//     (:302-307). So after deleting the LAST entry, keyAt(oldSize-1) returns the
//     deleted key rather than throwing. Real code depends on the family of
//     behaviours this produces; the probe pins it as UTL.spa.gc.stale*.
//   - because indices do not shift until gc, `removeAt(0); removeAt(3)` removes
//     the ORIGINAL indices 0 and 3. An eager backing store removes 0 and then
//     whatever slid into 3, i.e. the original 4 -- a silent off-by-one for every
//     caller that batches removals by index.
//   - put() over a DELETED slot revives that slot in place (:251-260), and a NEW
//     key whose insertion point lands on a DELETED slot REUSES it without
//     growing, so mSize does not move.
//   - indexOfKey returns ~insertionPoint on a miss (ContainerHelpers:38), never
//     a flat -1; indexOfValue compares with == and not equals (:388-397).
//   - append() degrades to put() when the key is not greater than the last key
//     (:453-457), and "the last key" is read from mKeys even when that entry is
//     pending deletion.
//
// The out-of-range accessors throw ArrayIndexOutOfBoundsException, which is
// AOSP's behaviour for apps targeting Q and later (the
// UtilConfig.sThrowExceptionForUpperArrayOutOfBounds guard at :303-307). We
// always take that branch. Note the guard covers index >= mSize only; a NEGATIVE
// index falls through to the raw array access and throws the same class for a
// different reason, which is also what AOSP does.
package android.util;

public class SparseArray<E> implements Cloneable {
    private static final Object DELETED = new Object();
    private boolean mGarbage = false;

    private int[] mKeys;
    private Object[] mValues;
    private int mSize;

    public SparseArray() {
        this(10);
    }

    public SparseArray(int initialCapacity) {
        if (initialCapacity <= 0) {
            mKeys = new int[0];
            mValues = new Object[0];
        } else {
            mValues = new Object[initialCapacity];
            mKeys = new int[initialCapacity];
        }
        mSize = 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SparseArray<E> clone() {
        SparseArray<E> clone = null;
        try {
            clone = (SparseArray<E>) super.clone();
            clone.mKeys = mKeys.clone();
            clone.mValues = mValues.clone();
        } catch (CloneNotSupportedException cnse) {
            /* ignore */
        }
        return clone;
    }

    public boolean contains(int key) {
        return indexOfKey(key) >= 0;
    }

    public E get(int key) {
        return get(key, null);
    }

    @SuppressWarnings("unchecked")
    public E get(int key, E valueIfKeyNotFound) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i < 0 || mValues[i] == DELETED) {
            return valueIfKeyNotFound;
        } else {
            return (E) mValues[i];
        }
    }

    public void delete(int key) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            if (mValues[i] != DELETED) {
                mValues[i] = DELETED;
                mGarbage = true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public E removeReturnOld(int key) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            if (mValues[i] != DELETED) {
                final E old = (E) mValues[i];
                mValues[i] = DELETED;
                mGarbage = true;
                return old;
            }
        }
        return null;
    }

    /** Alias for {@link #delete(int)}. */
    public void remove(int key) {
        delete(key);
    }

    public void removeAt(int index) {
        if (index >= mSize) {
            // The array might be slightly bigger than mSize, in which case
            // indexing would not fail; check explicitly.
            throw new ArrayIndexOutOfBoundsException(index);
        }
        if (mValues[index] != DELETED) {
            mValues[index] = DELETED;
            mGarbage = true;
        }
    }

    public void removeAtRange(int index, int size) {
        final int end = Math.min(mSize, index + size);
        for (int i = index; i < end; i++) {
            removeAt(i);
        }
    }

    private void gc() {
        int n = mSize;
        int o = 0;
        int[] keys = mKeys;
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

    public void put(int key, E value) {
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

    /** AOSP Kotlin operator alias for put (API 31): `sparseArray[key] = value`
     *  desugars to a `set` call, not a `put`. */
    public void set(int key, E value) {
        put(key, value);
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

    public int keyAt(int index) {
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

    public int indexOfKey(int key) {
        if (mGarbage) {
            gc();
        }

        return ContainerHelpers.binarySearch(mKeys, mSize, key);
    }

    /** Identity comparison, not equals -- AOSP says so at :388-389 and the body
     *  confirms it at :397. */
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

    public void clear() {
        int n = mSize;
        Object[] values = mValues;

        for (int i = 0; i < n; i++) {
            values[i] = null;
        }

        mSize = 0;
        mGarbage = false;
    }

    public void append(int key, E value) {
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
            int key = keyAt(i);
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

    // ---- Mac.apk internal helpers (NOT AOSP API) ------------------------
    // Used by our own Parcel/Bundle equality paths, which need a content
    // comparison that SparseArray itself does not provide (AOSP's SparseArray
    // inherits Object.equals/hashCode deliberately).

    public int contentHashCode() {
        int h = 0;
        for (int i = 0, n = size(); i < n; i++) {
            Object v = valueAt(i);
            h = 31 * h + keyAt(i) ^ (v == null ? 0 : v.hashCode());
        }
        return h;
    }

    public boolean contentEquals(SparseArray<?> other) {
        if (other == null || other.size() != size()) return false;
        for (int i = 0, n = size(); i < n; i++) {
            if (keyAt(i) != other.keyAt(i)) return false;
            Object a = valueAt(i), b = other.valueAt(i);
            if (a == null ? b != null : !a.equals(b)) return false;
        }
        return true;
    }
}
