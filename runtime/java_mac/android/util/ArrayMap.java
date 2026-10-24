// android.util.ArrayMap<K,V> -- ported from
//   frameworks/base/core/java/android/util/ArrayMap.java (android-11.0.0_r48)
//
// This was `extends LinkedHashMap`, which is wrong in a way that is visible to
// every caller that touches an index, not just to a benchmark:
//
//   ArrayMap is NOT a hash table and NOT insertion-ordered. It is a sorted
//   int[] of key HASHES plus a flat Object[] of interleaved key/value pairs,
//   and indexOfKey binary-searches the hashes (:124-135, :137-174, :432-435).
//   So INDEX ORDER IS ASCENDING BY key.hashCode(), SIGNED -- a key with a
//   negative hash sorts first -- and keyAt/valueAt/keySet/values/entrySet/
//   toString all walk that order. String.hashCode is fixed by the Java SE
//   spec, so for String keys this order is fully determined and portable, and
//   a LinkedHashMap answers a different key for every keyAt(i).
//
//   Colliding hashes are legal and common ("Aa" and "BB" are both 2112).
//   indexOf therefore cannot trust the binary-search landing spot: it compares
//   the key there, then scans FORWARD over the equal-hash run, then BACKWARD
//   (:157-172), and on a miss returns ~end -- the end of the hash chain, not
//   the landing index. A miss is a NEGATIVE number whose magnitude is the
//   insertion point, never a flat -1.
//
//   equals() accepts ANY Map (:833) rather than only another ArrayMap, and
//   hashCode is the Map-contract sum of (keyHash ^ valueHash) (:866-875), so an
//   ArrayMap and an equal HashMap compare equal in both directions and hash
//   alike. A null KEY is legal and takes the separate indexOfNull path
//   (:176-212), where the hash used is 0.
//
//   ArrayMap also declares three Collection-shaped bulk operations directly on
//   the map -- containsAll/removeAll/retainAll (:977, :998, :1008) -- which no
//   java.util.Map has. A LinkedHashMap subclass does not have them at all, so
//   every caller got NoSuchMethodError.
//
// Deliberate, documented deviations from the AOSP source, neither observable to
// an app that is correct on real Android:
//   - No array-recycling cache (AOSP's mBaseCache/mTwiceBaseCache free lists).
//     Pure allocation policy.
//   - removeAt does not implement the shrink-and-realloc branch (:765-792).
//     Also pure allocation policy; the retained entries and their order are
//     identical either way.
//   - The entry-set iterator returns a FRESH Map.Entry per next(). AOSP reuses
//     a single mutable entry for the whole iteration (documented at :1020-1024),
//     which breaks `new ArrayList<>(map.entrySet())`. Any code that is correct
//     against AOSP's reuse is also correct against fresh entries, so this can
//     only ever be more permissive, never divergent.
package android.util;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public final class ArrayMap<K, V> implements Map<K, V> {
    private static final int BASE_SIZE = 4;

    /** Whether keys are hashed by System.identityHashCode rather than
     *  hashCode(). Set by the @hide two-arg constructor. */
    private final boolean mIdentityHashCode;

    int[] mHashes;
    Object[] mArray;
    int mSize;

    private static int binarySearchHashes(int[] hashes, int N, int hash) {
        return ContainerHelpers.binarySearch(hashes, N, hash);
    }

    int indexOf(Object key, int hash) {
        final int N = mSize;

        // Important fast case: if nothing is in here, nothing to look for.
        if (N == 0) {
            return ~0;
        }

        int index = binarySearchHashes(mHashes, N, hash);

        // If the hash code wasn't found, then we have no entry for this key.
        if (index < 0) {
            return index;
        }

        // If the key at the returned index matches, that's what we want.
        if (key.equals(mArray[index << 1])) {
            return index;
        }

        // Search for a matching key after the index.
        int end;
        for (end = index + 1; end < N && mHashes[end] == hash; end++) {
            if (key.equals(mArray[end << 1])) return end;
        }

        // Search for a matching key before the index.
        for (int i = index - 1; i >= 0 && mHashes[i] == hash; i--) {
            if (key.equals(mArray[i << 1])) return i;
        }

        // Key not found -- return a negative value indicating where a new entry
        // for this key should go. The END of the hash chain is used to reduce
        // the number of array entries copied on insert.
        return ~end;
    }

    int indexOfNull() {
        final int N = mSize;

        if (N == 0) {
            return ~0;
        }

        int index = binarySearchHashes(mHashes, N, 0);

        if (index < 0) {
            return index;
        }

        if (null == mArray[index << 1]) {
            return index;
        }

        int end;
        for (end = index + 1; end < N && mHashes[end] == 0; end++) {
            if (null == mArray[end << 1]) return end;
        }

        for (int i = index - 1; i >= 0 && mHashes[i] == 0; i--) {
            if (null == mArray[i << 1]) return i;
        }

        return ~end;
    }

    private void allocArrays(final int size) {
        mHashes = new int[size];
        mArray = new Object[size << 1];
    }

    public ArrayMap() {
        mIdentityHashCode = false;
        mHashes = new int[0];
        mArray = new Object[0];
        mSize = 0;
    }

    public ArrayMap(int capacity) {
        this(capacity, false);
    }

    /** @hide in AOSP. */
    public ArrayMap(int capacity, boolean identityHashCode) {
        mIdentityHashCode = identityHashCode;
        if (capacity <= 0) {
            mHashes = new int[0];
            mArray = new Object[0];
        } else {
            allocArrays(capacity);
        }
        mSize = 0;
    }

    public ArrayMap(ArrayMap<K, V> map) {
        this();
        if (map != null) {
            putAll(map);
        }
    }

    /** Not an AOSP constructor (AOSP only accepts another ArrayMap), but a Map
     *  source is common in app code and in our own framework, and hash order
     *  makes the result independent of the source's iteration order anyway. */
    public ArrayMap(Map<? extends K, ? extends V> map) {
        this();
        if (map != null) {
            putAll(map);
        }
    }

    @Override
    public void clear() {
        if (mSize > 0) {
            mHashes = new int[0];
            mArray = new Object[0];
            mSize = 0;
        }
    }

    /** @hide in AOSP: like clear(), but keeps the backing arrays. */
    public void erase() {
        if (mSize > 0) {
            final int N = mSize << 1;
            final Object[] array = mArray;
            for (int i = 0; i < N; i++) {
                array[i] = null;
            }
            mSize = 0;
        }
    }

    public void ensureCapacity(int minimumCapacity) {
        final int osize = mSize;
        if (mHashes.length < minimumCapacity) {
            final int[] ohashes = mHashes;
            final Object[] oarray = mArray;
            allocArrays(minimumCapacity);
            if (mSize > 0) {
                System.arraycopy(ohashes, 0, mHashes, 0, osize);
                System.arraycopy(oarray, 0, mArray, 0, osize << 1);
            }
        }
        if (mSize != osize) {
            throw new ConcurrentModificationException();
        }
    }

    @Override
    public boolean containsKey(Object key) {
        return indexOfKey(key) >= 0;
    }

    public int indexOfKey(Object key) {
        return key == null ? indexOfNull()
                : indexOf(key, mIdentityHashCode ? System.identityHashCode(key) : key.hashCode());
    }

    /** Linear scan; equals()-based, unlike SparseArray.indexOfValue. */
    public int indexOfValue(Object value) {
        final int N = mSize * 2;
        final Object[] array = mArray;
        if (value == null) {
            for (int i = 1; i < N; i += 2) {
                if (array[i] == null) {
                    return i >> 1;
                }
            }
        } else {
            for (int i = 1; i < N; i += 2) {
                if (value.equals(array[i])) {
                    return i >> 1;
                }
            }
        }
        return -1;
    }

    @Override
    public boolean containsValue(Object value) {
        return indexOfValue(value) >= 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        final int index = indexOfKey(key);
        return index >= 0 ? (V) mArray[(index << 1) + 1] : null;
    }

    @SuppressWarnings("unchecked")
    public K keyAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return (K) mArray[index << 1];
    }

    @SuppressWarnings("unchecked")
    public V valueAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return (V) mArray[(index << 1) + 1];
    }

    @SuppressWarnings("unchecked")
    public V setValueAt(int index, V value) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        index = (index << 1) + 1;
        V old = (V) mArray[index];
        mArray[index] = value;
        return old;
    }

    @Override
    public boolean isEmpty() {
        return mSize <= 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        final int osize = mSize;
        final int hash;
        int index;
        if (key == null) {
            hash = 0;
            index = indexOfNull();
        } else {
            hash = mIdentityHashCode ? System.identityHashCode(key) : key.hashCode();
            index = indexOf(key, hash);
        }
        if (index >= 0) {
            index = (index << 1) + 1;
            final V old = (V) mArray[index];
            mArray[index] = value;
            return old;
        }

        index = ~index;
        if (osize >= mHashes.length) {
            final int n = osize >= (BASE_SIZE * 2) ? (osize + (osize >> 1))
                    : (osize >= BASE_SIZE ? (BASE_SIZE * 2) : BASE_SIZE);

            final int[] ohashes = mHashes;
            final Object[] oarray = mArray;
            allocArrays(n);

            if (ohashes.length > 0) {
                System.arraycopy(ohashes, 0, mHashes, 0, ohashes.length);
                System.arraycopy(oarray, 0, mArray, 0, oarray.length);
            }
        }

        if (index < osize) {
            System.arraycopy(mHashes, index, mHashes, index + 1, osize - index);
            System.arraycopy(mArray, index << 1, mArray, (index + 1) << 1, (mSize - index) << 1);
        }

        mHashes[index] = hash;
        mArray[index << 1] = key;
        mArray[(index << 1) + 1] = value;
        mSize++;
        return null;
    }

    /** @hide in AOSP: append to the end WITHOUT validating hash order. Only
     *  legal when the caller knows the key sorts last; validate() checks it. */
    public void append(K key, V value) {
        int index = mSize;
        final int hash = key == null ? 0
                : (mIdentityHashCode ? System.identityHashCode(key) : key.hashCode());
        if (index >= mHashes.length) {
            throw new IllegalStateException("Array is full");
        }
        if (index > 0 && mHashes[index - 1] > hash) {
            put(key, value);
            return;
        }
        mSize = index + 1;
        mHashes[index] = hash;
        index <<= 1;
        mArray[index] = key;
        mArray[index + 1] = value;
    }

    public void putAll(ArrayMap<? extends K, ? extends V> array) {
        final int N = array.mSize;
        ensureCapacity(mSize + N);
        if (mSize == 0) {
            if (N > 0) {
                System.arraycopy(array.mHashes, 0, mHashes, 0, N);
                System.arraycopy(array.mArray, 0, mArray, 0, N << 1);
                mSize = N;
            }
        } else {
            for (int i = 0; i < N; i++) {
                put(array.keyAt(i), array.valueAt(i));
            }
        }
    }

    @Override
    public V remove(Object key) {
        final int index = indexOfKey(key);
        if (index >= 0) {
            return removeAt(index);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public V removeAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }

        final Object old = mArray[(index << 1) + 1];
        final int osize = mSize;
        final int nsize;
        if (osize <= 1) {
            // Now empty.
            mHashes = new int[0];
            mArray = new Object[0];
            nsize = 0;
        } else {
            nsize = osize - 1;
            if (index < nsize) {
                System.arraycopy(mHashes, index + 1, mHashes, index, nsize - index);
                System.arraycopy(mArray, (index + 1) << 1, mArray, index << 1,
                        (nsize - index) << 1);
            }
            mArray[nsize << 1] = null;
            mArray[(nsize << 1) + 1] = null;
        }
        mSize = nsize;
        return (V) old;
    }

    /**
     * AOSP overrides Map's default forEach so iteration is index-based (no
     * entrySet/Iterator allocation) and so a concurrent structural change is
     * DETECTED rather than silently producing a short or duplicated walk.
     * AOSP: ArrayMap.forEach(BiConsumer).
     */
    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (action == null) {
            throw new NullPointerException();
        }
        final int size = mSize;
        for (int i = 0; i < size; ++i) {
            if (size != mSize) {
                throw new ConcurrentModificationException();
            }
            action.accept(keyAt(i), valueAt(i));
        }
    }

    /** AOSP: ArrayMap.replaceAll(BiFunction). Writes through setValueAt so the
     *  backing array is updated in place, matching AOSP. */
    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (function == null) {
            throw new NullPointerException();
        }
        final int size = mSize;
        for (int i = 0; i < size; ++i) {
            if (size != mSize) {
                throw new ConcurrentModificationException();
            }
            setValueAt(i, function.apply(keyAt(i), valueAt(i)));
        }
    }

    @Override
    public int size() {
        return mSize;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) object;
            if (size() != map.size()) {
                return false;
            }

            try {
                for (int i = 0; i < mSize; i++) {
                    K key = keyAt(i);
                    V mine = valueAt(i);
                    Object theirs = map.get(key);
                    if (mine == null) {
                        if (theirs != null || !map.containsKey(key)) {
                            return false;
                        }
                    } else if (!mine.equals(theirs)) {
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
        final Object[] array = mArray;
        int result = 0;
        for (int i = 0, v = 1, s = mSize; i < s; i++, v += 2) {
            Object value = array[v];
            result += hashes[i] ^ (value == null ? 0 : value.hashCode());
        }
        return result;
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "{}";
        }

        StringBuilder buffer = new StringBuilder(mSize * 28);
        buffer.append('{');
        for (int i = 0; i < mSize; i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            Object key = keyAt(i);
            if (key != this) {
                buffer.append(key);
            } else {
                buffer.append("(this Map)");
            }
            buffer.append('=');
            Object value = valueAt(i);
            if (value != this) {
                buffer.append(deepToString(value));
            } else {
                buffer.append("(this Map)");
            }
        }
        buffer.append('}');
        return buffer.toString();
    }

    /** AOSP toString routes values through ArrayUtils.deepToString so an array
     *  value prints its contents rather than its identity hash. */
    private static String deepToString(Object value) {
        if (value != null && value.getClass().isArray()) {
            if (value instanceof Object[])  return java.util.Arrays.deepToString((Object[]) value);
            if (value instanceof byte[])    return java.util.Arrays.toString((byte[]) value);
            if (value instanceof short[])   return java.util.Arrays.toString((short[]) value);
            if (value instanceof char[])    return java.util.Arrays.toString((char[]) value);
            if (value instanceof int[])     return java.util.Arrays.toString((int[]) value);
            if (value instanceof long[])    return java.util.Arrays.toString((long[]) value);
            if (value instanceof float[])   return java.util.Arrays.toString((float[]) value);
            if (value instanceof double[])  return java.util.Arrays.toString((double[]) value);
            if (value instanceof boolean[]) return java.util.Arrays.toString((boolean[]) value);
        }
        return String.valueOf(value);
    }

    // ------------------------------------------------------------------
    // Interop with traditional Java containers. Every view walks the array in
    // index (hash) order, which is what makes keySet()/values()/entrySet()
    // iteration order match keyAt()/valueAt().
    // ------------------------------------------------------------------

    /** Determine if the array map contains a key for every entry in the given
     *  collection (MapCollections.containsAllHelper). */
    public boolean containsAll(Collection<?> collection) {
        Iterator<?> it = collection.iterator();
        while (it.hasNext()) {
            if (!containsKey(it.next())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        ensureCapacity(mSize + map.size());
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /** Remove every key in the given collection
     *  (MapCollections.removeAllHelper); true if the size changed. */
    public boolean removeAll(Collection<?> collection) {
        int oldSize = size();
        Iterator<?> it = collection.iterator();
        while (it.hasNext()) {
            remove(it.next());
        }
        return oldSize != size();
    }

    /** Remove every key NOT in the given collection
     *  (MapCollections.retainAllHelper); true if the size changed. */
    public boolean retainAll(Collection<?> collection) {
        int oldSize = size();
        for (int i = mSize - 1; i >= 0; i--) {
            if (!collection.contains(keyAt(i))) {
                removeAt(i);
            }
        }
        return oldSize != size();
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    @Override
    public Set<K> keySet() {
        return new KeySet();
    }

    @Override
    public Collection<V> values() {
        return new ValueCollection();
    }

    /** Index-order walk shared by all three views. `offset` selects the key
     *  (0) or the value (1) half of each pair. */
    private abstract class IndexIterator<T> implements Iterator<T> {
        int mIndex = 0;
        boolean mCanRemove = false;

        @Override public boolean hasNext() { return mIndex < mSize; }

        abstract T valueOf(int index);

        @Override public T next() {
            if (mIndex >= mSize) throw new NoSuchElementException();
            T r = valueOf(mIndex);
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

    private final class KeySet extends java.util.AbstractSet<K> {
        @Override public int size() { return mSize; }
        @Override public boolean contains(Object o) { return containsKey(o); }
        @Override public boolean remove(Object o) {
            int i = indexOfKey(o);
            if (i < 0) return false;
            removeAt(i);
            return true;
        }
        @Override public void clear() { ArrayMap.this.clear(); }
        @Override public Iterator<K> iterator() {
            return new IndexIterator<K>() {
                @Override K valueOf(int index) { return keyAt(index); }
            };
        }
    }

    private final class ValueCollection extends java.util.AbstractCollection<V> {
        @Override public int size() { return mSize; }
        @Override public boolean contains(Object o) { return containsValue(o); }
        @Override public void clear() { ArrayMap.this.clear(); }
        @Override public Iterator<V> iterator() {
            return new IndexIterator<V>() {
                @Override V valueOf(int index) { return valueAt(index); }
            };
        }
    }

    private final class MapEntry implements Map.Entry<K, V> {
        private final int mEntryIndex;
        private final K mKey;
        private final V mValue;

        MapEntry(int index) {
            mEntryIndex = index;
            mKey = keyAt(index);
            mValue = valueAt(index);
        }

        @Override public K getKey() { return mKey; }
        @Override public V getValue() { return mValue; }
        @Override public V setValue(V value) { return setValueAt(mEntryIndex, value); }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Map.Entry)) return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return (mKey == null ? e.getKey() == null : mKey.equals(e.getKey()))
                && (mValue == null ? e.getValue() == null : mValue.equals(e.getValue()));
        }

        @Override public int hashCode() {
            return (mKey == null ? 0 : mKey.hashCode()) ^ (mValue == null ? 0 : mValue.hashCode());
        }

        @Override public String toString() { return mKey + "=" + mValue; }
    }

    private final class EntrySet extends java.util.AbstractSet<Map.Entry<K, V>> {
        @Override public int size() { return mSize; }
        @Override public void clear() { ArrayMap.this.clear(); }
        @Override public Iterator<Map.Entry<K, V>> iterator() {
            return new IndexIterator<Map.Entry<K, V>>() {
                @Override Map.Entry<K, V> valueOf(int index) { return new MapEntry(index); }
            };
        }
    }
}
