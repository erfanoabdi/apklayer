// android.os.RemoteCallbackList -- maintains a set of registered IInterface
// callbacks keyed by their underlying IBinder, with a begin/get/finish broadcast
// protocol and dead-callback pruning via linkToDeath. Single-process under
// Mac.apk, so callbacks never die remotely, but the full API is implemented so
// Services that register/broadcast to clients behave correctly in-process.
// Previously a bare-ctor stub.
//
// AOSP ref: frameworks/base/core/java/android/os/RemoteCallbackList.java
package android.os;

// AOSP fidelity: the unchecked usage below MIRRORS AOSP, which deprecates its
// own APIs while continuing to implement them. Removing it would diverge
// from the surface we exist to reproduce, so it is suppressed here rather
// than globally -- every other lint category stays live for this file.
@SuppressWarnings("unchecked")
public class RemoteCallbackList<E extends IInterface> {
    // Keyed by the callback's IBinder so a re-register of the same remote object
    // replaces rather than duplicates, matching AOSP.
    private final java.util.HashMap<IBinder, Callback> mCallbacks = new java.util.HashMap<>();
    private Object[] mActiveBroadcast;
    private int mBroadcastCount = -1;
    private boolean mKilled = false;

    private final class Callback implements IBinder.DeathRecipient {
        final E mCallback;
        final Object mCookie;

        Callback(E callback, Object cookie) {
            mCallback = callback;
            mCookie = cookie;
        }

        @Override public void binderDied() {
            synchronized (mCallbacks) {
                mCallbacks.remove(mCallback.asBinder());
            }
            onCallbackDied(mCallback, mCookie);
        }
    }

    public RemoteCallbackList() {}

    public boolean register(E callback) {
        return register(callback, null);
    }

    public boolean register(E callback, Object cookie) {
        synchronized (mCallbacks) {
            if (mKilled || callback == null) {
                return false;
            }
            IBinder binder = callback.asBinder();
            try {
                Callback cb = new Callback(callback, cookie);
                if (binder != null) {
                    try { binder.linkToDeath(cb, 0); } catch (Throwable ignored) {}
                    mCallbacks.put(binder, cb);
                } else {
                    // No binder (pure in-process callback): key by identity.
                    mCallbacks.put(new IdentityBinder(callback), cb);
                }
                return true;
            } catch (Throwable e) {
                return false;
            }
        }
    }

    public boolean unregister(E callback) {
        synchronized (mCallbacks) {
            if (callback == null) return false;
            IBinder binder = callback.asBinder();
            Callback cb = (binder != null) ? mCallbacks.remove(binder)
                                           : removeByIdentity(callback);
            if (cb != null && binder != null) {
                try { binder.unlinkToDeath(cb, 0); } catch (Throwable ignored) {}
            }
            return cb != null;
        }
    }

    public void kill() {
        synchronized (mCallbacks) {
            for (Callback cb : mCallbacks.values()) {
                IBinder b = cb.mCallback.asBinder();
                if (b != null) {
                    try { b.unlinkToDeath(cb, 0); } catch (Throwable ignored) {}
                }
            }
            mCallbacks.clear();
            mKilled = true;
        }
    }

    public void onCallbackDied(E callback) {}
    public void onCallbackDied(E callback, Object cookie) {
        onCallbackDied(callback);
    }

    public int beginBroadcast() {
        synchronized (mCallbacks) {
            if (mBroadcastCount > 0) {
                throw new IllegalStateException(
                    "beginBroadcast() called while already in a broadcast");
            }
            final int N = mBroadcastCount = mCallbacks.size();
            if (N <= 0) {
                return 0;
            }
            Object[] active = mActiveBroadcast;
            if (active == null || active.length < N) {
                mActiveBroadcast = active = new Object[N];
            }
            int i = 0;
            for (Callback cb : mCallbacks.values()) {
                active[i++] = cb;
            }
            return i;
        }
    }

    @SuppressWarnings("unchecked")
    public E getBroadcastItem(int index) {
        return ((Callback) mActiveBroadcast[index]).mCallback;
    }

    public Object getBroadcastCookie(int index) {
        return ((Callback) mActiveBroadcast[index]).mCookie;
    }

    public void finishBroadcast() {
        synchronized (mCallbacks) {
            if (mBroadcastCount < 0) {
                throw new IllegalStateException(
                    "finishBroadcast() called outside of a broadcast");
            }
            Object[] active = mActiveBroadcast;
            if (active != null) {
                final int N = mBroadcastCount;
                for (int i = 0; i < N; i++) {
                    active[i] = null;
                }
            }
            mBroadcastCount = -1;
        }
    }

    // AOSP core/java/android/os/RemoteCallbackList.java:671 declares this
    // `public void broadcast(Consumer<E>)`. It used to return the Consumer
    // here, which is not a source-compatible difference but a LINK-level one:
    // an app compiled against android.jar emits
    // invokevirtual broadcast:(Ljava/util/function/Consumer;)V and would have
    // hit NoSuchMethodError against a method whose descriptor returns
    // Consumer. Nothing in-tree consumed the return value.
    public void broadcast(java.util.function.Consumer<E> action) {
        int itemCount = beginBroadcast();
        try {
            for (int i = 0; i < itemCount; i++) {
                action.accept(getBroadcastItem(i));
            }
        } finally {
            finishBroadcast();
        }
    }

    // ---- Frozen-callee policy (AOSP :239-:373, API 36). The policy governs
    // what happens to a callback whose RECIPIENT PROCESS is frozen by the
    // kernel freezer: invoke anyway (UNSET), queue, keep only the newest, or
    // drop. Mac.apk is one process and nothing can be frozen, so no code path
    // here ever consults the policy -- but the value an app SET must be the
    // value it reads back, which is what these accessors and the Builder
    // guarantee. Answering a constant would break a caller that configures a
    // list and asserts on its own configuration.
    public static final int FROZEN_CALLEE_POLICY_UNSET = 0;
    public static final int FROZEN_CALLEE_POLICY_ENQUEUE_ALL = 1;
    public static final int FROZEN_CALLEE_POLICY_ENQUEUE_MOST_RECENT = 2;
    public static final int FROZEN_CALLEE_POLICY_DROP = 3;
    // AOSP :69
    private static final int DEFAULT_MAX_QUEUE_SIZE = 1000;

    private int mFrozenCalleePolicy = FROZEN_CALLEE_POLICY_UNSET;
    private int mMaxQueueSize = DEFAULT_MAX_QUEUE_SIZE;
    private java.util.concurrent.Executor mExecutor;

    /** AOSP :351 */
    public int getFrozenCalleePolicy() { return mFrozenCalleePolicy; }
    /** AOSP :361 */
    public int getMaxQueueSize() { return mMaxQueueSize; }
    /** AOSP :371 -- @Nullable; null unless a Builder supplied one. */
    public java.util.concurrent.Executor getExecutor() { return mExecutor; }

    /** AOSP :239. */
    public static final class Builder<E extends IInterface> {
        public interface InterfaceDiedCallback<E extends IInterface> {
            void onInterfaceDied(RemoteCallbackList<E> list, E deadInterface, Object cookie);
        }

        private int mFrozenCalleePolicy;
        private int mMaxQueueSize = DEFAULT_MAX_QUEUE_SIZE;
        private InterfaceDiedCallback<E> mInterfaceDiedCallback;
        private java.util.concurrent.Executor mExecutor;

        public Builder(int frozenCalleePolicy) { mFrozenCalleePolicy = frozenCalleePolicy; }

        /** AOSP :270 -- verbatim, including BOTH guards. They are the only
         *  observable behaviour this builder has, so they are ported rather
         *  than relaxed. */
        public Builder<E> setMaxQueueSize(int maxQueueSize) {
            if (maxQueueSize <= 0) {
                throw new IllegalArgumentException("maxQueueSize must be positive");
            }
            if (mFrozenCalleePolicy != FROZEN_CALLEE_POLICY_ENQUEUE_ALL) {
                throw new UnsupportedOperationException(
                        "setMaxQueueSize can only be called for FROZEN_CALLEE_POLICY_ENQUEUE_ALL");
            }
            mMaxQueueSize = maxQueueSize;
            return this;
        }

        public Builder<E> setInterfaceDiedCallback(InterfaceDiedCallback<E> callback) {
            mInterfaceDiedCallback = callback;
            return this;
        }

        public Builder<E> setExecutor(java.util.concurrent.Executor executor) {
            mExecutor = executor;
            return this;
        }

        /** AOSP :326 -- when a non-UNSET policy is chosen without an executor,
         *  AOSP substitutes a main-Handler executor rather than throwing (its
         *  own TODO says the throw comes later). Mirrored. */
        public RemoteCallbackList<E> build() {
            java.util.concurrent.Executor executor = mExecutor;
            if (executor == null && mFrozenCalleePolicy != FROZEN_CALLEE_POLICY_UNSET) {
                final Handler main = new Handler(Looper.getMainLooper());
                executor = new java.util.concurrent.Executor() {
                    @Override public void execute(Runnable command) { main.post(command); }
                };
            }
            final InterfaceDiedCallback<E> died = mInterfaceDiedCallback;
            RemoteCallbackList<E> list;
            if (died != null) {
                list = new RemoteCallbackList<E>() {
                    @Override public void onCallbackDied(E deadInterface, Object cookie) {
                        died.onInterfaceDied(this, deadInterface, cookie);
                    }
                };
            } else {
                list = new RemoteCallbackList<E>();
            }
            list.mFrozenCalleePolicy = mFrozenCalleePolicy;
            list.mMaxQueueSize = mMaxQueueSize;
            list.mExecutor = executor;
            return list;
        }
    }

    public int getRegisteredCallbackCount() {
        synchronized (mCallbacks) {
            if (mKilled) return 0;
            return mCallbacks.size();
        }
    }

    @SuppressWarnings("unchecked")
    public E getRegisteredCallbackItem(int index) {
        synchronized (mCallbacks) {
            if (mKilled) return null;
            int i = 0;
            for (Callback cb : mCallbacks.values()) {
                if (i++ == index) return cb.mCallback;
            }
            return null;
        }
    }

    public Object getRegisteredCallbackCookie(int index) {
        synchronized (mCallbacks) {
            if (mKilled) return null;
            int i = 0;
            for (Callback cb : mCallbacks.values()) {
                if (i++ == index) return cb.mCookie;
            }
            return null;
        }
    }

    // ---- identity-keyed fallback for callbacks whose asBinder() is null ----
    private Callback removeByIdentity(E callback) {
        java.util.Iterator<java.util.Map.Entry<IBinder, Callback>> it = mCallbacks.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<IBinder, Callback> e = it.next();
            if (e.getValue().mCallback == callback) {
                it.remove();
                return e.getValue();
            }
        }
        return null;
    }

    // A trivial IBinder identity key for pure in-process callbacks (no real
    // binder). Equality is identity of the wrapped callback object.
    private static final class IdentityBinder extends Binder {
        private final Object ref;
        IdentityBinder(Object ref) { this.ref = ref; }
        @Override public boolean equals(Object o) {
            return (o instanceof IdentityBinder) && ((IdentityBinder) o).ref == ref;
        }
        @Override public int hashCode() { return System.identityHashCode(ref); }
    }
}
