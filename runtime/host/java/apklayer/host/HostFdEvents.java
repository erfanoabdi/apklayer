package apklayer.host;

import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.system.Os;
import android.system.StructPollfd;
import java.io.FileDescriptor;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * A MessageQueue's file descriptor listeners (MessageQueue.addOnFileDescriptorEventListener): Android's native Looper watches the descriptors, Robolectric's queue has
 * no native side. One thread per watched descriptor waits in poll(2) and hands what it saw to the queue's own thread (MessageQueue.dispatchEvents), whose answer
 * is the next set of events, as in the native Looper. ParcelFileDescriptor's close listeners run this way (MediaProvider scans a file when its writer closes it).
 */
final class HostFdEvents {
    private HostFdEvents() {}

    private static final int INPUT = 1, OUTPUT = 2, ERROR = 4;  // OnFileDescriptorEventListener.EVENT_*
    private static final short POLLIN = 1, POLLOUT = 4, POLLERR = 8, POLLHUP = 16, POLLNVAL = 32;  // Linux poll(2)
    private record Key(MessageQueue queue, int fd) {}
    private static final Map<Key, Watch> WATCHES = new ConcurrentHashMap<>();

    /** MessageQueue.nativeSetFileDescriptorEvents: watch {@code fd} for {@code events}, 0 ends the watch. */
    static void set(MessageQueue queue, int fd, int events) {
        Key key = new Key(queue, fd);        Watch watch = events == 0 ? WATCHES.remove(key) : WATCHES.computeIfAbsent(key, Watch::new);
        if (watch == null) return;
        watch.events = events;
        watch.wake();
    }

    private static final class Watch implements Runnable {
        final Key key;
        volatile int events;
        final FileDescriptor[] wakePipe;

        Watch(Key key) {
            this.key = key;
            try { wakePipe = Os.pipe2(android.system.OsConstants.O_CLOEXEC); } catch (android.system.ErrnoException failure) { throw new IllegalStateException(failure); }
            Thread thread = new Thread(this, "fd-events-" + key.fd);
            thread.setDaemon(true);
            thread.start();
        }

        void wake() {
            try { new java.io.FileOutputStream(wakePipe[1]).write(1); } catch (java.io.IOException closed) { /* the watch has ended */ }
        }

        @Override public void run() {
            try {
                while (WATCHES.get(key) == this) {
                    int wanted = events;
                    StructPollfd target = new StructPollfd(), woken = new StructPollfd();
                    target.fd = descriptor(key.fd);
                    target.events = (short) (((wanted & INPUT) != 0 ? POLLIN : 0) | ((wanted & OUTPUT) != 0 ? POLLOUT : 0));
                    woken.fd = wakePipe[0];
                    woken.events = POLLIN;
                    Os.poll(new StructPollfd[] {target, woken}, -1);                    if (woken.revents != 0) { new java.io.FileInputStream(wakePipe[0]).read(new byte[64]); continue; }
                    int seen = ((target.revents & POLLIN) != 0 ? INPUT : 0) | ((target.revents & POLLOUT) != 0 ? OUTPUT : 0)
                            | ((target.revents & (POLLERR | POLLHUP | POLLNVAL)) != 0 ? ERROR : 0);
                    if (seen == 0) continue;
                    Looper looper = looperOf(key.queue);
                    if (looper == null) break;
                    CompletableFuture<Integer> next = new CompletableFuture<>();
                    new Handler(looper).post(() -> {
                        try { next.complete(ReflectionHelpers.callInstanceMethod(key.queue, "dispatchEvents", ClassParameter.from(int.class, key.fd), ClassParameter.from(int.class, seen))); }
                        catch (Throwable failure) { next.complete(0); throw failure; }
                    });
                    int answer = next.join();
                    if (answer == 0) WATCHES.remove(key, this);
                    else if (WATCHES.get(key) == this) events = answer;
                }
            } catch (android.system.ErrnoException | java.io.IOException failure) {
                System.out.println("[apklayer] fd events: watch of " + key.fd + " ended: " + failure);
                WATCHES.remove(key, this);
            } finally {
                for (FileDescriptor end : wakePipe) try { Os.close(end); } catch (android.system.ErrnoException ignored) { /* already closed */ }
            }
        }
    }

    private static FileDescriptor descriptor(int fd) {
        FileDescriptor descriptor = new FileDescriptor();
        ReflectionHelpers.setField(descriptor, "fd", fd);
        return descriptor;
    }

    private static Looper looperOf(MessageQueue queue) {
        for (Looper looper : org.robolectric.shadows.ShadowLooper.getAllLoopers()) if (looper.getQueue() == queue) return looper;
        return null;
    }
}
