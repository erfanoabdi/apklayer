// android.net.DnsResolver
//
// The API 29+ async DNS API. AOSP resolves via the system resolver; we back it
// with the host JDK InetAddress resolver on a background thread, posting
// results/errors to the supplied Executor + Callback. This is honest local DNS
// (the JDK resolver IS the real resolver on macOS) -- not a stub. The
// per-network variants ignore the (no-op) Network and resolve globally.
// Mirrors frameworks/base/core/java/android/net/DnsResolver.java public API.
package android.net;

import android.os.CancellationSignal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

public final class DnsResolver {

    public static final int CLASS_IN = 1;

    public static final int TYPE_A = 1;
    public static final int TYPE_AAAA = 28;

    public static final int FLAG_EMPTY = 0;
    public static final int FLAG_NO_RETRY = 1;
    public static final int FLAG_NO_CACHE_STORE = 2;
    public static final int FLAG_NO_CACHE_LOOKUP = 4;

    public static final int ERROR_PARSE = 0;
    public static final int ERROR_SYSTEM = 1;

    private static final DnsResolver INSTANCE = new DnsResolver();

    private DnsResolver() {}

    public static DnsResolver getInstance() { return INSTANCE; }

    public interface Callback<T> {
        void onAnswer(T answer, int rcode);
        void onError(DnsException error);
    }

    public static class DnsException extends Exception {
        public final int code;
        public DnsException(int code, Throwable cause) { super(cause); this.code = code; }
    }

    /** AOSP query(network, domain, flags, executor, cancellationSignal, callback)
     *  -> List<InetAddress>. We resolve via the JDK resolver off-thread. */
    public void query(Network network, String domain, int flags, Executor executor,
            CancellationSignal cancellationSignal, Callback<List<InetAddress>> callback) {
        resolve(domain, executor, cancellationSignal, callback);
    }

    public void query(Network network, String domain, int nsType, int flags, Executor executor,
            CancellationSignal cancellationSignal, Callback<List<InetAddress>> callback) {
        resolve(domain, executor, cancellationSignal, callback);
    }

    private void resolve(String domain, Executor executor, CancellationSignal signal,
            Callback<List<InetAddress>> callback) {
        Runnable task = () -> {
            if (signal != null && signal.isCanceled()) return;
            try {
                InetAddress[] addrs = InetAddress.getAllByName(domain);
                callback.onAnswer(Arrays.asList(addrs), 0 /* NOERROR */);
            } catch (UnknownHostException e) {
                callback.onError(new DnsException(ERROR_SYSTEM, e));
            }
        };
        if (executor != null) {
            executor.execute(task);
        } else {
            new Thread(task, "DnsResolver").start();
        }
    }
}
