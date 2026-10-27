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
