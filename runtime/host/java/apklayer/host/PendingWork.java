package apklayer.host;

import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import java.lang.reflect.Field;
import org.robolectric.shadows.ShadowLooper;

/**
 * Handler work of this process that falls due before a time: `apklayer stop` lets it run before a component host exits, because providers persist
 * their state from a handler a little after the change (the settings provider writes its files 0.2-2 s later). The queues are read under their
 * lock, as Robolectric's own ShadowPausedMessageQueue reads them, so no looper is held up.
 */
final class PendingWork {
    private PendingWork() {}

    private static final Field MESSAGES = field(MessageQueue.class, "mMessages"), NEXT = field(Message.class, "next");

    private static Field field(Class<?> owner, String name) {
        try { Field f = owner.getDeclaredField(name); f.setAccessible(true); return f; }
        catch (NoSuchFieldException other) { return null; }  // another queue implementation: nothing is waited for
    }

    /** Whether any looper has a message for a handler due at or before {@code uptimeMillis} (SystemClock's time base). */
    static boolean dueBefore(long uptimeMillis) {
        if (MESSAGES == null || NEXT == null) return false;
        try {
            for (Looper looper : ShadowLooper.getAllLoopers()) {
                MessageQueue queue = looper.getQueue();
                synchronized (queue) {
                    for (Message m = (Message) MESSAGES.get(queue); m != null; m = (Message) NEXT.get(m))
                        if (m.getTarget() != null && m.getWhen() <= uptimeMillis) return true;
                }
            }
        } catch (IllegalAccessException unreadable) { return false; }
        return false;
    }

    /**
     * Waits until every other looper has finished what it is running now (a message taken off its queue is no longer seen by {@link #dueBefore}, but
     * the handler may still be writing its file), at most until {@code deadlineNanos} (System.nanoTime).
     */
    static void awaitOtherLoopers(long deadlineNanos) {
        for (Looper looper : ShadowLooper.getAllLoopers()) {
            if (looper == Looper.getMainLooper() || !looper.getThread().isAlive()) continue;
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            if (!new android.os.Handler(looper).post(done::countDown)) continue;  // the looper is quitting
            try { done.await(Math.max(0, deadlineNanos - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
        }
    }
}
