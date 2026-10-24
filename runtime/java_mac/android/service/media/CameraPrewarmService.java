package android.service.media;

import android.content.Intent;
import android.os.IBinder;

/**
 * API 24 hook the lock screen uses to warm up a camera app before the user
 * finishes the swipe: the launcher binds this service on gesture start
 * (onPrewarm) and unbinds when the gesture resolves (onCooldown, told whether
 * the camera actually launched).
 *
 * Mac.apk has no lock screen and no camera-launch gesture, so nothing binds it.
 * A camera app that ships one still has to load the class and instantiate the
 * subclass, and its onPrewarm/onCooldown pair must be callable in a coherent
 * order if the app drives them itself.
 *
 * Source: frameworks/base/media/java/android/service/media/CameraPrewarmService.java
 */
public abstract class CameraPrewarmService extends android.app.Service {

    public CameraPrewarmService() { }

    public abstract void onPrewarm();

    /** @param cameraIntentFired whether the camera was actually launched after
     *  the prewarm; AOSP passes false when the gesture was abandoned. */
    public abstract void onCooldown(boolean cameraIntentFired);

    /** AOSP returns the prewarm binder. android.app.Service.onBind already
     *  returns null in this tree, so this override loses nothing. */
    @Override public IBinder onBind(Intent intent) { return null; }
}
