// android.media.MediaCodecInfo -- describes one codec component. AOSP
// frameworks/base media/java/android/media/MediaCodecInfo.java.
//
// Entries come from MediaCodecList's registry, which is itself built from
// HostCodecCapability data contributed by a HostCodecProvider (see both
// classes) -- REAL, measured host capability, never a hardcoded wish list.
// getCapabilitiesForType/isFormatSupported/AudioCapabilities/VideoCapabilities
// below implement the actual AOSP contract (mirrored from the source above)
// rather than stub answers, so ExoPlayer/media3's real selection algorithm
// (MediaCodecList.findDecoderForFormat -> CodecCapabilities.isFormatSupported)
// gets a real answer instead of tripping a null-check.
package android.media;

import android.util.Range;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class MediaCodecInfo {
    private static final int FLAG_IS_ENCODER = (1 << 0);
    private static final int FLAG_IS_VENDOR = (1 << 1);
    private static final int FLAG_IS_SOFTWARE_ONLY = (1 << 2);
    private static final int FLAG_IS_HARDWARE_ACCELERATED = (1 << 3);

    private final int mFlags;
    private final String mName;
    private final String mCanonicalName;
    private final Map<String, CodecCapabilities> mCaps;

    MediaCodecInfo(String name, String canonicalName, int flags, CodecCapabilities[] caps) {
        mName = name;
        mCanonicalName = canonicalName != null ? canonicalName : name;
        mFlags = flags;
        mCaps = new HashMap<String, CodecCapabilities>();
        for (CodecCapabilities c : caps) {
            mCaps.put(c.mMime, c);
        }
    }

    /** Turns one provider-contributed HostCodecCapability into a real
     *  MediaCodecInfo. The ONE place data becomes an object -- see
     *  MediaCodecList.buildCodecInfos(), the only caller. Naming matches
     *  MediaCodec.createDecoderByType/createEncoderByType exactly
     *  ("OMX.macapk.dec."/"OMX.macapk.enc." + mime) so
     *  createByCodecName(info.getName()) yields the same codec. */
    static MediaCodecInfo fromHostCapability(HostCodecCapability cap) {
        int flags = 0;
        if (cap.encoder) flags |= FLAG_IS_ENCODER;
        flags |= cap.hardwareAccelerated ? FLAG_IS_HARDWARE_ACCELERATED : FLAG_IS_SOFTWARE_ONLY;
        String name = (cap.encoder ? "OMX.macapk.enc." : "OMX.macapk.dec.") + cap.mime;
        CodecCapabilities caps = CodecCapabilities.fromHostCapability(cap);
        return new MediaCodecInfo(name, name, flags, new CodecCapabilities[] { caps });
    }

    /** Used by MediaCodec.getCodecInfo() (MediaCodec.java, not owned by this
     *  lane) to resolve a MediaCodecInfo purely from a codec name string.
     *  Looks the name up in the real registry first -- so a codec created via
     *  createDecoderByType(mime) reports its REAL capabilities here too, not
     *  just when reached via MediaCodecList.getCodecInfos() -- and only falls
     *  back to an honest capability-free placeholder for a name the registry
     *  does not recognize (e.g. a mime nothing on this host can decode). */
    static MediaCodecInfo forName(String name) {
        MediaCodecInfo real = MediaCodecList.findByName(name);
        if (real != null) return real;
        boolean enc = name != null && name.contains(".enc.");
        return new MediaCodecInfo(name, name, enc ? FLAG_IS_ENCODER : 0, new CodecCapabilities[0]);
    }

    public String getName() { return mName; }
    public String getCanonicalName() { return mCanonicalName; }
    public boolean isAlias() { return !mName.equals(mCanonicalName); }
    public boolean isEncoder() { return (mFlags & FLAG_IS_ENCODER) != 0; }
    public boolean isVendor() { return (mFlags & FLAG_IS_VENDOR) != 0; }
    public boolean isSoftwareOnly() { return (mFlags & FLAG_IS_SOFTWARE_ONLY) != 0; }
    public boolean isHardwareAccelerated() { return (mFlags & FLAG_IS_HARDWARE_ACCELERATED) != 0; }

    /** The codec runs in a separate sandboxed process (mediaswcodec / a vendor
     *  HAL). AOSP MediaCodecInfo.java, SECURITY_MODEL_SANDBOXED. */
    public static final int SECURITY_MODEL_SANDBOXED = 0;
    /** The codec runs IN the app process but is written in a memory-safe
     *  language. AOSP MediaCodecInfo.java, SECURITY_MODEL_MEMORY_SAFE. */
    public static final int SECURITY_MODEL_MEMORY_SAFE = 1;

    /**
     * API 36. How the codec is isolated from the app.
     *
     * SANDBOXED is the truthful answer here and the safer one to be wrong
     * about. Mac.apk's codecs are AudioToolbox/VideoToolbox, which decode
     * out-of-process in Apple's own coreaudiod / VTDecoderXPCService rather
     * than inside the app -- so the app's address space is not where untrusted
     * bitstream is parsed, which is exactly what SANDBOXED asserts.
     * MEMORY_SAFE is the value an app may use to justify feeding a codec
     * untrusted data IN-process, so claiming it without a memory-safe in-process
     * decoder would be the dangerous direction.
     */
    public int getSecurityModel() { return SECURITY_MODEL_SANDBOXED; }

    public String[] getSupportedTypes() {
        String[] types = mCaps.keySet().toArray(new String[mCaps.size()]);
        Arrays.sort(types);
        return types;
    }

    /** AOSP throws IllegalArgumentException("codec does not support type")
     *  when the mime is not one of this codec's supported types; mirrored
     *  exactly rather than returning an empty-but-present CodecCapabilities,
     *  since a caller relies on the exception to mean "wrong type, try a
     *  different codec" (see MediaCodecList.findCodecForFormat's catch). */
    public CodecCapabilities getCapabilitiesForType(String type) {
        CodecCapabilities caps = mCaps.get(type);
        if (caps == null) {
            throw new IllegalArgumentException("codec does not support type");
        }
        return caps.dup();
    }

    public static final class CodecCapabilities {
        // AOSP CodecCapabilities.FEATURE_* (frameworks/base .../MediaCodecInfo.java).
        // Mac.apk implements NONE of these -- most importantly
        // FEATURE_SecurePlayback: this project does not implement DRM (see
        // CLAUDE.md, "Explicitly OUT of scope"), so isFeatureSupported below
        // must never answer true for it, and isFormatSupported must reject a
        // format that requires it. That is what makes a video codec entry
        // safe to advertise once one is real: it can never be mistaken for a
        // ".secure" variant we do not have.
        public static final String FEATURE_AdaptivePlayback = "adaptive-playback";
        public static final String FEATURE_SecurePlayback = "secure-playback";
        public static final String FEATURE_TunneledPlayback = "tunneled-playback";
        public static final String FEATURE_DynamicTimestamp = "dynamic-timestamp";
        public static final String FEATURE_FrameParsing = "frame-parsing";
        public static final String FEATURE_MultipleFrames = "multiple-frames";
        public static final String FEATURE_PartialFrame = "partial-frame";
        public static final String FEATURE_IntraRefresh = "intra-refresh";
        public static final String FEATURE_LowLatency = "low-latency";
        public static final String FEATURE_QpBounds = "qp-bounds";
        public static final String FEATURE_EncodingStatistics = "encoding-statistics";
        public static final String FEATURE_HdrEditing = "hdr-editing";

        private static final Range<Integer> DEFAULT_BITRATE_RANGE = new Range<Integer>(0, Integer.MAX_VALUE);
        private static final int DEFAULT_MAX_SUPPORTED_INSTANCES = 32;   // AOSP's own default hint

        String mMime;
        public CodecProfileLevel[] profileLevels = new CodecProfileLevel[0];   // modifiable by caller, per AOSP
        public int[] colorFormats = new int[0];
        private AudioCapabilities mAudioCaps;
        private VideoCapabilities mVideoCaps;
        // Encoding is out of scope end to end (MediaCodec's native backing is
        // decode-only -- see MediaCodec.java's own header comment), so this
        // stays permanently null: AOSP's own contract for
        // getEncoderCapabilities() is "null if this is not an encoder".
        private final EncoderCapabilities mEncoderCaps = null;

        public CodecCapabilities() {}

        /**
         * AOSP: builds a minimal CodecCapabilities for a (mime, profile, level)
         * triple -- "@return null if the type/profile/level is not supported"
         * (MediaCodecInfo.java, createFromProfileLevel). It is a pure
         * constructor helper, not a capability query: AOSP does not consult the
         * device's codec list either, it just wraps the arguments.
         *
         * Mirrored exactly, including AOSP's own guard that rejects a caller
         * asking for a profile of 0 -- so an app that builds one of these and
         * hands it to isFormatSupported() gets this host's real answer from the
         * registry, rather than a fabricated capability from this method.
         */
        public static CodecCapabilities createFromProfileLevel(String mime, int profile,
                int level) {
            if (mime == null || profile == 0) return null;
            CodecProfileLevel pl = new CodecProfileLevel();
            pl.profile = profile;
            pl.level = level;
            CodecCapabilities ret = new CodecCapabilities();
            ret.mMime = mime;
            ret.profileLevels = new CodecProfileLevel[] { pl };
            return ret;
        }

        static CodecCapabilities fromHostCapability(HostCodecCapability cap) {
            CodecCapabilities c = new CodecCapabilities();
            c.mMime = cap.mime;
            c.profileLevels = cap.profileLevels.clone();
            if (cap.isAudio()) c.mAudioCaps = AudioCapabilities.fromHostCapability(cap);
            if (cap.isVideo()) c.mVideoCaps = VideoCapabilities.fromHostCapability(cap);
            return c;
        }

        /** Defensive copy returned by getCapabilitiesForType: profileLevels
         *  is documented (AOSP included) as modifiable by the caller, so a
         *  caller mutating its copy must not corrupt the shared registry
         *  entry every future getCapabilitiesForType(type) call returns. */
        CodecCapabilities dup() {
            CodecCapabilities c = new CodecCapabilities();
            c.mMime = mMime;
            c.profileLevels = profileLevels.clone();
            c.colorFormats = colorFormats.clone();
            c.mAudioCaps = mAudioCaps;   // immutable after construction: safe to share
            c.mVideoCaps = mVideoCaps;
            return c;
        }

        public String getMimeType() { return mMime; }
        public int getMaxSupportedInstances() { return DEFAULT_MAX_SUPPORTED_INSTANCES; }
        public AudioCapabilities getAudioCapabilities() { return mAudioCaps; }
        public VideoCapabilities getVideoCapabilities() { return mVideoCaps; }
        public EncoderCapabilities getEncoderCapabilities() { return mEncoderCaps; }

        /** We support none of the FEATURE_* set: no DRM (no secure-playback,
         *  ever -- see the field-block comment above), no tunneled/adaptive
         *  pipeline, no per-frame statistics. Uniformly false is the honest
         *  answer for every codec this registry can produce today. */
        public boolean isFeatureSupported(String name) { return false; }
        public boolean isFeatureRequired(String name) { return false; }

        public MediaFormat getDefaultFormat() {
            MediaFormat f = new MediaFormat();
            f.setString(MediaFormat.KEY_MIME, mMime);
            // AOSP: "report settings that have only a single choice" -- only
            // meaningful when a capability table narrows to one value, which
            // ours (13 AAC rates, 9 MP3 rates) never does today.
            if (mAudioCaps != null) mAudioCaps.fillDefaultFormat(f);
            return f;
        }

        /** AOSP frameworks/base .../MediaCodecInfo.java CodecCapabilities#isFormatSupported,
         *  simplified to what this registry can actually populate: mime match,
         *  feature directives (see the FEATURE_* block above -- this is the
         *  choke point that keeps a DRM-requiring format from ever matching),
         *  a declared profile if the codec has a profile/level table, then the
         *  audio or video capability check. */
        public final boolean isFormatSupported(MediaFormat format) {
            Map<String, Object> map = format.getMap();
            String mime = (String) map.get(MediaFormat.KEY_MIME);
            if (mime != null && !mMime.equalsIgnoreCase(mime)) return false;

            for (Map.Entry<String, Object> e : map.entrySet()) {
                String key = e.getKey();
                if (!key.startsWith("feature-")) continue;
                Object v = e.getValue();
                if (!(v instanceof Integer)) continue;
                boolean requested = ((Integer) v).intValue() != 0;
                String featureName = key.substring("feature-".length());
                if (requested && !isFeatureSupported(featureName)) return false;
            }

            Integer profile = (Integer) map.get(MediaFormat.KEY_PROFILE);
            Integer level = (Integer) map.get(MediaFormat.KEY_LEVEL);
            if (profile != null && !supportsProfileLevel(profile.intValue(), level)) return false;

            if (mAudioCaps != null && !mAudioCaps.supportsFormat(format)) return false;
            if (mVideoCaps != null && !mVideoCaps.supportsFormat(format)) return false;
            return true;
        }

        // An empty profileLevels means "this format has no meaningful
        // profile/level concept" (true of MP3 -- real device mp3 decoders
        // list none either), so a stray KEY_PROFILE on such a format is not
        // grounds for rejection. When we DO carry a table (AAC; the probed
        // AVC/HEVC and the libvpx VP8/VP9 tables), the pair must be covered,
        // per AOSP CodecCapabilities.supportsProfileLevel: same profile, no
        // level asked or AAC (which has no levels) -> yes; an HEVC HIGH-tier
        // level is only covered by a high-tier entry ("high tier levels are
        // only supported by other high tier levels"); otherwise the listed
        // level must be >= the asked one. (AOSP's H.263 / MPEG-4 partial-order
        // special cases are not mirrored: no entry here carries a table for
        // those two mimes.)
        private boolean supportsProfileLevel(int profile, Integer level) {
            if (profileLevels.length == 0) return true;
            for (CodecProfileLevel pl : profileLevels) {
                if (pl.profile != profile) continue;
                if (level == null) return true;
                if (MediaFormat.MIMETYPE_AUDIO_AAC.equalsIgnoreCase(mMime)) return true;
                if (MediaFormat.MIMETYPE_VIDEO_HEVC.equalsIgnoreCase(mMime)) {
                    boolean supportsHighTier = (pl.level & CodecProfileLevel.HEVCHighTierLevels) != 0;
                    boolean checkingHighTier = (level.intValue() & CodecProfileLevel.HEVCHighTierLevels) != 0;
                    if (checkingHighTier && !supportsHighTier) continue;
                }
                if (pl.level >= level.intValue()) return true;
            }
            return false;
        }

        static boolean supportsBitrate(Range<Integer> bitrateRange, MediaFormat format) {
            Map<String, Object> map = format.getMap();
            Integer maxBitrate = (Integer) map.get(MediaFormat.KEY_MAX_BIT_RATE);
            Integer bitrate = (Integer) map.get(MediaFormat.KEY_BIT_RATE);
            if (bitrate == null) bitrate = maxBitrate;
            else if (maxBitrate != null) bitrate = Math.max(bitrate, maxBitrate);
            if (bitrate != null && bitrate > 0) return bitrateRange.contains(bitrate);
            return true;
        }
    }

    /** Profile/level constants, verbatim from AOSP frameworks/base
     *  media/java/android/media/MediaCodecInfo.java (Apache-2.0) --
     *  mirrored in full per CLAUDE.md's "mirror AOSP's public API surface
     *  exactly" convention, even though only the AAC ones are populated by
     *  any registry entry today. Kept complete rather than trimmed to "what
     *  we use now" so the video seam (MediaCodecList's buildCodecInfos) has
     *  real H.264/HEVC/VP8/VP9/AV1 profile/level values ready the moment a
     *  HostCodecProvider supplies them, instead of a second AOSP fetch. */
    public static final class CodecProfileLevel {
        public static final int AVCProfileBaseline = 0x01;
        public static final int AVCProfileMain     = 0x02;
        public static final int AVCProfileExtended = 0x04;
        public static final int AVCProfileHigh     = 0x08;
        public static final int AVCProfileHigh10   = 0x10;
        public static final int AVCProfileHigh422  = 0x20;
        public static final int AVCProfileHigh444  = 0x40;
        public static final int AVCProfileConstrainedBaseline = 0x10000;
        public static final int AVCProfileConstrainedHigh     = 0x80000;
        public static final int AVCLevel1       = 0x01;
        public static final int AVCLevel1b      = 0x02;
        public static final int AVCLevel11      = 0x04;
        public static final int AVCLevel12      = 0x08;
        public static final int AVCLevel13      = 0x10;
        public static final int AVCLevel2       = 0x20;
        public static final int AVCLevel21      = 0x40;
        public static final int AVCLevel22      = 0x80;
        public static final int AVCLevel3       = 0x100;
        public static final int AVCLevel31      = 0x200;
        public static final int AVCLevel32      = 0x400;
        public static final int AVCLevel4       = 0x800;
        public static final int AVCLevel41      = 0x1000;
        public static final int AVCLevel42      = 0x2000;
        public static final int AVCLevel5       = 0x4000;
        public static final int AVCLevel51      = 0x8000;
        public static final int AVCLevel52      = 0x10000;
        public static final int AVCLevel6       = 0x20000;
        public static final int AVCLevel61      = 0x40000;
        public static final int AVCLevel62      = 0x80000;
        public static final int H263ProfileBaseline             = 0x01;
        public static final int H263ProfileH320Coding           = 0x02;
        public static final int H263ProfileBackwardCompatible   = 0x04;
        public static final int H263ProfileISWV2                = 0x08;
        public static final int H263ProfileISWV3                = 0x10;
        public static final int H263ProfileHighCompression      = 0x20;
        public static final int H263ProfileInternet             = 0x40;
        public static final int H263ProfileInterlace            = 0x80;
        public static final int H263ProfileHighLatency          = 0x100;
        public static final int H263Level10      = 0x01;
        public static final int H263Level20      = 0x02;
        public static final int H263Level30      = 0x04;
        public static final int H263Level40      = 0x08;
        public static final int H263Level45      = 0x10;
        public static final int H263Level50      = 0x20;
        public static final int H263Level60      = 0x40;
        public static final int H263Level70      = 0x80;
        public static final int MPEG4ProfileSimple              = 0x01;
        public static final int MPEG4ProfileSimpleScalable      = 0x02;
        public static final int MPEG4ProfileCore                = 0x04;
        public static final int MPEG4ProfileMain                = 0x08;
        public static final int MPEG4ProfileNbit                = 0x10;
        public static final int MPEG4ProfileScalableTexture     = 0x20;
        public static final int MPEG4ProfileSimpleFace          = 0x40;
        public static final int MPEG4ProfileSimpleFBA           = 0x80;
        public static final int MPEG4ProfileBasicAnimated       = 0x100;
        public static final int MPEG4ProfileHybrid              = 0x200;
        public static final int MPEG4ProfileAdvancedRealTime    = 0x400;
        public static final int MPEG4ProfileCoreScalable        = 0x800;
        public static final int MPEG4ProfileAdvancedCoding      = 0x1000;
        public static final int MPEG4ProfileAdvancedCore        = 0x2000;
        public static final int MPEG4ProfileAdvancedScalable    = 0x4000;
        public static final int MPEG4ProfileAdvancedSimple      = 0x8000;
        public static final int MPEG4Level0      = 0x01;
        public static final int MPEG4Level0b     = 0x02;
        public static final int MPEG4Level1      = 0x04;
        public static final int MPEG4Level2      = 0x08;
        public static final int MPEG4Level3      = 0x10;
        public static final int MPEG4Level3b     = 0x18;
        public static final int MPEG4Level4      = 0x20;
        public static final int MPEG4Level4a     = 0x40;
        public static final int MPEG4Level5      = 0x80;
        public static final int MPEG4Level6      = 0x100;
        public static final int MPEG2ProfileSimple              = 0x00;
        public static final int MPEG2ProfileMain                = 0x01;
        public static final int MPEG2Profile422                 = 0x02;
        public static final int MPEG2ProfileSNR                 = 0x03;
        public static final int MPEG2ProfileSpatial             = 0x04;
        public static final int MPEG2ProfileHigh                = 0x05;
        public static final int MPEG2LevelLL     = 0x00;
        public static final int MPEG2LevelML     = 0x01;
        public static final int MPEG2LevelH14    = 0x02;
        public static final int MPEG2LevelHL     = 0x03;
        public static final int MPEG2LevelHP     = 0x04;
        public static final int AACObjectMain       = 1;
        public static final int AACObjectLC         = 2;
        public static final int AACObjectSSR        = 3;
        public static final int AACObjectLTP        = 4;
        public static final int AACObjectHE         = 5;
        public static final int AACObjectScalable   = 6;
        public static final int AACObjectERLC       = 17;
        public static final int AACObjectERScalable = 20;
        public static final int AACObjectLD         = 23;
        public static final int AACObjectHE_PS      = 29;
        public static final int AACObjectELD        = 39;
        public static final int AACObjectXHE        = 42;
        public static final int VP8Level_Version0 = 0x01;
        public static final int VP8Level_Version1 = 0x02;
        public static final int VP8Level_Version2 = 0x04;
        public static final int VP8Level_Version3 = 0x08;
        public static final int VP8ProfileMain = 0x01;
        public static final int VP9Profile0 = 0x01;
        public static final int VP9Profile1 = 0x02;
        public static final int VP9Profile2 = 0x04;
        public static final int VP9Profile3 = 0x08;
        public static final int VP9Profile2HDR = 0x1000;
        public static final int VP9Profile3HDR = 0x2000;
        public static final int VP9Profile2HDR10Plus = 0x4000;
        public static final int VP9Profile3HDR10Plus = 0x8000;
        public static final int VP9Level1  = 0x1;
        public static final int VP9Level11 = 0x2;
        public static final int VP9Level2  = 0x4;
        public static final int VP9Level21 = 0x8;
        public static final int VP9Level3  = 0x10;
        public static final int VP9Level31 = 0x20;
        public static final int VP9Level4  = 0x40;
        public static final int VP9Level41 = 0x80;
        public static final int VP9Level5  = 0x100;
        public static final int VP9Level51 = 0x200;
        public static final int VP9Level52 = 0x400;
        public static final int VP9Level6  = 0x800;
        public static final int VP9Level61 = 0x1000;
        public static final int VP9Level62 = 0x2000;
        public static final int HEVCProfileMain        = 0x01;
        public static final int HEVCProfileMain10      = 0x02;
        public static final int HEVCProfileMainStill   = 0x04;
        public static final int HEVCProfileMain10HDR10 = 0x1000;
        public static final int HEVCProfileMain10HDR10Plus = 0x2000;
        public static final int HEVCMainTierLevel1  = 0x1;
        public static final int HEVCHighTierLevel1  = 0x2;
        public static final int HEVCMainTierLevel2  = 0x4;
        public static final int HEVCHighTierLevel2  = 0x8;
        public static final int HEVCMainTierLevel21 = 0x10;
        public static final int HEVCHighTierLevel21 = 0x20;
        public static final int HEVCMainTierLevel3  = 0x40;
        public static final int HEVCHighTierLevel3  = 0x80;
        public static final int HEVCMainTierLevel31 = 0x100;
        public static final int HEVCHighTierLevel31 = 0x200;
        public static final int HEVCMainTierLevel4  = 0x400;
        public static final int HEVCHighTierLevel4  = 0x800;
        public static final int HEVCMainTierLevel41 = 0x1000;
        public static final int HEVCHighTierLevel41 = 0x2000;
        public static final int HEVCMainTierLevel5  = 0x4000;
        public static final int HEVCHighTierLevel5  = 0x8000;
        public static final int HEVCMainTierLevel51 = 0x10000;
        public static final int HEVCHighTierLevel51 = 0x20000;
        public static final int HEVCMainTierLevel52 = 0x40000;
        public static final int HEVCHighTierLevel52 = 0x80000;
        public static final int HEVCMainTierLevel6  = 0x100000;
        public static final int HEVCHighTierLevel6  = 0x200000;
        public static final int HEVCMainTierLevel61 = 0x400000;
        public static final int HEVCHighTierLevel61 = 0x800000;
        public static final int HEVCMainTierLevel62 = 0x1000000;
        public static final int HEVCHighTierLevel62 = 0x2000000;
        // AOSP: private, read by CodecCapabilities.supportsProfileLevel's tier rule.
        private static final int HEVCHighTierLevels =
            HEVCHighTierLevel1 | HEVCHighTierLevel2 | HEVCHighTierLevel21 | HEVCHighTierLevel3 |
            HEVCHighTierLevel31 | HEVCHighTierLevel4 | HEVCHighTierLevel41 | HEVCHighTierLevel5 |
            HEVCHighTierLevel51 | HEVCHighTierLevel52 | HEVCHighTierLevel6 | HEVCHighTierLevel61 |
            HEVCHighTierLevel62;
        public static final int DolbyVisionProfileDvavPer = 0x1;
        public static final int DolbyVisionProfileDvavPen = 0x2;
        public static final int DolbyVisionProfileDvheDer = 0x4;
        public static final int DolbyVisionProfileDvheDen = 0x8;
        public static final int DolbyVisionProfileDvheDtr = 0x10;
        public static final int DolbyVisionProfileDvheStn = 0x20;
        public static final int DolbyVisionProfileDvheDth = 0x40;
        public static final int DolbyVisionProfileDvheDtb = 0x80;
        public static final int DolbyVisionProfileDvheSt  = 0x100;
        public static final int DolbyVisionProfileDvavSe  = 0x200;
        public static final int DolbyVisionProfileDvav110 = 0x400;
        public static final int DolbyVisionLevelHd24    = 0x1;
        public static final int DolbyVisionLevelHd30    = 0x2;
        public static final int DolbyVisionLevelFhd24   = 0x4;
        public static final int DolbyVisionLevelFhd30   = 0x8;
        public static final int DolbyVisionLevelFhd60   = 0x10;
        public static final int DolbyVisionLevelUhd24   = 0x20;
        public static final int DolbyVisionLevelUhd30   = 0x40;
        public static final int DolbyVisionLevelUhd48   = 0x80;
        public static final int DolbyVisionLevelUhd60   = 0x100;
        public static final int DolbyVisionLevelUhd120  = 0x200;
        public static final int DolbyVisionLevel8k30    = 0x400;
        public static final int DolbyVisionLevel8k60    = 0x800;
        public static final int AV1ProfileMain8   = 0x1;
        public static final int AV1ProfileMain10  = 0x2;
        public static final int AV1ProfileMain10HDR10 = 0x1000;
        public static final int AV1ProfileMain10HDR10Plus = 0x2000;
        public static final int AV1Level2       = 0x1;
        public static final int AV1Level21      = 0x2;
        public static final int AV1Level22      = 0x4;
        public static final int AV1Level23      = 0x8;
        public static final int AV1Level3       = 0x10;
        public static final int AV1Level31      = 0x20;
        public static final int AV1Level32      = 0x40;
        public static final int AV1Level33      = 0x80;
        public static final int AV1Level4       = 0x100;
        public static final int AV1Level41      = 0x200;
        public static final int AV1Level42      = 0x400;
        public static final int AV1Level43      = 0x800;
        public static final int AV1Level5       = 0x1000;
        public static final int AV1Level51      = 0x2000;
        public static final int AV1Level52      = 0x4000;
        public static final int AV1Level53      = 0x8000;
        public static final int AV1Level6       = 0x10000;
        public static final int AV1Level61      = 0x20000;
        public static final int AV1Level62      = 0x40000;
        public static final int AV1Level63      = 0x80000;
        public static final int AV1Level7       = 0x100000;
        public static final int AV1Level71      = 0x200000;
        public static final int AV1Level72      = 0x400000;
        public static final int AV1Level73      = 0x800000;

        /** The profile of the media content -- one of the *Profile* values
         *  declared above, chosen per the codec's mime type. */
        public int profile;
        /** The level of the media content -- one of the *Level* values
         *  declared above. AAC has no notion of level (see
         *  CodecCapabilities.supportsProfile's caller); this stays 0 for
         *  every AAC entry, matching AOSP's own "AAC does not use levels". */
        public int level;
        public CodecProfileLevel() {}
        public CodecProfileLevel(int profile, int level) { this.profile = profile; this.level = level; }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (obj instanceof CodecProfileLevel) {
                CodecProfileLevel other = (CodecProfileLevel) obj;
                return other.profile == profile && other.level == level;
            }
            return false;
        }
        @Override
        public int hashCode() { return (profile << 16) ^ level; }
    }

    /** AOSP frameworks/base .../MediaCodecInfo.java AudioCapabilities,
     *  rebuilt from a HostCodecCapability instead of vendor XML (we have no
     *  vendor XML -- the host itself, via AudioToolbox, IS the "vendor").
     *  Every number here traces to a HostCodecCapability the registry built
     *  from a verified-real source; see MediaCodecList's registry comments
     *  for what backs each currently-registered entry. */
    public static final class AudioCapabilities {
        private int[] mSampleRates;                 // null => continuous (see mSampleRateRanges)
        private Range<Integer>[] mSampleRateRanges;
        private int mMaxChannels = 1;
        private Range<Integer> mBitrateRange = CodecCapabilities.DEFAULT_BITRATE_RANGE;

        private AudioCapabilities() {}

        @SuppressWarnings("unchecked")
        static AudioCapabilities fromHostCapability(HostCodecCapability cap) {
            AudioCapabilities ac = new AudioCapabilities();
            if (cap.sampleRates != null) {
                int[] rates = cap.sampleRates.clone();
                Arrays.sort(rates);
                ac.mSampleRates = rates;
                Range<Integer>[] ranges = new Range[rates.length];
                for (int i = 0; i < rates.length; i++) ranges[i] = new Range<Integer>(rates[i], rates[i]);
                ac.mSampleRateRanges = ranges;
            } else if (cap.sampleRateRange != null) {
                ac.mSampleRateRanges = new Range[] { cap.sampleRateRange };
            } else {
                ac.mSampleRateRanges = new Range[0];
            }
            ac.mMaxChannels = Math.max(1, cap.maxChannels);
            if (cap.bitrateRange != null) ac.mBitrateRange = cap.bitrateRange;
            return ac;
        }

        private boolean supports(Integer sampleRate, Integer channels) {
            if (channels != null && (channels < 1 || channels > mMaxChannels)) return false;
            if (sampleRate != null) {
                boolean inRange = false;
                for (Range<Integer> r : mSampleRateRanges) {
                    if (r.contains(sampleRate)) { inRange = true; break; }
                }
                if (!inRange) return false;
            }
            return true;
        }

        /** Query whether the sample rate is supported by the codec. */
        public boolean isSampleRateSupported(int sampleRate) { return supports(sampleRate, null); }

        /** Discrete supported sample rates, sorted ascending, or null if the
         *  codec supports a continuous range instead (AOSP contract). */
        public int[] getSupportedSampleRates() {
            return mSampleRates != null ? mSampleRates.clone() : null;
        }
        public Range<Integer>[] getSupportedSampleRateRanges() { return mSampleRateRanges.clone(); }

        public int getMaxInputChannelCount() { return mMaxChannels; }
        public int getMinInputChannelCount() { return 1; }
        @SuppressWarnings("unchecked")
        public Range<Integer>[] getInputChannelCountRanges() {
            return new Range[] { new Range<Integer>(1, mMaxChannels) };
        }
        public Range<Integer> getBitrateRange() { return mBitrateRange; }

        void fillDefaultFormat(MediaFormat format) {
            if (mSampleRates != null && mSampleRates.length == 1) {
                format.setInteger(MediaFormat.KEY_SAMPLE_RATE, mSampleRates[0]);
            }
            if (mMaxChannels == 1) format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        }

        /** package-private: called from CodecCapabilities.isFormatSupported. */
        boolean supportsFormat(MediaFormat format) {
            Map<String, Object> map = format.getMap();
            Integer sampleRate = (Integer) map.get(MediaFormat.KEY_SAMPLE_RATE);
            Integer channels = (Integer) map.get(MediaFormat.KEY_CHANNEL_COUNT);
            if (!supports(sampleRate, channels)) return false;
            return CodecCapabilities.supportsBitrate(mBitrateRange, format);
        }
    }

    /** AOSP frameworks/base .../MediaCodecInfo.java VideoCapabilities, same
     *  reduced-but-real-contract treatment as AudioCapabilities above. No
     *  registry entry populates this today (see MediaCodecList's video
     *  comment) -- every method here is exercised the moment one does,
     *  without another AOSP round-trip. */
    public static final class VideoCapabilities {
        private Range<Integer> mWidthRange;
        private Range<Integer> mHeightRange;
        private Range<Integer> mFrameRateRange;
        private int mWidthAlignment = 2;    // 2 = universal 4:2:0 chroma alignment
        private int mHeightAlignment = 2;
        /** 0 = no area bound (the pre-probe registry); see HostCodecCapability.maxMacroblocks. */
        private int mMaxMacroblocks;
        private Range<Integer> mBitrateRange = CodecCapabilities.DEFAULT_BITRATE_RANGE;

        private VideoCapabilities() {}

        static VideoCapabilities fromHostCapability(HostCodecCapability cap) {
            VideoCapabilities vc = new VideoCapabilities();
            vc.mWidthRange = cap.widthRange;
            vc.mHeightRange = cap.heightRange;
            vc.mFrameRateRange = cap.frameRateRange;
            if (cap.widthAlignment > 0) vc.mWidthAlignment = cap.widthAlignment;
            if (cap.heightAlignment > 0) vc.mHeightAlignment = cap.heightAlignment;
            vc.mMaxMacroblocks = cap.maxMacroblocks;
            if (cap.bitrateRange != null) vc.mBitrateRange = cap.bitrateRange;
            return vc;
        }

        public int getWidthAlignment() { return mWidthAlignment; }
        public int getHeightAlignment() { return mHeightAlignment; }
        public Range<Integer> getSupportedWidths() { return mWidthRange; }
        public Range<Integer> getSupportedHeights() { return mHeightRange; }
        public Range<Integer> getBitrateRange() { return mBitrateRange; }
        /** AOSP declares this Range<Integer> (frames per second); the two
         *  *For() overloads below are the Range<Double> ones AOSP uses for a
         *  specific size. */
        public Range<Integer> getSupportedFrameRates() { return mFrameRateRange; }

        private static boolean aligned(int v, int alignment) { return alignment <= 1 || (v % alignment) == 0; }

        public boolean isSizeSupported(int width, int height) {
            if (mWidthRange == null || mHeightRange == null) return false;
            if (!(mWidthRange.contains(width) && mHeightRange.contains(height)
                    && aligned(width, mWidthAlignment) && aligned(height, mHeightAlignment))) {
                return false;
            }
            // AOSP isSizeSupported also bounds the frame AREA in blocks
            // (mBlockCountRange.contains(blockCount)).
            if (mMaxMacroblocks > 0) {
                long blocks = (long) ((width + 15) / 16) * ((height + 15) / 16);
                if (blocks > mMaxMacroblocks) return false;
            }
            return true;
        }
        public boolean areSizeAndRateSupported(int width, int height, double frameRate) {
            if (!isSizeSupported(width, height)) return false;
            if (mFrameRateRange == null) return frameRate > 0;
            return frameRate >= mFrameRateRange.getLower() - 0.001
                    && frameRate <= mFrameRateRange.getUpper() + 0.001;
        }
        public Range<Integer> getSupportedWidthsFor(int height) {
            if (mHeightRange == null || !mHeightRange.contains(height) || !aligned(height, mHeightAlignment)) return null;
            return narrowByBlockCount(mWidthRange, height);
        }
        public Range<Integer> getSupportedHeightsFor(int width) {
            if (mWidthRange == null || !mWidthRange.contains(width) || !aligned(width, mWidthAlignment)) return null;
            return narrowByBlockCount(mHeightRange, width);
        }
        /** AOSP getSupportedWidthsFor/HeightsFor narrow the range by the block
         *  count: maxWidthInBlocks = min(mBlockCountRange.getUpper() /
         *  heightInBlocks, ...), range.intersect(..., maxWidthInBlocks *
         *  mBlockWidth). Here the only bound is the probed area
         *  (mMaxMacroblocks, 16x16 blocks), so a size that isSizeSupported
         *  rejects for its AREA is not offered by the *For() range either.
         *  null when the other dimension alone already exceeds the bound
         *  (AOSP throws IllegalArgumentException there; this class answers
         *  null for an unsupported other dimension, see above). */
        private Range<Integer> narrowByBlockCount(Range<Integer> range, int otherDim) {
            if (mMaxMacroblocks <= 0) return range;
            int otherInBlocks = (otherDim + 15) / 16;
            long upper = (long) (mMaxMacroblocks / otherInBlocks) * 16;
            if (upper >= range.getUpper()) return range;
            if (upper < range.getLower()) return null;
            return new Range<Integer>(range.getLower(), (int) upper);
        }
        public Range<Double> getSupportedFrameRatesFor(int width, int height) {
            if (!isSizeSupported(width, height) || mFrameRateRange == null) return null;
            return new Range<Double>((double) mFrameRateRange.getLower(), (double) mFrameRateRange.getUpper());
        }
        /** AOSP derives this from a real per-size measured/estimated table
         *  (NPP-based modeling of encode/decode cost); we have no such
         *  profile for a host codec, so this returns the same declared range
         *  as getSupportedFrameRatesFor -- an honest "we don't know the
         *  achievable rate independent of the declared one" rather than a
         *  fabricated estimate. */
        public Range<Double> getAchievableFrameRatesFor(int width, int height) {
            return getSupportedFrameRatesFor(width, height);
        }
        public java.util.List<PerformancePoint> getSupportedPerformancePoints() {
            return java.util.Collections.emptyList();
        }

        boolean supportsFormat(MediaFormat format) {
            Map<String, Object> map = format.getMap();
            Integer width = (Integer) map.get(MediaFormat.KEY_WIDTH);
            Integer height = (Integer) map.get(MediaFormat.KEY_HEIGHT);
            if (width != null && height != null && !isSizeSupported(width, height)) return false;
            Object frObj = map.get(MediaFormat.KEY_FRAME_RATE);
            if (frObj instanceof Number && mFrameRateRange != null) {
                double fr = ((Number) frObj).doubleValue();
                if (fr > 0 && (fr < mFrameRateRange.getLower() - 0.001 || fr > mFrameRateRange.getUpper() + 0.001)) return false;
            }
            return CodecCapabilities.supportsBitrate(mBitrateRange, format);
        }

        /** A (width, height, frameRate) capability point, in MACROBLOCKS (16px
         *  units) per AOSP's own field comments. covers() asks whether this
         *  point can serve another -- used by apps to check "can this decoder
         *  do 1080p60" against a list this registry does not populate yet, so
         *  the comparison-only semantics (no real per-size cost table) are
         *  the honest answer available. Source: frameworks/base
         *  .../MediaCodecInfo.java (PerformancePoint). */
        public static final class PerformancePoint {
            private final int mWidth;
            private final int mHeight;
            private final int mFrameRate;
            public PerformancePoint(int width, int height, int frameRate) {
                mWidth = width; mHeight = height; mFrameRate = frameRate;
            }
            public int getMaxFrameRate() { return mFrameRate; }
            public long getMaxMacroBlockRate() {
                return (long) ((mWidth / 16) * (mHeight / 16)) * mFrameRate;
            }
            public boolean covers(PerformancePoint other) {
                if (other == null) return false;
                return mWidth >= other.mWidth && mHeight >= other.mHeight && mFrameRate >= other.mFrameRate;
            }

            /** AOSP: "@return whether the performance point covers a media
             *  format", built from the format's width/height/frame-rate keys
             *  (MediaCodecInfo.java, PerformancePoint.covers(MediaFormat)). */
            public boolean covers(MediaFormat format) {
                if (format == null) return false;
                int w = intKey(format, MediaFormat.KEY_WIDTH, 0);
                int h = intKey(format, MediaFormat.KEY_HEIGHT, 0);
                int fr = intKey(format, MediaFormat.KEY_FRAME_RATE, 0);
                return covers(new PerformancePoint(w, h, fr));
            }

            private static int intKey(MediaFormat format, String key, int fallback) {
                try {
                    if (format.containsKey(key)) return format.getInteger(key);
                } catch (Throwable ignore) {
                    // KEY_FRAME_RATE is legally a float on a real device; fall
                    // through to the float read rather than failing the query.
                }
                try {
                    if (format.containsKey(key)) return (int) format.getFloat(key);
                } catch (Throwable ignore) { }
                return fallback;
            }

            @Override public boolean equals(Object obj) {
                if (this == obj) return true;
                if (!(obj instanceof PerformancePoint)) return false;
                PerformancePoint o = (PerformancePoint) obj;
                return mWidth == o.mWidth && mHeight == o.mHeight && mFrameRate == o.mFrameRate;
            }
            @Override public int hashCode() {
                return (mWidth * 31 + mHeight) * 31 + mFrameRate;
            }
            @Override public String toString() { return mWidth + "x" + mHeight + "@" + mFrameRate; }

            // The 33 standard performance points AOSP publishes as constants
            // (MediaCodecInfo.java, PerformancePoint SD_*/HD_*/FHD_*/UHD_*).
            // These are pure DEFINITIONS of a resolution/frame-rate pair -- SD
            // is 720x480, HD is 1280x720, FHD is 1920x1080, UHD is 3840x2160 --
            // so their values are the same on every device and carry no claim
            // about what this host can decode. Apps use them as the argument to
            // VideoCapabilities.areSizeAndRateSupported / covers(), i.e. as the
            // QUESTION, and the answer still comes from this host's real
            // capability table.
            public static final PerformancePoint SD_24 = new PerformancePoint(720, 480, 24);
            public static final PerformancePoint SD_25 = new PerformancePoint(720, 576, 25);
            public static final PerformancePoint SD_30 = new PerformancePoint(720, 480, 30);
            public static final PerformancePoint SD_48 = new PerformancePoint(720, 480, 48);
            public static final PerformancePoint SD_50 = new PerformancePoint(720, 576, 50);
            public static final PerformancePoint SD_60 = new PerformancePoint(720, 480, 60);
            public static final PerformancePoint HD_24 = new PerformancePoint(1280, 720, 24);
            public static final PerformancePoint HD_25 = new PerformancePoint(1280, 720, 25);
            public static final PerformancePoint HD_30 = new PerformancePoint(1280, 720, 30);
            public static final PerformancePoint HD_50 = new PerformancePoint(1280, 720, 50);
            public static final PerformancePoint HD_60 = new PerformancePoint(1280, 720, 60);
            public static final PerformancePoint HD_100 = new PerformancePoint(1280, 720, 100);
            public static final PerformancePoint HD_120 = new PerformancePoint(1280, 720, 120);
            public static final PerformancePoint HD_200 = new PerformancePoint(1280, 720, 200);
            public static final PerformancePoint HD_240 = new PerformancePoint(1280, 720, 240);
            public static final PerformancePoint FHD_24 = new PerformancePoint(1920, 1080, 24);
            public static final PerformancePoint FHD_25 = new PerformancePoint(1920, 1080, 25);
            public static final PerformancePoint FHD_30 = new PerformancePoint(1920, 1080, 30);
            public static final PerformancePoint FHD_50 = new PerformancePoint(1920, 1080, 50);
            public static final PerformancePoint FHD_60 = new PerformancePoint(1920, 1080, 60);
            public static final PerformancePoint FHD_100 = new PerformancePoint(1920, 1080, 100);
            public static final PerformancePoint FHD_120 = new PerformancePoint(1920, 1080, 120);
            public static final PerformancePoint FHD_200 = new PerformancePoint(1920, 1080, 200);
            public static final PerformancePoint FHD_240 = new PerformancePoint(1920, 1080, 240);
            public static final PerformancePoint UHD_24 = new PerformancePoint(3840, 2160, 24);
            public static final PerformancePoint UHD_25 = new PerformancePoint(3840, 2160, 25);
            public static final PerformancePoint UHD_30 = new PerformancePoint(3840, 2160, 30);
            public static final PerformancePoint UHD_50 = new PerformancePoint(3840, 2160, 50);
            public static final PerformancePoint UHD_60 = new PerformancePoint(3840, 2160, 60);
            public static final PerformancePoint UHD_100 = new PerformancePoint(3840, 2160, 100);
            public static final PerformancePoint UHD_120 = new PerformancePoint(3840, 2160, 120);
            public static final PerformancePoint UHD_200 = new PerformancePoint(3840, 2160, 200);
            public static final PerformancePoint UHD_240 = new PerformancePoint(3840, 2160, 240);
        }
    }

    /** Encoding is entirely out of scope (see CodecCapabilities.mEncoderCaps
     *  above) -- this class exists only so a signature that references it
     *  resolves. AOSP-shaped for when/if an encoder ever exists. */
    public static final class EncoderCapabilities {
        public Range<Integer> getQualityRange() { return null; }
        public Range<Integer> getComplexityRange() { return null; }
        public boolean isBitrateModeSupported(int mode) { return false; }
    }
}
