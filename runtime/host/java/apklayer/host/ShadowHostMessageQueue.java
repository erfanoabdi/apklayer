package apklayer.host;

import android.os.MessageQueue;
import java.lang.reflect.InvocationTargetException;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowMessageQueue;
import org.robolectric.shadows.ShadowPausedMessageQueue;

/** Select AOSP's legacy queue before Robolectric's API-36 scheduler inspects mMessages. */
@Implements(value = MessageQueue.class, isInAndroidSdk = false, shadowPicker = ShadowHostMessageQueue.Picker.class)
public class ShadowHostMessageQueue extends ShadowPausedMessageQueue {
    /** queue pointer -> the native Looper of the thread polling it, for threads where native code (Chromium's message pump) created one. */
    /** Only with a WebView provider (-Dapklayer.nativelooper): its native message pumps need this; other apps keep Robolectric's queue untouched. */
    private static final boolean NATIVE_LOOPERS_ENABLED = Boolean.getBoolean("apklayer.nativelooper");
    private static final java.util.Map<Long, Long> NATIVE_LOOPERS = new java.util.concurrent.ConcurrentHashMap<>();

    /** A thread with a native Looper waits there (its eventfds wake it for native tasks, ALooper_wake for Java messages) instead of only on Robolectric's monitor. */
    @Implementation @Override protected void nativePollOnce(long ptr, int timeoutMillis) {
        long looper = NATIVE_LOOPERS_ENABLED ? NativeLooper.forThread() : 0;
        if (looper == 0) { super.nativePollOnce(ptr, timeoutMillis); return; }
        NATIVE_LOOPERS.put(ptr, looper);
        NativeLooper.pollOnce(timeoutMillis);
    }

    @Implementation protected static void nativeWake(long ptr) {
        Long looper = NATIVE_LOOPERS.get(ptr);
        if (looper != null) NativeLooper.wake(looper);
        ShadowPausedMessageQueue.nativeWake(ptr);
    }

    @Implementation protected static void __staticInitializer__() {
        Shadow.directInitialize(MessageQueue.class);
        try {
            MessageQueue.class.getDeclaredMethod("setUseDeliQueue", boolean.class).invoke(null, false);
        } catch (NoSuchMethodException ignored) {
            // Older frameworks have only the legacy queue.
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Cannot select framework message queue", failure);
        }
    }
    public static class Picker extends ShadowMessageQueue.Picker {
        @Override public Class<? extends ShadowMessageQueue> pickShadowClass() {
            Class<? extends ShadowMessageQueue> selected = super.pickShadowClass();
            return selected == ShadowPausedMessageQueue.class ? ShadowHostMessageQueue.class : selected;
        }
    }
}
