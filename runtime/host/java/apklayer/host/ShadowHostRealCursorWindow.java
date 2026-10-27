package apklayer.host;

import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.ShadowPicker;

/** CursorWindow over the framework's own natives (ashmem-backed windows that cross processes in a Parcel), not Robolectric's in-process stand-in. */
@Implements(className = "android.database.CursorWindow", shadowPicker = ShadowHostRealCursorWindow.Picker.class, callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealCursorWindow {
    /** Robolectric chooses its CursorWindow shadow by a picker; with the real binder this one is always the answer. */
    public static class Picker implements ShadowPicker<Object> {
        @Override public Class<?> pickShadowClass() { return ShadowHostRealCursorWindow.class; }
    }
}
