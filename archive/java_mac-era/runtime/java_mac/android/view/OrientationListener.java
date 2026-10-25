// android.view.OrientationListener -- the API-1 orientation helper, deprecated
// in favour of OrientationEventListener and implemented on top of it.
//   AOSP: frameworks/base/core/java/android/view/OrientationListener.java
//         (android14-release), mirrored member for member: abstract, implements
//         the deprecated android.hardware.SensorListener, and delegates
//         enable()/disable() to an internal OrientationEventListener whose
//         onOrientationChanged() forwards here.
//
// Honest-capability note: our OrientationEventListener models a device with no
// rotation sensor (macOS has no accelerometer), so onOrientationChanged() never
// fires -- which is exactly what this class does on such a device. AOSP's
// two-arg constructor also calls the package-private
// OrientationEventListener.registerListener(this) so the legacy
// SensorListener.onSensorChanged() mirror receives raw accelerometer values;
// ours has no registerListener because it never has any values to mirror. If
// OrientationEventListener ever gains a real sensor source, add
// registerListener there and call it from the (Context, int) constructor below.
// Census + method: docs/fwgap-new-platform-classes_2026-09-26.md.
package android.view;

import android.content.Context;
import android.hardware.SensorListener;

/**
 * Helper class for receiving notifications from the SensorManager when
 * the orientation of the device has changed.
 *  @deprecated use {@link android.view.OrientationEventListener} instead.
 */
@Deprecated
public abstract class OrientationListener implements SensorListener {
    private OrientationEventListener mOrientationEventLis;

    /**
     * Returned from onOrientationChanged when the device orientation cannot be determined
     * (typically when the device is in a close to flat position).
     */
    public static final int ORIENTATION_UNKNOWN = OrientationEventListener.ORIENTATION_UNKNOWN;

    /** Creates a new OrientationListener. */
    public OrientationListener(Context context) {
        mOrientationEventLis = new OrientationEventListenerInternal(context);
    }

    /** Creates a new OrientationListener sampling at {@code rate}. */
    public OrientationListener(Context context, int rate) {
        mOrientationEventLis = new OrientationEventListenerInternal(context, rate);
    }

    class OrientationEventListenerInternal extends OrientationEventListener {
        OrientationEventListenerInternal(Context context) {
            super(context);
        }

        OrientationEventListenerInternal(Context context, int rate) {
            super(context, rate);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            OrientationListener.this.onOrientationChanged(orientation);
        }
    }

    /**
     * Enables the OrientationListener so it will monitor the sensor and call
     * {@link #onOrientationChanged} when the device orientation changes.
     */
    public void enable() {
        mOrientationEventLis.enable();
    }

    /** Disables the OrientationListener. */
    public void disable() {
        mOrientationEventLis.disable();
    }

    @Override
    public void onAccuracyChanged(int sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(int sensor, float[] values) {
        // just ignore the call here onOrientationChanged is invoked anyway (AOSP)
    }

    /**
     * Look at {@link android.view.OrientationEventListener#onOrientationChanged}
     * for method description and usage
     * @param orientation The new orientation of the device.
     */
    public abstract void onOrientationChanged(int orientation);
}
