package android.service.vr;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * API 24 base for a service the platform binds while a VR-mode activity is in
 * the foreground.
 *
 * Mac.apk presents a plain phone/tablet-shaped device and never enters VR mode,
 * so nothing binds this and onCurrentVrActivityChanged never fires. That is the
 * state on every non-VR Android device too -- the class exists there as well
 * and is simply never bound -- so this is honest rather than degraded.
 *
 * Constant transcribed verbatim from /tmp/aosp_api/android16.txt:
 *   SERVICE_INTERFACE = "android.service.vr.VrListenerService"
 *
 * Source: frameworks/base/core/java/android/service/vr/VrListenerService.java
 */
public abstract class VrListenerService extends android.app.Service {

    public static final String SERVICE_INTERFACE = "android.service.vr.VrListenerService";

    public VrListenerService() { }

    /** AOSP returns the listener binder. android.app.Service.onBind already
     *  returns null in this tree, so this override loses nothing. */
    @Override public IBinder onBind(Intent intent) { return null; }

    /** Empty in AOSP too; the listener overrides it. */
    public void onCurrentVrActivityChanged(ComponentName component) { }

    /**
     * AOSP asks the VR manager whether the user enabled this component as a VR
     * helper. There is no VR manager and no such user setting here, so no
     * component is enabled -- the same answer a device gives for a component
     * the user has not enabled.
     */
    public static final boolean isVrModePackageEnabled(Context context,
            ComponentName component) {
        return false;
    }
}
