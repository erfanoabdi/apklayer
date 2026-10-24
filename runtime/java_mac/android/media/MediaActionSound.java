// android.media.MediaActionSound -- plays the camera shutter/focus/record
// sounds. AOSP frameworks/base media/java/android/media/MediaActionSound.java.
// Mac.apk has no bundled shutter samples wired into the mixer, so load() is a
// no-op and play() does nothing audible; the contract (camera UIs call play
// after capture, expecting no return) holds.
package android.media;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class MediaActionSound {
    public static final int SHUTTER_CLICK = 0;
    public static final int FOCUS_COMPLETE = 1;
    public static final int START_VIDEO_RECORDING = 2;
    public static final int STOP_VIDEO_RECORDING = 3;

    public MediaActionSound() {}
    public void load(int soundName) {}
    public void play(int soundName) {}
    public void release() {}

    /**
     * API 33. AOSP: true when the device's region REQUIRES the camera shutter
     * sound to be audible (Japan and South Korea mandate it), read from
     * CameraManager's per-camera "shutter sound must play" characteristic.
     *
     * false here, and honestly: this is not a permission we are granting
     * ourselves -- Mac.apk exposes no camera at all, so there is no shutter to
     * mandate a sound for, and the enforcement AOSP performs lives in the camera
     * HAL rather than in this class. An app that checks this to decide whether
     * to offer a "silent shutter" toggle gets the right answer for the device it
     * is running on.
     */
    public static boolean mustPlayShutterSound() { return false; }
}
