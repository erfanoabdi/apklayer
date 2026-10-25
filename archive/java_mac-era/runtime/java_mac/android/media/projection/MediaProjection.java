// android.media.projection.MediaProjection
//
// The granted screen-capture token. Mac.apk never grants capture consent (see
// MediaProjectionManager), so apps rarely hold a real instance, but the type
// must resolve for fields/casts/returns. Mirrors
// frameworks/base/media/java/android/media/projection/MediaProjection.java
// (aosp-mirror/platform_frameworks_base, main).
package android.media.projection;

import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

public final class MediaProjection {

    // AOSP keeps the registered callbacks so createVirtualDisplay can enforce
    // the U+ "must register a callback before capturing" rule below.
    private final List<Callback> mCallbacks = new ArrayList<>();

    public MediaProjection() {}

    public abstract static class Callback {
        public void onStop() {}

        /** API 34. Fired when the captured content changes size. Never here:
         *  there is no capture session (see MediaProjectionManager), so nothing
         *  is being captured to resize. */
        public void onCapturedContentResize(int width, int height) {}

        /** API 34. Fired when the captured content becomes (in)visible. Never
         *  here, for the same reason. */
        public void onCapturedContentVisibilityChanged(boolean isVisible) {}
    }

    public void registerCallback(Callback callback, Handler handler) {
        if (callback == null) {
            throw new IllegalArgumentException("callback should not be null");
        }
        mCallbacks.add(callback);
    }

    public void unregisterCallback(Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback should not be null");
        }
        mCallbacks.remove(callback);
    }

    public void stop() {}

    /**
     * Whether this projection may capture the audio other apps are playing.
     *
     * AOSP has no such method on MediaProjection: it asks the projection token
     * itself, {@code projection.getProjection().canProjectAudio()} on
     * IMediaProjection, which is where AudioPlaybackCaptureConfiguration.Builder
     * reads it (AudioPlaybackCaptureConfiguration.java, the Builder's
     * Preconditions.checkArgument). There is no IMediaProjection here -- no
     * binder, no system server -- so the same question is answered on the object
     * an app can actually reach.
     *
     * The answer is a permanent false, and it is a fact about the platform
     * rather than a stub. Playback capture on Android works by inserting a
     * loopback AudioMix into AudioFlinger's policy engine and re-routing OTHER
     * apps' output into it. Mac.apk runs exactly one Android app per process and
     * mixes it straight into CoreAudio; there is no second app whose output
     * could be captured, and no policy engine to insert a mix into. Answering
     * true and then delivering silence would be the worst of the options: the
     * app would record happily and produce an empty file with nothing to blame.
     * False is the answer AOSP gives for a video-only projection, so an app's
     * existing error path already handles it.
     *
     * @hide
     */
    public boolean canProjectAudio() {
        return false;
    }

    /**
     * Start mirroring the screen into the caller's Surface. API 21.
     *
     * AOSP (MediaProjection.java L233-262) does two things before it reaches
     * the display service, and BOTH are reproduced because both are observable:
     *
     *  1. On targetSdk UPSIDE_DOWN_CAKE (34) and up it throws
     *     IllegalStateException when no Callback has been registered, with the
     *     message below. Apps written against API 34+ rely on hitting that
     *     during development, and swallowing it here would let a bug ship.
     *  2. It then builds a VirtualDisplayConfig and asks DisplayManager to
     *     create the display -- which is documented @Nullable, "or null if no
     *     VirtualDisplay could be created".
     *
     * We return null for step 2, deliberately and consistently with
     * android.hardware.display.DisplayManager.createVirtualDisplay, which
     * already returns null in this tree for the same underlying reason: Mac.apk
     * composites one logical display into one NSWindow and has no second
     * display pipeline to rasterize into an app-supplied Surface.
     *
     * Null is the right answer rather than a VirtualDisplay handle, because a
     * handle whose Surface never receives a frame is UNDETECTABLE from the
     * app's side -- it would wait forever for buffers nobody will draw --
     * whereas null is an outcome the AOSP contract already forces every caller
     * to handle.
     *
     * The AOSP callback-required check is deliberately NOT gated on targetSdk
     * here: this tree has no per-app targetSdk plumbed into media.projection,
     * and applying the newer, stricter rule to everyone surfaces a real
     * programming error rather than hiding one. Flagged in case an app that
     * targets < 34 and skips the callback is later found to regress.
     */
    public VirtualDisplay createVirtualDisplay(String name, int width, int height, int dpi,
            int flags, Surface surface, VirtualDisplay.Callback callback, Handler handler) {
        if (mCallbacks.isEmpty()) {
            throw new IllegalStateException(
                    "Must register a callback before starting capture, to manage resources in"
                            + " response to MediaProjection states.");
        }
        return null;
    }

    /**
     * The pre-flags overload. API 21, deprecated in AOSP in favour of the
     * flags-taking one above; it forwards with the AUTO_MIRROR/PUBLIC flags
     * derived from isSecure, and reaches the same null.
     */
    @Deprecated
    public VirtualDisplay createVirtualDisplay(String name, int width, int height, int dpi,
            boolean isSecure, Surface surface, VirtualDisplay.Callback callback, Handler handler) {
        return createVirtualDisplay(name, width, height, dpi, 0, surface, callback, handler);
    }
}
