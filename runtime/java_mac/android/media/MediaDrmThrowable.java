package android.media;

/**
 * Mixed into every DRM-related throwable so an app can recover the vendor's own
 * error codes from any of them. API 33.
 *
 * All three accessors are DEFAULT methods in AOSP returning 0, and 0 is
 * documented as "the vendor did not supply one". Mac.apk has no DRM plugin (see
 * MediaDrm's class comment), so no vendor code can ever exist and 0 is the
 * truthful answer for every throwable -- which is why the AOSP defaults are
 * inherited verbatim rather than overridden.
 *
 * Source: frameworks/base/media/java/android/media/MediaDrmThrowable.java
 */
public interface MediaDrmThrowable {

    /** AOSP: "the error context ... 0 if not available." */
    default int getErrorContext() { return 0; }

    /** AOSP: "the OEM error code ... 0 if not available." */
    default int getOemError() { return 0; }

    /** AOSP: "the vendor error code ... 0 if not available." */
    default int getVendorError() { return 0; }
}
