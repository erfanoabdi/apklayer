// android.view.OrientationEventListener
//
// AOSP makes this an abstract class that registers a SensorManager
// accelerometer listener and calls onOrientationChanged(int) as the
// device is rotated. macOS has no accelerometer, so a Mac.apk window
// has a fixed orientation -- the honest equivalent of a device with no
// rotation sensor: canDetectOrientation() returns false, enable()/
// disable() are inert, and onOrientationChanged() simply never fires.
//
// Public surface mirrored from frameworks/base OrientationEventListener:
//   public OrientationEventListener(Context)
//   public OrientationEventListener(Context, int rate)
//   void enable() / void disable() / boolean canDetectOrientation()
//   void onOrientationChanged(int)         (abstract in AOSP)
//   int ORIENTATION_UNKNOWN = -1
//
// We keep this CONCRETE (with a no-op onOrientationChanged the subclass
// overrides) rather than abstract, and retain a no-arg ctor, so neither
// a direct `new OrientationEventListener()` nor a subclass regresses.
// Frozen Bubble subclasses it in onCreate via the (Context,int) ctor;
// the missing ctor was throwing NoSuchMethodError and aborting onCreate.
package android.view;

import android.content.Context;

public class OrientationEventListener {
    public static final int ORIENTATION_UNKNOWN = -1;

    private boolean mEnabled = false;

    public OrientationEventListener() {}
    public OrientationEventListener(Context context) {}
    public OrientationEventListener(Context context, int rate) {}

    public void enable()  { mEnabled = true; }
    public void disable() { mEnabled = false; }

    /** No rotation sensor on macOS -> honest false, matching a device
     *  whose SensorManager has no TYPE_ACCELEROMETER. */
    public boolean canDetectOrientation() { return false; }

    /** Never invoked (no sensor); subclasses override per AOSP contract. */
    public void onOrientationChanged(int orientation) {}
}
