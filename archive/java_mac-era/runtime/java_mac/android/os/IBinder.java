// android.os.IBinder -- IPC primitive. Empty for v2 (no IPC).
package android.os;

public interface IBinder {
    int FIRST_CALL_TRANSACTION = 0x00000001;
    int LAST_CALL_TRANSACTION  = 0x00ffffff;
    int FLAG_ONEWAY            = 0x00000001;
    // AOSP: soft-limit hint apps use to decide whether to chunk a large
    // payload before an IPC call rather than risk TransactionTooLargeException.
    // Verified verbatim (frameworks_base main, core/java/android/os/IBinder.java):
    // `public static final int MAX_IPC_SIZE = 64 * 1024;` (@hide) and
    // `static int getSuggestedMaxIpcSizeBytes() { return MAX_IPC_SIZE; }`.
    // Interface members are implicitly public, matching this file's existing
    // unmarked-public style.
    int MAX_IPC_SIZE = 64 * 1024;
    static int getSuggestedMaxIpcSizeBytes() { return MAX_IPC_SIZE; }

    // AOSP core/java/android/os/IBinder.java:108-160 -- the reserved
    // transaction codes, verbatim (each is four ASCII bytes packed
    // big-endian). Compile-time constants, so an app inlines them; present
    // here for reflective readers and for in-tree callers.
    int PING_TRANSACTION          = ('_'<<24)|('P'<<16)|('N'<<8)|'G';
    int DUMP_TRANSACTION          = ('_'<<24)|('D'<<16)|('M'<<8)|'P';
    int SHELL_COMMAND_TRANSACTION = ('_'<<24)|('C'<<16)|('M'<<8)|'D';
    int INTERFACE_TRANSACTION     = ('_'<<24)|('N'<<16)|('T'<<8)|'F';
    int TWEET_TRANSACTION         = ('_'<<24)|('T'<<16)|('W'<<8)|'T';
    int LIKE_TRANSACTION          = ('_'<<24)|('L'<<16)|('I'<<8)|'K';
    int SYSPROPS_TRANSACTION      = ('_'<<24)|('S'<<16)|('P'<<8)|'R';

    /** AOSP :390 -- delivered when the process HOSTING a remote binder is
     *  frozen/unfrozen by the kernel freezer. */
    interface FrozenStateChangeCallback {
        int STATE_FROZEN = 0;
        int STATE_UNFROZEN = 1;
        void onFrozenStateChanged(IBinder who, int state);
    }

    /** AOSP :458 / :481 -- both are `default` methods that
     *  `throw new UnsupportedOperationException()`, documented "if the kernel
     *  binder driver does not support this feature". Mac.apk has no binder
     *  driver at all, so that is exactly our case; keep the throw rather than
     *  silently accepting a registration whose callback would never fire. */
    default void addFrozenStateChangeCallback(java.util.concurrent.Executor executor,
                                              FrozenStateChangeCallback callback)
            throws RemoteException {
        throw new UnsupportedOperationException();
    }
    /** AOSP :471 (@hide overload) -- binder-thread variant. */
    default void addFrozenStateChangeCallback(FrozenStateChangeCallback callback)
            throws RemoteException {
        addFrozenStateChangeCallback(Runnable::run, callback);
    }
    default boolean removeFrozenStateChangeCallback(FrozenStateChangeCallback callback) {
        throw new UnsupportedOperationException();
    }

    String getInterfaceDescriptor() throws RemoteException;
    boolean pingBinder();
    boolean isBinderAlive();
    IInterface queryLocalInterface(String descriptor);
    void dump(java.io.FileDescriptor fd, String[] args) throws RemoteException;
    void dumpAsync(java.io.FileDescriptor fd, String[] args) throws RemoteException;
    boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException;
    void linkToDeath(DeathRecipient recipient, int flags) throws RemoteException;
    boolean unlinkToDeath(DeathRecipient recipient, int flags);

    interface DeathRecipient {
        void binderDied();
        /** AOSP :339 -- `default void binderDied(IBinder who) { binderDied(); }` */
        default void binderDied(IBinder who) { binderDied(); }
    }
}
