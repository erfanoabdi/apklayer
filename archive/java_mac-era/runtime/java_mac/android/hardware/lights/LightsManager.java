// android.hardware.lights.LightsManager -- control surface for device
// lights (status LEDs, keyboard backlight, controller player-id LEDs).
// Referenced by anarchre. Reached via InputDevice.getLightsManager() (see
// android/view/InputDevice.java), not Context.getSystemService -- that
// mirrors AOSP's own per-input-device LightsManager, as distinct from the
// system-wide one AOSP also exposes through Context.
//
// AOSP declares this class ABSTRACT (frameworks/base/core/java/android/
// hardware/lights/LightsManager.java, fetched 2026-08-12,
// android13-release), with the real implementation living in a hidden
// binder-backed subclass that talks to com.android.server.lights over
// AIDL. Mac.apk has no such service-process split and no lights HAL, so --
// matching this codebase's convention for every other AOSP manager class
// that is "abstract base + hidden impl" upstream (see DisplayManager.java,
// StorageManager.java) -- this is flattened to ONE concrete class.
//
// DESIGN: a Mac has no peripheral-light service, so the AOSP-faithful
// answer is "zero lights available," not a fabricated one:
//   - getLights() returns an empty (never null) list, matching AOSP's
//     @NonNull contract -- callers that iterate it correctly do nothing,
//     instead of NullPointerException-ing on a null they weren't told to
//     expect.
//   - openSession() returns a real, closeable LightsSession (also never
//     null per AOSP) so `try (LightsSession s = mgr.openSession()) { ... }`
//     -- the AOSP-documented usage pattern -- works unmodified.
//   - LightsSession.requestLights() accepts the request (an app should not
//     see an exception for doing something AOSP allows) but has nothing to
//     act on, since getLights() never handed out a light to target.
package android.hardware.lights;

import android.os.Binder;
import android.os.IBinder;

import java.util.Collections;
import java.util.List;

public class LightsManager {
    /** @deprecated moved to {@link Light#LIGHT_TYPE_MICROPHONE}; AOSP keeps
     *  both copies of the constant, so this file does too. */
    @Deprecated
    public static final int LIGHT_TYPE_MICROPHONE = 8;

    public LightsManager() {}

    /** AOSP: "Returns the lights available on the device." Mac.apk has none. */
    public List<Light> getLights() {
        return Collections.emptyList();
    }

    /** AOSP: never null. Since getLights() never hands out a Light, no
     *  well-behaved caller should reach this with one -- but if it does,
     *  "off" (color 0, no player id) is the only truthful answer for
     *  hardware that does not exist. */
    public LightState getLightState(Light light) {
        if (light == null) throw new NullPointerException("light must not be null");
        return new LightState(0, 0);
    }

    public LightsSession openSession() {
        return new LightsSession();
    }

    /** @param priority higher wins when multiple sessions request the same
     *  light simultaneously. Unused: with no lights to arbitrate over,
     *  every session's requests are equally (and completely) inert. */
    public LightsSession openSession(int priority) {
        return new LightsSession();
    }

    /** AOSP: "encapsulates a session ... AutoCloseable that will get closed
     *  when leaving the session context." Flattened to concrete for the
     *  same reason as the outer class -- see file header. */
    public static class LightsSession implements AutoCloseable {
        private final IBinder mToken = new Binder();
        private boolean mClosed;

        public LightsSession() {}

        /** Accepted but inert: there is no light hardware for the request
         *  to reach. See the outer class's file-header DESIGN note. */
        public void requestLights(LightsRequest request) {}

        @Override
        public void close() {
            mClosed = true;
        }

        public boolean isClosed() { return mClosed; }

        public IBinder getToken() { return mToken; }
    }
}
