// android.util.Pools -- the object-recycling pools AndroidX (RecyclerView
// ViewHolder/AttachInfo, MotionEvent, Message, Compose interop) uses to avoid
// per-frame allocation. AOSP exposes Pool<T>, SimplePool<T>, SynchronizedPool<T>.
// A missing class fails at class-load for any consumer; v1.0.426 Tier-2 adds the
// faithful surface. Source: frameworks/base/core/java/android/util/Pools.java
package android.util;

public final class Pools {
    private Pools() {}

    public interface Pool<T> {
        T acquire();
        boolean release(T instance);
    }

    public static class SimplePool<T> implements Pool<T> {
        private final Object[] mPool;
        private int mPoolSize;

        public SimplePool(int maxPoolSize) {
            if (maxPoolSize <= 0) throw new IllegalArgumentException("The max pool size must be > 0");
            mPool = new Object[maxPoolSize];
        }

        @Override @SuppressWarnings("unchecked")
        public T acquire() {
            if (mPoolSize > 0) {
                final int last = mPoolSize - 1;
                T instance = (T) mPool[last];
                mPool[last] = null;
                mPoolSize--;
                return instance;
            }
            return null;
        }

        @Override
        public boolean release(T instance) {
            if (isInPool(instance)) throw new IllegalStateException("Already in the pool!");
            if (mPoolSize < mPool.length) {
                mPool[mPoolSize] = instance;
                mPoolSize++;
                return true;
            }
            return false;
        }

        private boolean isInPool(T instance) {
            for (int i = 0; i < mPoolSize; i++) if (mPool[i] == instance) return true;
            return false;
        }
    }

    public static class SynchronizedPool<T> extends SimplePool<T> {
        private final Object mLock = new Object();
        public SynchronizedPool(int maxPoolSize) { super(maxPoolSize); }
        @Override public T acquire() { synchronized (mLock) { return super.acquire(); } }
        @Override public boolean release(T element) { synchronized (mLock) { return super.release(element); } }
    }
}
