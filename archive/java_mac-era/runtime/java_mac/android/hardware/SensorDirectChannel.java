// android.hardware.SensorDirectChannel -- a shared-memory channel a sensor HAL
// writes samples into directly, bypassing the per-event Binder round trip.
// Obtained from SensorManager.createDirectChannel(MemoryFile|HardwareBuffer).
//
// Mirrors frameworks/base/core/java/android/hardware/SensorDirectChannel.java
// (android16-release).
//
// Mac.apk never creates one: the sensor values it reports are synthesised in
// Java (see SensorManager's header), there is no HAL to hand a buffer to, and
// createDirectChannel therefore returns null -- which is AOSP's own documented
// failure return ("null on failure"). The type must exist so an app's
// try/`if (channel == null)` path resolves and runs, and so a field or local
// declared as SensorDirectChannel does not fail verification.
package android.hardware;

import java.nio.channels.Channel;

public final class SensorDirectChannel implements Channel {

    /** Stop delivering samples on this channel. */
    public static final int RATE_STOP = 0;
    /** Nominal 50Hz. */
    public static final int RATE_NORMAL = 1;
    /** Nominal 200Hz. */
    public static final int RATE_FAST = 2;
    /** Nominal 800Hz. */
    public static final int RATE_VERY_FAST = 3;

    public static final int TYPE_MEMORY_FILE = 1;
    public static final int TYPE_HARDWARE_BUFFER = 2;

    // AOSP's constructor is package-private and takes the owning SensorManager
    // plus the native channel handle. Kept package-private: an app can only
    // ever receive one of these from SensorManager, and SensorManager here
    // never makes one.
    private final SensorManager mManager;
    private final int mNativeHandle;
    private final long mSize;
    private volatile boolean mClosed;

    /* package */ SensorDirectChannel(SensorManager manager, int handle, long size) {
        mManager = manager;
        mNativeHandle = handle;
        mSize = size;
    }

    @Override public boolean isOpen() { return !mClosed; }

    @Override public void close() {
        mClosed = true;
    }

    /** AOSP returns the report token on success and 0 on failure, and returns 0
     *  unconditionally for RATE_STOP. A closed channel throws
     *  IllegalStateException there, so the check comes first. */
    public int configure(Sensor sensor, int rateLevel) {
        if (mClosed) {
            throw new IllegalStateException("channel is closed");
        }
        return 0;
    }
}
