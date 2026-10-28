package apklayer.host;

import org.robolectric.annotation.Implements;

/** PendingIntent's own code over the hub-backed activity manager: the IIntentSender is a hub binder that any app can send through. */
@Implements(className = "android.app.PendingIntent", callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealPendingIntent {
}
