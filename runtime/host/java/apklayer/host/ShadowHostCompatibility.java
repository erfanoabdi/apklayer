package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Compat changes follow the app's target SDK whichever delegate the framework installed last (see HostCompat). */
@Implements(value = android.compat.Compatibility.class, isInAndroidSdk = false)
public class ShadowHostCompatibility {
    @Implementation
    protected static boolean isChangeEnabled(long changeId) { return HostCompat.enabled(changeId); }
}
