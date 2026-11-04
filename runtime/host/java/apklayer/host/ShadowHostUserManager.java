package apklayer.host;

import android.os.UserHandle;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowUserManager;

/** The desktop user is always unlocked: credential-protected storage (SharedPreferences, files) is available from the first line of an app (Firebase reads it from a thread at start). */
@Implements(value = android.os.UserManager.class, isInAndroidSdk = false)
public class ShadowHostUserManager extends ShadowUserManager {
    @Implementation protected boolean isUserUnlocked() { return true; }
    @Implementation protected boolean isUserUnlocked(UserHandle user) { return true; }
    @Implementation protected boolean isUserRunning(UserHandle user) { return true; }
    @Implementation protected boolean isUserRunningOrStopping(UserHandle user) { return true; }
    @Implementation protected boolean isUserUnlockingOrUnlocked(UserHandle user) { return true; }
    @Implementation protected boolean isUserUnlockingOrUnlocked(int userId) { return true; }
    @Implementation protected boolean isUserUnlocked(int userId) { return true; }
    /** One user without profiles (the sandbox's user service answers null): SettingsProvider loops over it for the keys it clones to profiles (TIME_12_24). */
    @Implementation protected int[] getProfileIdsWithDisabled(int userId) { return new int[] {userId}; }
}
