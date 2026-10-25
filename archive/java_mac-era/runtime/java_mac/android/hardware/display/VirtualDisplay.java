// android.hardware.display.VirtualDisplay -- the handle DisplayManager returns
// for a display whose content is rendered into a caller-supplied Surface
// (screen mirroring, Presentation on a second screen, MediaProjection capture).
//
// Why Mac.apk needs the CLASS: Organic Maps, VLC, and Telegram all reference
// it. Class resolution happens when the containing method is first linked, not
// when the call executes, so an absent class breaks a method that may never
// even take the createVirtualDisplay branch -- which is exactly why the type
// has to exist here even though this device never produces an instance.
//
// AOSP source mirrored: frameworks/base/core/java/android/hardware/display/
// VirtualDisplay.java (android11-release): getDisplay, getSurface, setSurface,
// resize, release, toString, and the abstract static Callback with
// onPaused/onResumed/onStopped.
//
// ---------------------------------------------------------------------------
// This device does not create virtual displays, and says so honestly
// ---------------------------------------------------------------------------
// A VirtualDisplay is only meaningful if the compositor can actually rasterize
// a second display into the caller's Surface. Mac.apk composites one logical
// display into one NSWindow; there is no second display pipeline to attach a
// Surface to. So DisplayManager.createVirtualDisplay returns null -- which is a
// documented AOSP outcome ("or null if the application could not create the
// virtual display"), i.e. an answer apps already handle -- rather than handing
// back a VirtualDisplay whose Surface silently receives nothing. The second
// option is undetectable from the app's side: it would wait forever for frames
// that no one will ever draw.
//
// The consequence is that no instance of this class is ever constructed today.
// The instance methods below are still written against real state rather than
// left as `return null` stubs, so that if a virtual-display pipeline is added
// this class does not have to be rewritten -- only its constructor called.
package android.hardware.display;

import android.view.Display;
import android.view.Surface;

/**
 * Represents a virtual display. The content of a virtual display is rendered to
 * a Surface that the caller provides to DisplayManager.createVirtualDisplay.
 */
public final class VirtualDisplay {
    private final Display mDisplay;
    private Surface mSurface;
    private boolean mReleased;

    // AOSP's constructor is package-private and takes (DisplayManagerGlobal,
    // Display, IVirtualDisplayCallback, Surface). The global and the callback
    // token are the handles onto system_server's display service, which this
    // tree has no analogue for, so they are dropped. Package-private is
    // preserved: apps obtain a VirtualDisplay only from DisplayManager.
    VirtualDisplay(Display display, Surface surface) {
        mDisplay = display;
        mSurface = surface;
    }

    /** Gets the virtual display. */
    public Display getDisplay() {
        return mDisplay;
    }

    /** Gets the surface that backs the virtual display. */
    public Surface getSurface() {
        return mSurface;
    }

    /**
     * Sets the surface that backs the virtual display, or null to detach it.
     * Detaching has a similar effect to turning off the screen. It remains the
     * caller's responsibility to destroy a detached surface.
     */
    public void setSurface(Surface surface) {
        if (mSurface != surface) {
            mSurface = surface;
        }
    }

    /**
     * Asks the virtual display to resize, so a caller can adapt to changing
     * conditions without tearing the display down and recreating it.
     */
    public void resize(int width, int height, int densityDpi) {
    }

    /**
     * Releases the virtual display and destroys its underlying surface. All
     * remaining windows on the display are forcibly removed.
     */
    public void release() {
        mReleased = true;
    }

    /**
     * Sets the on/off state for a virtual display.
     * @hide
     */
    public void setDisplayState(boolean isOn) {
    }

    /**
     * AOSP VirtualDisplay.java:122 setRotation(int) -- rotate the virtual
     * display's content.
     *
     * The argument check is AOSP's and comes first, because it is the only
     * observable part of this method on any device: an out-of-range value
     * throws IllegalArgumentException there and must throw here. Note AOSP
     * guards the WHOLE body (including the check) behind the
     * virtual_display_rotation_api flag and returns silently when it is off --
     * so on a device with the flag disabled, an invalid rotation is silently
     * ignored. We treat the flag as ON, i.e. we validate, because a silently
     * swallowed bad argument is the harder failure to diagnose and because
     * current.txt publishes the method.
     *
     * Past the check there is nothing to do: mToken is AOSP's handle onto the
     * display service, no VirtualDisplay is ever constructed here (see the
     * class header and DisplayManager.createVirtualDisplay), and AOSP itself
     * no-ops when that token is null.
     */
    public void setRotation(int rotation) {
        if (rotation != Surface.ROTATION_0 && rotation != Surface.ROTATION_90
                && rotation != Surface.ROTATION_180 && rotation != Surface.ROTATION_270) {
            throw new IllegalArgumentException(
                    "Invalid virtual display rotation value: " + rotation);
        }
    }

    @Override
    public String toString() {
        return "VirtualDisplay{display=" + mDisplay + ", released=" + mReleased
                + ", surface=" + mSurface + "}";
    }

    /**
     * Interface for receiving information about a VirtualDisplay's state
     * changes.
     *
     * Nothing on this device dispatches these callbacks, because nothing here
     * creates a virtual display to change state. An app that registers one
     * simply never hears from it, which is the same thing it observes on a real
     * device when createVirtualDisplay fails.
     */
    public static abstract class Callback {
        /**
         * The projection has been paused by the system, or the app detached the
         * surface with setSurface(null). No further buffers arrive while
         * paused.
         */
        public void onPaused() { }

        /** The projection has resumed after having been paused. */
        public void onResumed() { }

        /**
         * The projection has been stopped by the system. It will receive no
         * more frames and will never resume. Releasing the VirtualDisplay is
         * still the application's responsibility.
         */
        public void onStopped() { }
    }
}
