package apklayer.host;

import android.app.KeyguardManager;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowKeyguardManager;

/** Robolectric's keyguard manager; the answers that depend on the instance's credential wait for its enrollment (HostKeyStore.lockScreen), which the app start does not. */
@Implements(value = KeyguardManager.class, isInAndroidSdk = false)
public class ShadowHostKeyguardManager extends ShadowKeyguardManager {
    @Implementation @Override protected boolean isKeyguardSecure() { HostKeyStore.awaitLockScreen(); return super.isKeyguardSecure(); }
    @Implementation @Override protected boolean isDeviceSecure() { HostKeyStore.awaitLockScreen(); return super.isDeviceSecure(); }
    @Implementation @Override protected boolean isDeviceLocked() { HostKeyStore.awaitLockScreen(); return super.isDeviceLocked(); }
    @Implementation @Override protected boolean isDeviceLocked(int userId) { HostKeyStore.awaitLockScreen(); return super.isDeviceLocked(userId); }
}
