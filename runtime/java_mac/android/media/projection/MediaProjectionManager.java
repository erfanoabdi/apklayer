// android.media.projection.MediaProjectionManager
//
// AOSP's service for screen-capture/casting consent. Apps fetch it via
// getSystemService(MEDIA_PROJECTION_SERVICE) and call createScreenCaptureIntent()
// to prompt the user, then getMediaProjection(resultCode, data) after consent.
// Mac.apk has no screen-capture consent transport, so the capture intent is a
// bare Intent (the consent flow yields RESULT_CANCELED, so apps get null from
// getMediaProjection and take their "capture denied" path). Mirrors
// frameworks/base/media/java/android/media/projection/MediaProjectionManager.java.
package android.media.projection;

import android.content.Context;
import android.content.Intent;

public final class MediaProjectionManager {

    public MediaProjectionManager() {}
    public MediaProjectionManager(Context context) {}

    /** AOSP MediaProjectionManager.EXTRA_MEDIA_PROJECTION_CONFIG (API 33): the
     *  extra the config-taking overload attaches to the consent Intent. */
    public static final String EXTRA_MEDIA_PROJECTION_CONFIG =
            "android.media.projection.extra.EXTRA_MEDIA_PROJECTION_CONFIG";

    /** AOSP createScreenCaptureIntent(): an Intent to start for consent. We
     *  hand back a bare Intent; the (absent) consent UI yields RESULT_CANCELED,
     *  so getMediaProjection() below returns null. */
    public Intent createScreenCaptureIntent() {
        return new Intent();
    }

    /**
     * AOSP createScreenCaptureIntent(MediaProjectionConfig) -- API 33. The
     * config customizes the consent dialog (user-chosen region vs the default
     * display); AOSP builds the same Intent as the no-argument overload and
     * attaches the config as an extra, which is exactly what happens here.
     *
     * The config is carried rather than dropped even though Mac.apk never shows
     * a consent dialog: an app that reads its own Intent back (some do, to
     * re-issue the request) must find what it put there. AOSP throws NPE on a
     * null config -- {@code i.putExtra(key, (Parcelable) null)} would silently
     * store nothing -- so the null check is explicit.
     */
    public Intent createScreenCaptureIntent(MediaProjectionConfig config) {
        java.util.Objects.requireNonNull(config, "config must not be null");
        Intent i = createScreenCaptureIntent();
        i.putExtra(EXTRA_MEDIA_PROJECTION_CONFIG, (android.os.Parcelable) config);
        return i;
    }

    /** AOSP getMediaProjection(resultCode, data): null unless consent was
     *  granted (RESULT_OK). We never grant, so this is always null. */
    public MediaProjection getMediaProjection(int resultCode, Intent resultData) {
        return null;
    }
}
