// android.media.CamcorderProfile -- a canned set of recording parameters for a
// given quality. AOSP frameworks/base media/java/android/media/CamcorderProfile.java
// (aosp-mirror/platform_frameworks_base, main).
//
// Mac.apk has no camcorder, but apps query CamcorderProfile.get(quality) for
// sensible defaults before configuring MediaRecorder. We return a fixed 720p
// profile so callers get usable, self-consistent fields rather than a crash;
// hasProfile reflects what we can represent.
package android.media;

public class CamcorderProfile {
    public static final int QUALITY_LOW = 0;
    public static final int QUALITY_HIGH = 1;
    public static final int QUALITY_QCIF = 2;
    public static final int QUALITY_CIF = 3;
    public static final int QUALITY_480P = 4;
    public static final int QUALITY_720P = 5;
    public static final int QUALITY_1080P = 6;
    public static final int QUALITY_QVGA = 7;
    public static final int QUALITY_2160P = 8;
    // AOSP CamcorderProfile.java L114-134. These five were missing and are the
    // reason the getAll range check below needs a real upper bound: without
    // them QUALITY_LIST_END would have been wrong by five values, and an app
    // asking for QUALITY_VGA would have been told it is an invalid quality.
    public static final int QUALITY_VGA = 9;
    public static final int QUALITY_4KDCI = 10;
    public static final int QUALITY_QHD = 11;
    public static final int QUALITY_2K = 12;
    public static final int QUALITY_8KUHD = 13;

    public static final int QUALITY_TIME_LAPSE_LOW = 1000;
    public static final int QUALITY_TIME_LAPSE_HIGH = 1001;
    // AOSP L153-208.
    public static final int QUALITY_TIME_LAPSE_QCIF = 1002;
    public static final int QUALITY_TIME_LAPSE_CIF = 1003;
    public static final int QUALITY_TIME_LAPSE_480P = 1004;
    public static final int QUALITY_TIME_LAPSE_720P = 1005;
    public static final int QUALITY_TIME_LAPSE_1080P = 1006;
    public static final int QUALITY_TIME_LAPSE_QVGA = 1007;
    public static final int QUALITY_TIME_LAPSE_2160P = 1008;
    public static final int QUALITY_TIME_LAPSE_VGA = 1009;
    public static final int QUALITY_TIME_LAPSE_4KDCI = 1010;
    public static final int QUALITY_TIME_LAPSE_QHD = 1011;
    public static final int QUALITY_TIME_LAPSE_2K = 1012;
    public static final int QUALITY_TIME_LAPSE_8KUHD = 1013;

    public static final int QUALITY_HIGH_SPEED_LOW = 2000;
    public static final int QUALITY_HIGH_SPEED_HIGH = 2001;
    // AOSP L249-281.
    public static final int QUALITY_HIGH_SPEED_480P = 2002;
    public static final int QUALITY_HIGH_SPEED_720P = 2003;
    public static final int QUALITY_HIGH_SPEED_1080P = 2004;
    public static final int QUALITY_HIGH_SPEED_2160P = 2005;
    public static final int QUALITY_HIGH_SPEED_CIF = 2006;
    public static final int QUALITY_HIGH_SPEED_VGA = 2007;
    public static final int QUALITY_HIGH_SPEED_4KDCI = 2008;

    // AOSP keeps these private and uses them for getAll's range check
    // (CamcorderProfile.java L137-138, L211-212, L284-285).
    private static final int QUALITY_LIST_START = QUALITY_LOW;
    private static final int QUALITY_LIST_END = QUALITY_8KUHD;
    private static final int QUALITY_TIME_LAPSE_LIST_START = QUALITY_TIME_LAPSE_LOW;
    private static final int QUALITY_TIME_LAPSE_LIST_END = QUALITY_TIME_LAPSE_8KUHD;
    private static final int QUALITY_HIGH_SPEED_LIST_START = QUALITY_HIGH_SPEED_LOW;
    private static final int QUALITY_HIGH_SPEED_LIST_END = QUALITY_HIGH_SPEED_4KDCI;

    public int duration;
    public int quality;
    public int fileFormat;
    public int videoCodec;
    public int videoBitRate;
    public int videoFrameRate;
    public int videoFrameWidth;
    public int videoFrameHeight;
    public int audioCodec;
    public int audioBitRate;
    public int audioSampleRate;
    public int audioChannels;

    private CamcorderProfile() {}

    private static CamcorderProfile default720p(int quality) {
        CamcorderProfile p = new CamcorderProfile();
        p.duration = 30;
        p.quality = quality;
        p.fileFormat = MediaRecorder.OutputFormat.MPEG_4;
        p.videoCodec = MediaRecorder.VideoEncoder.H264;
        p.videoBitRate = 8_000_000;
        p.videoFrameRate = 30;
        p.videoFrameWidth = 1280;
        p.videoFrameHeight = 720;
        p.audioCodec = MediaRecorder.AudioEncoder.AAC;
        p.audioBitRate = 128_000;
        p.audioSampleRate = 44100;
        p.audioChannels = 2;
        return p;
    }

    // ⚠ CHANGED 2026-08-30 (v1.0.1113): these answered hasProfile=true with a
    // synthetic 720p profile, and that was a lie that ACTIVELY CAUSED DATA LOSS.
    //
    // MediaRecorder has no encoder or muxer yet -- it creates the output file and
    // leaves it EMPTY -- so advertising a camcorder profile does not merely
    // misinform: it STEERS an app into the one path we cannot honour. A recorder
    // that checks hasProfile(), gets true, records, and ships a 0-byte file has
    // been told a comfortable lie, which CLAUDE.md's decision ladder calls the
    // forbidden fourth rung.
    //
    // AOSP-legal at these values, quoted: hasProfile delegates straight to
    // native_has_camcorder_profile, which answers false when no profile exists;
    // get() is a bare `return native_get_camcorder_profile(cameraId, quality)`
    // and its own class javadoc says "If the device has no back-facing camera,
    // this returns null". So null IS the device answer here, not a stub.
    //
    // This also brings the class into line with the rest of the tree, which
    // already refuses video recording LOUDLY: OutputConfiguration rejects a
    // MediaRecorder surface class and CameraCaptureSessionImpl reports
    // MediaRecorder targets unsupported. hasProfile=true was the odd one out.
    //
    // Restore a real profile only when video recording actually lands, and
    // derive it from a MEASURED VTCompressionSession probe rather than a
    // constant. See docs/media-audio-camera-parity-audit_2026-08-30.md.
    public static CamcorderProfile get(int quality) { return null; }
    public static CamcorderProfile get(int cameraId, int quality) { return null; }
    public static boolean hasProfile(int quality) { return false; }
    public static boolean hasProfile(int cameraId, int quality) { return false; }

    /**
     * The richer, per-codec-list form of get(). API 31.
     *
     * AOSP (CamcorderProfile.java L604-628) does three things before touching
     * the media HAL, and all three are observable, so all three are reproduced:
     * it throws IllegalArgumentException("Unsupported quality level: N") for a
     * quality outside the three ranges, it returns NULL when cameraId does not
     * parse as an integer, and only then reads media_profiles.xml through
     * native_get_camcorder_profiles.
     *
     * For the third step we have no media_profiles.xml and no camcorder, so the
     * answer is derived from the SAME fixed 720p profile get() already returns,
     * expressed as a one-video-profile, one-audio-profile EncoderProfiles. That
     * consistency is the point: an app that reads get() and an app that reads
     * getAll() must not be told two different things about the same device.
     * Returning null instead would be defensible in isolation, but it would
     * push a caller that prefers the modern API onto a null path while the
     * legacy API hands its sibling a full profile.
     */
    public static EncoderProfiles getAll(String cameraId, int quality) {
        if (!((quality >= QUALITY_LIST_START && quality <= QUALITY_LIST_END)
                || (quality >= QUALITY_TIME_LAPSE_LIST_START
                        && quality <= QUALITY_TIME_LAPSE_LIST_END)
                || (quality >= QUALITY_HIGH_SPEED_LIST_START
                        && quality <= QUALITY_HIGH_SPEED_LIST_END))) {
            throw new IllegalArgumentException("Unsupported quality level: " + quality);
        }

        try {
            Integer.valueOf(cameraId);
        } catch (NumberFormatException e) {
            return null;
        }

        // Consistent with get()/hasProfile() above, which now answer null/false:
        // an app reading getAll() and an app reading get() must not be told two
        // different things about the same device -- that consistency was this
        // method's stated purpose, and it is preserved, just at the honest value.
        // AOSP javadoc: "@return null if there are no encoder profiles defined
        // for the quality level."
        return null;
    }
}
