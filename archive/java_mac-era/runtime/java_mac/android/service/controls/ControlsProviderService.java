package android.service.controls;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.service.controls.actions.ControlAction;

import java.util.List;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

/**
 * Base for a smart-home app's device-controls provider.
 *
 * PACKAGE NOTE -- see DeviceTypes for the full version. System UI's
 * device-controls panel binds this service; Mac.apk has no such panel, so
 * nothing binds it, no publisher is subscribed to, and performControlAction is
 * never called. Everything else in the package (Control, the templates, the
 * actions) is ordinary value objects a provider builds outside the callback,
 * and those are faithful.
 *
 * Constants transcribed verbatim from /tmp/aosp_api/android16.txt.
 * Note SERVICE_CONTROLS (not SERVICE_INTERFACE) is the action name here -- the
 * odd one out among android.service.* services.
 *
 * Source: frameworks/base/core/java/android/service/controls/ControlsProviderService.java
 */
public abstract class ControlsProviderService extends android.app.Service {

    public static final String SERVICE_CONTROLS =
            "android.service.controls.ControlsProviderService";
    public static final String META_DATA_PANEL_ACTIVITY =
            "android.service.controls.META_DATA_PANEL_ACTIVITY";
    public static final String EXTRA_LOCKSCREEN_ALLOW_TRIVIAL_CONTROLS =
            "android.service.controls.extra.LOCKSCREEN_ALLOW_TRIVIAL_CONTROLS";
    /** API 35. */
    public static final String EXTRA_CONTROLS_SURFACE =
            "android.service.controls.extra.CONTROLS_SURFACE";
    public static final int CONTROLS_SURFACE_ACTIVITY_PANEL = 0;
    public static final int CONTROLS_SURFACE_DREAM = 1;

    public static final String TAG = "ControlsProviderService";

    public ControlsProviderService() { }

    public abstract Flow.Publisher<Control> createPublisherForAllAvailable();

    public abstract Flow.Publisher<Control> createPublisherFor(List<String> controlIds);

    /** AOSP's default returns null, meaning "I have no suggestions". A
     *  provider overrides it. */
    public Flow.Publisher<Control> createPublisherForSuggested() { return null; }

    public abstract void performControlAction(String controlId, ControlAction action,
            Consumer<Integer> consumer);

    /** AOSP declares both final and returns the controls binder. */
    @Override public final IBinder onBind(Intent intent) { return null; }

    /** ⚠ NOT a no-op override of a stateful superclass method: AOSP's
     *  ControlsProviderService.onUnbind tears down its own publisher plumbing,
     *  and android.app.Service.onUnbind's body just returns false (this tree
     *  and AOSP alike), so returning false loses nothing. Declared because
     *  AOSP declares it final and a caller resolves it here. */
    @Override public final boolean onUnbind(Intent intent) { return false; }

    /**
     * API 30. AOSP asks System UI to show a "add this control?" prompt. There
     * is no panel to add it to; AOSP itself no-ops when the caller is not the
     * current controls provider, so a provider's own flow is unchanged.
     */
    public static void requestAddControl(Context context, ComponentName componentName,
            Control control) { }
}
