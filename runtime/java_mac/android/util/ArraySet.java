// android.util.ArraySet<E> -- ported from
//   frameworks/base/core/java/android/util/ArraySet.java (android-11.0.0_r48)
//
// The Set half of ArrayMap's machinery, and it was a LinkedHashSet delegate for
// the same reason ArrayMap was a LinkedHashMap: it looked equivalent through
// size/contains/add/remove, and is not. See ArrayMap.java's header for the full
// argument; the short form is:
//
//   - A sorted int[] of value HASHES plus a flat Object[] of values, so
//     iteration, valueAt(i) and toArray() are all ASCENDING BY hashCode(),
//     SIGNED. A LinkedHashSet answers a different value for every valueAt(i).
//   - indexOf walks the equal-hash run forward then backward (:113-146) and
//     returns ~end on a miss -- a negative INSERTION POINT, not -1.
//   - toString is BRACE-delimited, "{a, b, c}" (:830-853), not AbstractSet's
//     "[a, b, c]". An empty ArraySet prints "{}".
//   - hashCode is the SUM OF HASHES (:812-818), which is exactly the
//     Set.hashCode contract, and equals() accepts any Set (:785), so an
//     ArraySet and an equal HashSet agree in both directions.
//   - a null value is legal and takes the separate indexOfNull path (:148-172).
//
// Same two deliberate deviations as ArrayMap: no array-recycling free lists, and
// removeAt does not implement the shrink-and-realloc branch. Both are allocation
// policy with no observable effect on contents or order.
package android.util;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ArraySet<E> implements Collection<E>, Set<E> {
    private static final int BASE_SIZE = 4;

    private final boolean mIdentityHashCode;
    int[] mHashes;
    Object[] mArray;
    int mSize;

    private int binarySearch(int[] hashes, int hash) {
        return ContainerHelpers.binarySearch(hashes, mSize, hash);
    }

    private int indexOf(Object key, int hash) {
        final int N = mSize;

        // Important fast case: if nothing is in here, nothing to look for.
        if (N == 0) {
            return ~0;
        }

        int index = binarySearch(mHashes, hash);

        // If the hash code wasn't found, then we have no entry for this key.
        if (index < 0) {
            return index;
        }

        // If the key at the returned index matches, that's what we want.
        if (key.equals(mArray[index])) {
            return index;
        }

        // Search for a matching key after the index.
        int end;
        for (end = index + 1; end < N && mHashes[end] == hash; end++) {
            if (key.equals(mArray[end])) return end;
        }

        // Search for a matching key before the index.
        for (int i = index - 1; i >= 0 && mHashes[i] == hash; i--) {
            if (key.equals(mArray[i])) return i;
        }

        // Not found: the negative value names the END of the hash chain, which
        // is where a new entry would go.
        return ~end;
    }

    private int indexOfNull() {
        final int N = mSize;

        if (N == 0) {
            return ~0;
        }

        int index = binarySearch(mHashes, 0);

        if (index < 0) {
            return index;
        }

        if (null == mArray[index]) {
            return index;
        }

        int end;
        for (end = index + 1; end < N && mHashes[end] == 0; end++) {
            if (null == mArray[end]) return end;
        }

        for (int i = index - 1; i >= 0 && mHashes[i] == 0; i--) {
            if (null == mArray[i]) return i;
        }

        return ~end;
    }

    private void allocArrays(final int size) {
        mHashes = new int[size];
        mArray = new Object[size];
    }

    public ArraySet() {
        this(0, false);
    }

    public ArraySet(int capacity) {
        this(capacity, false);
    }

    /** @hide in AOSP. */
    public ArraySet(int capacity, boolean identityHashCode) {
        mIdentityHashCode = identityHashCode;
        if (capacity <= 0) {
            mHashes = new int[0];
            mArray = new Object[0];
        } else {
            allocArrays(capacity);
        }
        mSize = 0;
    }

    public ArraySet(ArraySet<E> set) {
        this();
        if (set != null) {
            addAll(set);
        }
    }

    public ArraySet(Collection<? extends E> set) {
        this();
        if (set != null) {
            addAll(set);
        }
    }

    public ArraySet(E[] array) {
        this();
        if (array != null) {
            for (E value : array) {
                add(value);
            }
        }
    }

    @Override
    public void clear() {
        if (mSize != 0) {
            mHashes = new int[0];
            mArray = new Object[0];
            mSize = 0;
        }
    }

    public void ensureCapacity(int minimumCapacity) {
        final int oSize = mSize;
        if (mHashes.length < minimumCapacity) {
            final int[] ohashes = mHashes;
            final Object[] oarray = mArray;
            allocArrays(minimumCapacity);
            if (mSize > 0) {
                System.arraycopy(ohashes, 0, mHashes, 0, mSize);
                System.arraycopy(oarray, 0, mArray, 0, mSize);
            }
        }
        if (mSize != oSize) {
            throw new ConcurrentModificationException();
        }
    }

    @Override
    public boolean contains(Object key) {
        return indexOf(key) >= 0;
    }

    /** Returns the index of a value, or a NEGATIVE insertion point if absent. */
    public int indexOf(Object key) {
        return key == null ? indexOfNull()
                : indexOf(key, mIdentityHashCode ? System.identityHashCode(key) : key.hashCode());
    }

    @SuppressWarnings("unchecked")
    public E valueAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return (E) mArray[index];
    }

    @Override
    public boolean isEmpty() {
        return mSize <= 0;
    }

    @Override
    public boolean add(E value) {
        final int oSize = mSize;
        final int hash;
        int index;
        if (value == null) {
            hash = 0;
            index = indexOfNull();
        } else {
            hash = mIdentityHashCode ? System.identityHashCode(value) : value.hashCode();
            index = indexOf(value, hash);
        }
        if (index >= 0) {
            return false;
        }

        index = ~index;
        if (oSize >= mHashes.length) {
            final int n = oSize >= (BASE_SIZE * 2) ? (oSize + (oSize >> 1))
                    : (oSize >= BASE_SIZE ? (BASE_SIZE * 2) : BASE_SIZE);

            final int[] ohashes = mHashes;
            final Object[] oarray = mArray;
            allocArrays(n);

            if (ohashes.length > 0) {
                System.arraycopy(ohashes, 0, mHashes, 0, ohashes.length);
                System.arraycopy(oarray, 0, mArray, 0, oarray.length);
            }
        }

        if (index < oSize) {
            System.arraycopy(mHashes, index, mHashes, index + 1, oSize - index);
            System.arraycopy(mArray, index, mArray, index + 1, oSize - index);
        }

        mHashes[index] = hash;
        mArray[index] = value;
        mSize++;
        return true;
    }

    /** @hide in AOSP: append WITHOUT validating hash order. */
    public void append(E value) {
        final int index = mSize;
        final int hash = value == null ? 0
                : (mIdentityHashCode ? System.identityHashCode(value) : value.hashCode());
        if (index >= mHashes.length) {
            throw new IllegalStateException("Array is full");
        }
        if (index > 0 && mHashes[index - 1] > hash) {
            add(value);
            return;
        }
        mSize = index + 1;
        mHashes[index] = hash;
        mArray[index] = value;
    }

    public void addAll(ArraySet<? extends E> array) {
        final int N = array.mSize;
        ensureCapacity(mSize + N);
        if (mSize == 0) {
            if (N > 0) {
                System.arraycopy(array.mHashes, 0, mHashes, 0, N);
                System.arraycopy(array.mArray, 0, mArray, 0, N);
                mSize = N;
            }
        } else {
            for (int i = 0; i < N; i++) {
                add(array.valueAt(i));
            }
        }
    }

    @Override
    public boolean remove(Object object) {
        final int index = indexOf(object);
        if (index >= 0) {
            removeAt(index);
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public E removeAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        final int oSize = mSize;
        final Object old = mArray[index];
        if (oSize <= 1) {
            clear();
        } else {
            final int nSize = oSize - 1;
            if (index < nSize) {
                System.arraycopy(mHashes, index + 1, mHashes, index, nSize - index);
                System.arraycopy(mArray, index + 1, mArray, index, nSize - index);
            }
            mArray[nSize] = null;
            mSize = nSize;
        }
        return (E) old;
    }

    public boolean removeAll(ArraySet<? extends E> array) {
        final int N = array.mSize;
        final int originalSize = mSize;
        for (int i = 0; i < N; i++) {
            remove(array.valueAt(i));
        }
        return originalSize != mSize;
    }

    /**
     * AOSP overrides Collection's default forEach so iteration is index-based
     * (no Iterator allocation) and so a concurrent structural change is
     * DETECTED rather than silently producing a short or duplicated walk.
     * AOSP: ArraySet.forEach(Consumer).
     */
    @Override
    public void forEach(Consumer<? super E> action) {
        if (action == null) {
            throw new NullPointerException();
        }
        final int size = mSize;
        for (int i = 0; i < size; ++i) {
            if (size != mSize) {
                throw new ConcurrentModificationException();
            }
            action.accept(valueAt(i));
        }
    }

    /**
     * AOSP: ArraySet.removeIf(Predicate). Deliberately NOT implemented as a
     * loop over removeAt(): compacting in one pass avoids the O(n^2) worth of
     * intermediate array shuffling that the default would do.
     *
     * Our ArraySet does not implement the shrink-and-realloc branch (see this
     * file's header), so this compacts in place and nulls the tail, which is
     * AOSP's else branch verbatim.
     */
    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        if (filter == null) {
            throw new NullPointerException();
        }
        if (mSize == 0) {
            return false;
        }

        int replaceIndex = 0;
        int numRemoved = 0;
        for (int i = 0; i < mSize; ++i) {
            @SuppressWarnings("unchecked")
            final E value = (E) mArray[i];
            if (filter.test(value)) {
                numRemoved++;
            } else {
                if (replaceIndex != i) {
                    mArray[replaceIndex] = mArray[i];
                    mHashes[replaceIndex] = mHashes[i];
                }
                replaceIndex++;
            }
        }

        if (numRemoved == 0) {
            return false;
        } else if (numRemoved == mSize) {
            clear();
            return true;
        }

        mSize -= numRemoved;
        // Null out the tail so the removed elements are not retained. AOSP
        // does this outside the loop above on purpose -- writing twice to the
        // same index would be wasted work.
        for (int i = mSize; i < mArray.length; ++i) {
            mArray[i] = null;
        }
        return true;
    }

    @Override
    public int size() {
        return mSize;
    }

    @Override
    public Object[] toArray() {
        Object[] result = new Object[mSize];
        System.arraycopy(mArray, 0, result, 0, mSize);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] array) {
        if (array.length < mSize) {
            T[] newArray = (T[]) java.lang.reflect.Array.newInstance(
                    array.getClass().getComponentType(), mSize);
            array = newArray;
        }
        System.arraycopy(mArray, 0, array, 0, mSize);
        if (array.length > mSize) {
            array[mSize] = null;
        }
        return array;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object instanceof Set) {
            Set<?> set = (Set<?>) object;
            if (size() != set.size()) {
                return false;
            }

            try {
                for (int i = 0; i < mSize; i++) {
                    E mine = valueAt(i);
                    if (!set.contains(mine)) {
                        return false;
                    }
                }
            } catch (NullPointerException ignored) {
                return false;
            } catch (ClassCastException ignored) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int[] hashes = mHashes;
        int result = 0;
        for (int i = 0, s = mSize; i < s; i++) {
            result += hashes[i];
        }
        return result;
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "{}";
        }

        StringBuilder buffer = new StringBuilder(mSize * 14);
        buffer.append('{');
        for (int i = 0; i < mSize; i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            Object value = valueAt(i);
            if (value != this) {
                buffer.append(value);
            } else {
                buffer.append("(this Set)");
            }
        }
        buffer.append('}');
        return buffer.toString();
    }

    @Override
    public Iterator<E> iterator() {
        return new ElementIterator();
    }

    /** Index-order walk, which for an ArraySet is hash order. */
    private final class ElementIterator implements Iterator<E> {
        private int mIndex = 0;
        private boolean mCanRemove = false;

        @Override public boolean hasNext() { return mIndex < mSize; }

        @Override public E next() {
            if (mIndex >= mSize) throw new NoSuchElementException();
            E r = valueAt(mIndex);
            mIndex++;
            mCanRemove = true;
            return r;
        }

        @Override public void remove() {
            if (!mCanRemove) throw new IllegalStateException();
            mIndex--;
            removeAt(mIndex);
            mCanRemove = false;
        }
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
        Iterator<?> it = collection.iterator();
        while (it.hasNext()) {
            if (!contains(it.next())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends E> collection) {
        ensureCapacity(mSize + collection.size());
        boolean added = false;
        for (E value : collection) {
            added |= add(value);
        }
        return added;
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
        boolean removed = false;
        for (Object value : collection) {
            removed |= remove(value);
        }
        return removed;
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
        boolean removed = false;
        for (int i = mSize - 1; i >= 0; i--) {
            if (!collection.contains(mArray[i])) {
                removeAt(i);
                removed = true;
            }
        }
        return removed;
    }
}
