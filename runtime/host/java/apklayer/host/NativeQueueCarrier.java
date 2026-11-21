package apklayer.host;

import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import org.robolectric.shadows.ShadowLooper;

/**
 * Framework JNI that reads a MessageQueue's mPtr as a NativeMessageQueue (android_os_MessageQueue_getMessageQueue: SystemSensorManager's event
 * queue, NativeActivity, InputQueue) finds no native queue in Robolectric's. It gets a real one, made on the queue's own thread (whose native
 * Looper it uses, which that thread polls: ShadowHostMessageQueue, AppHost.pollNativeLooper), in a MessageQueue object that only carries the
 * pointer. The JNI keeps its own reference; nativeInit's is dropped after the call.
 */
final class NativeQueueCarrier {
    private NativeQueueCarrier() {}

    interface Call<T> { T with(MessageQueue carrier) throws Exception; }

    static <T> T call(MessageQueue queue, Call<T> call) throws Exception {
        Looper looper = looperOf(queue);
        long[] ptr = new long[1];
        Runnable make = () -> ptr[0] = NativeBinding.<Long>call(MessageQueue.class, "nativeInit", new Class<?>[0]);
        if (looper.isCurrentThread()) make.run();
        else if (!new Handler(looper).runWithScissors(make, 5000)) throw new IllegalStateException("the queue's looper does not run");
        java.lang.reflect.Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        MessageQueue carrier = (MessageQueue) unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, MessageQueue.class);
        java.lang.reflect.Field mPtr = MessageQueue.class.getDeclaredField("mPtr");
        mPtr.setAccessible(true);
        mPtr.setLong(carrier, ptr[0]);
        try { return call.with(carrier); }
        finally { NativeBinding.call(MessageQueue.class, "nativeDestroy", new Class<?>[] {long.class}, ptr[0]); }
    }

    private static Looper looperOf(MessageQueue queue) {
        for (Looper l : ShadowLooper.getAllLoopers()) if (l.getQueue() == queue) return l;
        throw new IllegalStateException("no looper for the queue");
    }
}
