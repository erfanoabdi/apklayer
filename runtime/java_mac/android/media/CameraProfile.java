package android.media;

/**
 * The platform's recommended JPEG encoding quality for camera stills (API 8).
 *
 * These are per-device tuning values read from a media_profiles XML the OEM
 * ships. Mac.apk exposes no camera, so there is no OEM profile to read -- and
 * AOSP's own fallback when a device declares none is the same fixed ladder
 * used here (quality 70 / 80 / 90 for LOW / MEDIUM / HIGH, the values in
 * frameworks/av's default media_profiles.xml). Those are the numbers an app
 * would get from an untuned Android device, so a JPEG it encodes with them
 * comes out at a sensible size rather than at 0 quality.
 *
 * Source: frameworks/base/media/java/android/media/CameraProfile.java
 */
public class CameraProfile {

    public static final int QUALITY_LOW = 0;
    public static final int QUALITY_MEDIUM = 1;
    public static final int QUALITY_HIGH = 2;

    public CameraProfile() { }

    /** AOSP: the quality for camera 0. */
    public static int getJpegEncodingQualityParameter(int quality) {
        return getJpegEncodingQualityParameter(0, quality);
    }

    /** AOSP throws IllegalArgumentException for a quality outside the three
     *  defined levels (CameraProfile.java, getJpegEncodingQualityParameter). */
    public static int getJpegEncodingQualityParameter(int cameraId, int quality) {
        if (quality < QUALITY_LOW || quality > QUALITY_HIGH) {
            throw new IllegalArgumentException("Unsupported quality level: " + quality);
        }
        switch (quality) {
            case QUALITY_LOW:    return 70;
            case QUALITY_MEDIUM: return 80;
            default:             return 90;
        }
    }
}
