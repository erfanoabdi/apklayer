// android.media.MediaCodecList -- enumerates installed codec components. AOSP
// frameworks/base media/java/android/media/MediaCodecList.java.
//
// THE BUG THIS FIXES: getCodecInfos() used to return an empty array
// unconditionally. ExoPlayer/media3 (and the NDK AMediaCodec surface, and
// anything else that follows the standard "ask the platform which codec
// handles this format" idiom) selects a decoder by walking MediaCodecList
// BEFORE attempting to open one -- an empty list means such an app concludes
// this device has no codecs at all, and never even tries. Measured on
// PlayTube (ExoPlayer/media3): player UI up, timeline stuck at 0:00/0:00,
// ExoPlayer:Playback spinning past dispatch #2200 with zero exceptions --
// consistent with track selection never resolving a supported format.
//
// THE FIX: getCodecInfos()/findDecoderForFormat()/findEncoderForFormat() now
// answer from a REAL registry (below), built once from every registered
// HostCodecProvider's HostCodecCapability data -- see both classes' doc
// comments for the full contract. This is NOT a hardcoded list: it is the
// single place "does this host actually decode X" data becomes a
// MediaCodecInfo, so getCodecInfos(), find*ForFormat() and
// MediaCodec.getCodecInfo() (MediaCodecInfo.forName -> findByName below)
// can never disagree with each other about what is available.
//
// WHAT IS REGISTERED TODAY: VerifiedAudioDecoders (bottom of this file) --
// AAC (audio/mp4a-latm) and MPEG/MP3 (audio/mpeg) decode, because those are
// the only two mimes MediaCodec.configure()/start() actually opens a working
// native decoder for right now (MediaCodec.java, mFormatId 1/2 ->
// com.macapk.loader.NativeMedia -> AudioToolbox AudioConverter -- real,
// verified, not hypothetical). Sample-rate/bitrate/channel numbers are
// AOSP's OWN platform-default table for these two mimes
// (frameworks/base media/java/android/media/MediaCodecInfo.java,
// AudioCapabilities.applyLevelLimits(), fetched 2026-08-16), used here
// because AudioConverter decode is bounded by the AAC/MP3 bitstream formats'
// own defined sample-rate tables, not by an Apple-specific narrower limit --
// media_decode.mm passes sampleRate/channels straight from the source
// format into AudioConverterNew with no clamping of its own.
// isHardwareAccelerated() is FALSE for both: this is an explicit, considered
// answer, not a placeholder -- no dedicated Apple Silicon audio-decode ASIC
// is documented (unlike the video decode engine), so AudioConverter's AAC/
// MP3 decode is treated as CPU-side software absent a real measurement. If a
// future session gets a live readback (there is no per-codec audio
// equivalent of VTDecompressionSession's hardware-usage property that this
// lane found), replace this comment's reasoning, not just the boolean.
//
// VIDEO: registered too, as of the same session -- VideoToolboxDecoders
// (bottom of this file) reads com.macapk.loader.NativeMedia.
// nativeVideoSupportedMimes()/nativeVideoIsHardware(mime), the exact two
// methods that class's own doc comment says this lane is expected to read,
// backed by runtime/src/video_toolbox.mm (real VTDecompressionSession decode,
// wired all the way through MediaCodec.java's configure()/start()/
// dequeue*/releaseOutputBuffer -- not the older MACAPK_VIDEO_SELFTEST
// diagnostic path, which only ever decoded a fixed file to a debug overlay
// and is unrelated to this). Before that native surface existed, this file
// deliberately left video unregistered: tracing MediaCodec.java as it stood
// then, start() allocated real input buffers regardless of whether a decoder
// actually opened, so a listed-but-unopenable video codec would have made
// dequeueInputBuffer keep succeeding while queueInputBuffer silently dropped
// every access unit and dequeueOutputBuffer spun on TRY_AGAIN_LATER forever
// -- no exception, the PlayTube symptom reproduced one layer deeper and
// harder to diagnose. MediaCodec.java's start() now throws a loud
// CodecException if a mime this file advertised fails to actually open
// (its own comment: "this class must not reproduce that shape for a codec
// it claimed to support"), which is what makes listing a real video codec
// here safe rather than a repeat of that failure mode.
//
// ⚠ CORRECTED 2026-08-17: this comment used to end here with "VP8 correctly
// never appears: VideoToolbox has no VP8 decoder on any macOS version" --
// true of VideoToolboxDecoders specifically (see its own doc below) but
// wrong as a claim about this REGISTRY, and it was stale the moment it was
// written: v1.0.984 (same day) had already landed VP8 as a real, working
// libvpx decode path in MediaCodec.java (isVideoMimeSupported/isVp8/
// safeVp8Open), so its total absence from getCodecInfos() was a live gap,
// not a correct answer -- exactly the class of bug this file's own "THE BUG
// THIS FIXES" section describes for the pre-rewrite empty-array case,
// recurring one layer down: an app that walks MediaCodecList BEFORE calling
// MediaCodec.createDecoderByType (ExoPlayer's MediaCodecUtil.
// getDecoderInfos does exactly this) would conclude VP8 does not exist and
// never try it, even though start() could open a real one. LibvpxDecoders
// (bottom of this file) now closes it for VP8 and its VP9 sibling
// (runtime/src/video_vp9_decode.mm) together, found and fixed in the same
// session that added VP9 -- see that provider's own doc for why it is a
// separate provider rather than a VideoToolboxDecoders special-case.
package android.media;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MediaCodecList {
    public static final int REGULAR_CODECS = 0;
    public static final int ALL_CODECS = 1;

    // ---- The registry: providers in, MediaCodecInfo[] out ----------------

    private static final List<HostCodecProvider> sProviders = new CopyOnWriteArrayList<HostCodecProvider>();
    private static volatile MediaCodecInfo[] sCached;

    static {
        // Registered through the exact same seam any future provider uses
        // (see the class doc) rather than special-cased here, so there is
        // exactly one code path from "data" to a MediaCodecInfo.
        registerHostCodecProvider(new VerifiedAudioDecoders());
        registerHostCodecProvider(new VideoToolboxDecoders());
        registerHostCodecProvider(new LibvpxDecoders());
    }

    /**
     * Registers a source of REAL, measured host codec capability. Safe to
     * call at any time (before or after MediaCodecList has been read) --
     * the registry rebuilds lazily on next read rather than depending on
     * registration happening before some fixed point. THIS is the seam:
     * add a codec by constructing HostCodecCapability data from a real
     * probe and calling this, not by editing buildCodecInfos() or
     * hand-building a MediaCodecInfo.
     */
    public static void registerHostCodecProvider(HostCodecProvider provider) {
        if (provider == null) return;
        sProviders.add(provider);
        sCached = null;   // invalidate; next read rebuilds
    }

    private static synchronized MediaCodecInfo[] allCodecInfos() {
        MediaCodecInfo[] cached = sCached;
        if (cached != null) return cached;
        List<MediaCodecInfo> out = new ArrayList<MediaCodecInfo>();
        for (HostCodecProvider p : sProviders) {
            List<HostCodecCapability> caps;
            try {
                caps = p.getHostCodecCapabilities();
            } catch (RuntimeException | LinkageError e) {
                // One misbehaving provider (a probe that threw, or whose
                // native half is not linked -- UnsatisfiedLinkError is a
                // LinkageError, not a RuntimeException) must not blank out
                // every other provider's real answers.
                continue;
            }
            if (caps == null) continue;
            for (HostCodecCapability c : caps) {
                if (c == null) continue;
                out.add(MediaCodecInfo.fromHostCapability(c));
            }
        }
        MediaCodecInfo[] arr = out.toArray(new MediaCodecInfo[out.size()]);
        sCached = arr;
        return arr;
    }

    /** Used by MediaCodecInfo.forName (MediaCodec.getCodecInfo()'s path) to
     *  resolve a codec name to its real registry entry. Package-private. */
    static MediaCodecInfo findByName(String name) {
        if (name == null) return null;
        for (MediaCodecInfo info : allCodecInfos()) {
            if (info.getName().equals(name)) return info;
        }
        return null;
    }

    // ---- Instance API (AOSP shape) ----------------------------------------

    private final MediaCodecInfo[] mCodecInfos;

    /**
     * @param kind Either {@link #REGULAR_CODECS} or {@link #ALL_CODECS}. AOSP
     *     distinguishes these by excluding secure-/tunneled-only codecs from
     *     REGULAR_CODECS. Mac.apk registers neither kind (no DRM, no tunneled
     *     pipeline -- see MediaCodecInfo.CodecCapabilities' FEATURE_ comment),
     *     so both kinds are always the identical set here.
     */
    public MediaCodecList(int kind) {
        mCodecInfos = allCodecInfos();
    }

    public MediaCodecInfo[] getCodecInfos() { return Arrays.copyOf(mCodecInfos, mCodecInfos.length); }

    /** AOSP: "the name of a decoder that supports the given format and feature
     *  requests, or null if no such codec has been found". Walks the real
     *  registry exactly as AOSP walks its component table: first non-encoder
     *  whose CodecCapabilities.isFormatSupported(format) holds. The returned
     *  name is the exact one createDecoderByType(mime) produces (see
     *  MediaCodecInfo.fromHostCapability), so createByCodecName(name) yields
     *  the same working codec. Throws NPE on a null format, matching AOSP
     *  (findCodecForFormat dereferences format immediately). */
    public String findDecoderForFormat(MediaFormat format) { return findCodecForFormat(false, format); }

    /** No encoder is ever registered (MediaCodec's native backing is
     *  decode-only -- see MediaCodec.java's header comment), so AOSP's "or
     *  null if no such codec has been found" is the whole answer -- but the
     *  format is still dereferenced first so a null argument raises the same
     *  NullPointerException AOSP raises. */
    public String findEncoderForFormat(MediaFormat format) { return findCodecForFormat(true, format); }

    private String findCodecForFormat(boolean encoder, MediaFormat format) {
        String mime = format.getString(MediaFormat.KEY_MIME);
        for (MediaCodecInfo info : mCodecInfos) {
            if (info.isEncoder() != encoder) continue;
            MediaCodecInfo.CodecCapabilities caps;
            try {
                caps = info.getCapabilitiesForType(mime);
            } catch (IllegalArgumentException e) {
                continue;   // this codec does not declare that mime
            }
            if (caps.isFormatSupported(format)) return info.getName();
        }
        return null;
    }

    // Deprecated static API (pre-API-21), backed by the same real registry.
    public static int getCodecCount() { return allCodecInfos().length; }
    public static MediaCodecInfo getCodecInfoAt(int index) {
        MediaCodecInfo[] all = allCodecInfos();
        if (index < 0 || index >= all.length) {
            throw new IllegalArgumentException("no codec at index " + index);
        }
        return all[index];
    }

    // ---- The verified-real AUDIO truth, contributed through the same ----
    // ---- seam any other provider would use.                          ----

    /** All 13 formats MediaCodec.configure()/start() can actually open today
     *  (mFormatId 1-13 -> NativeMedia -> AudioToolbox AudioConverter, or for
     *  Vorbis, stb_vorbis -- see MediaCodec.java's configure() and
     *  docs/audiotoolbox-codec-widening_2026-08-16.md). See the class doc
     *  for the AAC/MPEG sample-rate/bitrate table provenance and the
     *  isHardwareAccelerated=false reasoning, which applies identically to
     *  every entry below (same AudioConverter, or for Vorbis a pure-software
     *  vendored decoder -- no case here is hardware).
     *
     *  PRECISION NOTE for the 11 entries added alongside AAC/MPEG (2026-08):
     *  unlike AAC/MPEG's table (AOSP's own documented platform defaults),
     *  these sample-rate/channel/bitrate bounds are each format's OWN
     *  spec-defined limit (ATSC A/52 for AC3/EAC3, 3GPP TS 26.101/26.201 for
     *  AMR-NB/WB, ITU-T G.711 for the two law codecs, the FLAC/Vorbis/ALAC
     *  format specs), not a live per-rate AudioConverter probe on this host
     *  -- the same category of reasoning the AAC entry already uses
     *  ("bounded by the bitstream format's own defined sample-rate tables,
     *  not an Apple-specific narrower limit"). Where a format is genuinely
     *  variable-bitrate with no meaningful spec ceiling (FLAC, ALAC, RAW,
     *  Vorbis, Opus), bitrateRange is null -- per HostCodecCapability's own
     *  doc, null is never used to REJECT a format, only AAC/MPEG-style
     *  "unknown/unbounded", so leaving it null there is the honest choice,
     *  not a shortcut. MediaCodec.start() throws CodecException if
     *  nativeDecOpen fails for any mime this registry claims (see that
     *  method's "Loud, not silent" comment, added for exactly this
     *  widening), so an optimistic bound here fails safely rather than
     *  silently, the same safety net the video registry's own doc relies on
     *  for its resolution bounds. */
    private static final class VerifiedAudioDecoders implements HostCodecProvider {
        public List<HostCodecCapability> getHostCodecCapabilities() {
            List<HostCodecCapability> out = new ArrayList<HostCodecCapability>(13);
            out.add(HostCodecCapability.audioDecoder(
                    MediaFormat.MIMETYPE_AUDIO_AAC,
                    false /* hardwareAccelerated: see class doc */,
                    new int[] { 7350, 8000, 11025, 12000, 16000, 22050, 24000,
                                32000, 44100, 48000, 64000, 88200, 96000 },
                    null, 48,
                    new android.util.Range<Integer>(8000, 510000),
                    new MediaCodecInfo.CodecProfileLevel[] {
                        // AAC-LC is what our decode path is proven against end
                        // to end. AudioConverter's kAudioFormatMPEG4AAC decoder
                        // is documented by Apple to also transparently handle
                        // HE-AAC/HE-AAC-v2 (SBR/PS) via the magic cookie -- and
                        // media_decode.mm's demuxer already special-cases the
                        // HE-AAC container format id -- but that path has not
                        // been verified live this session, so only the
                        // demonstrably-working profile is advertised here.
                        new MediaCodecInfo.CodecProfileLevel(
                                MediaCodecInfo.CodecProfileLevel.AACObjectLC, 0)
                    }));
            out.add(HostCodecCapability.audioDecoder(
                    MediaFormat.MIMETYPE_AUDIO_MPEG,
                    false /* hardwareAccelerated: see class doc */,
                    new int[] { 8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000 },
                    null, 2,
                    new android.util.Range<Integer>(8000, 320000),
                    new MediaCodecInfo.CodecProfileLevel[0]   // MP3 has no profile/level concept
            ));
            // Opus (RFC 6716): the decoder ASBD is forced to 48000 Hz
            // regardless of the caller's declared rate -- section 3a of the
            // widening doc -- so 48000 is the only rate actually decoded,
            // not an under-claim. Channel count kept at the verified stereo
            // case (RFC 6716 allows up to 255 but only mono/stereo has been
            // exercised end to end).
            out.add(HostCodecCapability.audioDecoder(
                    MediaFormat.MIMETYPE_AUDIO_OPUS,
                    false, new int[] { 48000 }, null, 2,
                    null /* VBR, no spec ceiling */,
                    new MediaCodecInfo.CodecProfileLevel[0]));
            // FLAC: format spec allows up to 8 channels and a 20-bit sample
            // rate field (max 655350 Hz); bounded here to the commonly
            // encoded 8 kHz-192 kHz span rather than the theoretical max.
            out.add(HostCodecCapability.audioDecoder(
                    MediaFormat.MIMETYPE_AUDIO_FLAC,
                    false, null, new android.util.Range<Integer>(8000, 192000), 8,
                    null /* lossless: bitrate is a function of content, no ceiling */,
                    new MediaCodecInfo.CodecProfileLevel[0]));
            // ALAC (Apple Lossless): same practical envelope as FLAC (up to
            // 8 channels, 8 kHz-192 kHz) -- the two share the same lossless
            // PCM-container heritage and QuickTime's own ALAC muxer limits.
            out.add(HostCodecCapability.audioDecoder(
                    MediaFormat.MIMETYPE_AUDIO_ALAC,
                    false, null, new android.util.Range<Integer>(8000, 192000), 8,
                    null, new MediaCodecInfo.CodecProfileLevel[0]));
            // AC3 (Dolby Digital, ATSC A/52): exactly 3 defined sample
            // rates, up to 5.1 (6ch), 32-640 kbps per the spec's bitstream
            // rate table.
            out.add(HostCodecCapability.audioDecoder(
                    MediaFormat.MIMETYPE_AUDIO_AC3,
                    false, new int[] { 32000, 44100, 48000 }, null, 6,
                    new android.util.Range<Integer>(32000, 640000),
                    new MediaCodecInfo.CodecProfileLevel[0]));
            // EAC3 (Dolby Digital Plus): same 3 base rates as AC3, up to 7.1
            // (8ch) with channel extension, bitrate ceiling per the Dolby
            // spec's highest defined bitstream rate.
            out.add(HostCodecCapability.audioDecoder(
                    MediaFormat.MIMETYPE_AUDIO_EAC3,
                    false, new int[] { 32000, 44100, 48000 }, null, 8,
                    new android.util.Range<Integer>(32000, 3024000),
                    new MediaCodecInfo.CodecProfileLevel[0]));
            // AMR-NB (3GPP TS 26.071/26.101): spec-fixed 8000 Hz mono (see
            // media_decode.mm section 3c -- the ASBD is forced regardless of
            // the caller's declared rate), 8 codec modes from 4.75 to
            // 12.2 kbps.
            out.add(HostCodecCapability.audioDecoder(
                    MediaFormat.MIMETYPE_AUDIO_AMR_NB,
                    false, new int[] { 8000 }, null, 1,
                    new android.util.Range<Integer>(4750, 12200),
                    new MediaCodecInfo.CodecProfileLevel[0]));
            // AMR-WB (3GPP TS 26.171/26.201): spec-fixed 16000 Hz mono, 9
            // codec modes from 6.6 to 23.85 kbps.
            out.add(HostCodecCapability.audioDecoder(
                    MediaFormat.MIMETYPE_AUDIO_AMR_WB,
                    false, new int[] { 16000 }, null, 1,
                    new android.util.Range<Integer>(6600, 23850),
                    new MediaCodecInfo.CodecProfileLevel[0]));
            // G.711 A-law / u-law (ITU-T G.711): telephone-quality companded
            // PCM, canonically 8000 Hz; media_decode.mm's byte-CBR path
            // (mBytesPerPacket = channel count) is channel-flexible so this
            // does not hardcode mono the way AMR's forced ASBD does.
            // Bitrate is exactly 8 bits/sample * 8000 Hz = 64000 bps per
            // channel, fixed by the companding scheme, not a range.
            out.add(HostCodecCapability.audioDecoder(
                    MediaFormat.MIMETYPE_AUDIO_G711_ALAW,
                    false, new int[] { 8000 }, null, 2,
                    new android.util.Range<Integer>(64000, 64000),
                    new MediaCodecInfo.CodecProfileLevel[0]));
            out.add(HostCodecCapability.audioDecoder(
                    MediaFormat.MIMETYPE_AUDIO_G711_MLAW,
                    false, new int[] { 8000 }, null, 2,
                    new android.util.Range<Integer>(64000, 64000),
                    new MediaCodecInfo.CodecProfileLevel[0]));
            // Raw PCM: memcpy passthrough (media_decode.mm bypasses
            // AudioConverter entirely for formatId 12), so there is no
            // codec-imposed limit at all -- the wide span here is the
            // honest "we do not constrain this" answer, not an optimistic
            // guess about a real codec's envelope.
            out.add(HostCodecCapability.audioDecoder(
                    MediaFormat.MIMETYPE_AUDIO_RAW,
                    false, null, new android.util.Range<Integer>(1, 192000), 8,
                    null, new MediaCodecInfo.CodecProfileLevel[0]));
            // Vorbis (via the vendored stb_vorbis, not AudioConverter -- see
            // section 5 of the widening doc): the format spec defines up to
            // 255 channels and an arbitrary sample rate; bounded here to the
            // commonly encoded envelope rather than the theoretical max, the
            // same judgment call FLAC/ALAC above make.
            out.add(HostCodecCapability.audioDecoder(
                    MediaFormat.MIMETYPE_AUDIO_VORBIS,
                    false, null, new android.util.Range<Integer>(8000, 192000), 8,
                    null, new MediaCodecInfo.CodecProfileLevel[0]));
            return out;
        }
    }

    // ---- The verified-real VIDEO truth, contributed through the same ----
    // ---- seam -- built on the video lane's own probe, not invented here. --

    /** H.264/HEVC/VP9/AV1/MPEG-4/MPEG-2/H.263 decode via VideoToolbox
     *  (runtime/src/video_toolbox.mm), read through exactly the two
     *  NativeMedia methods that file's own doc comment names for this
     *  purpose -- nativeVideoSupportedMimes() for the mime set,
     *  nativeVideoIsHardware(mime) for the per-codec hardware/software
     *  answer (a real kVTDecompressionPropertyKey_UsingHardwareAcceleratedVideoDecoder
     *  readback after a trial session create, per that file's header
     *  comment -- not requested-and-assumed). VP8/VP9 never appear here
     *  because nativeVideoSupportedMimes() never reports them (no
     *  VideoToolbox decoder exists for either on any macOS version -- VP9
     *  is probed and comes back OSStatus -12906, genuine absence, see
     *  docs/videotoolbox-probe-audit_2026-08-16.md section 3), so there is
     *  nothing to filter FOR THIS PROVIDER. They are not missing from the
     *  overall registry: LibvpxDecoders below adds them through the same
     *  seam, backed by a different native file. Two providers, not one
     *  provider with a special case, because they answer to genuinely
     *  different probes (a live per-host VideoToolbox capability query vs.
     *  an unconditional "libvpx is statically linked, so it is always
     *  here") -- collapsing them would make VideoToolboxDecoders' own
     *  "everything here came from nativeVideoSupportedMimes()" invariant
     *  false.
     *
     *  Width/height/frame-rate ranges are a DECLARED APPROXIMATION, not a
     *  per-codec measurement: unlike "is this mime decodable" and "is it
     *  hardware", VideoToolbox exposes no queryable maximum resolution or
     *  frame rate per codec type. 4096x4096 is the universally-supported
     *  H.264 hardware decode bound and is very likely an UNDER-claim for
     *  HEVC/AV1 on Apple Silicon (which typically reaches 8K) -- never an
     *  OVER-claim, which is the safe direction: if VTDecompressionSessionCreate
     *  itself rejects a format this range approved, MediaCodec.start() now
     *  throws a loud CodecException rather than silently swallowing (see its
     *  "Loud, not silent" comment), so an optimistic bound fails safely.
     *  profileLevels: ⚠ CORRECTED 2026-09-30. This used to be left EMPTY
     *  for every entry on the theory that an empty table is "permissive".
     *  It is not, for the apps that matter: ExoPlayer's
     *  MediaCodecInfo.isCodecProfileAndLevelSupported looks for a matching
     *  (profile, level >= stream level) pair and an empty table matches
     *  nothing, so every AVC/HEVC track was FORMAT_EXCEEDS_CAPABILITIES and
     *  the track selector fell back to the LOWEST quality (NewPipe: 144p).
     *  AVC and HEVC now carry a table MEASURED on this host by a real
     *  encode-then-decode round trip per profile (NativeMedia
     *  .nativeVideoCapabilities / runtime/src/video_caps_probe.mm, see
     *  videoToolboxEntry); the remaining mimes keep the empty table.
     *  Width/height then come from the largest frame that decoded, and
     *  MACAPK_CODEC_PROFILE_LEVELS=0 restores the old registry exactly. */
    private static final class VideoToolboxDecoders implements HostCodecProvider {
        // No VT property exposes this per codec (see class doc); one
        // conservative bound applied to every registered video mime.
        private static final android.util.Range<Integer> SIZE_RANGE = new android.util.Range<Integer>(2, 4096);
        private static final android.util.Range<Integer> FRAME_RATE_RANGE = new android.util.Range<Integer>(1, 240);

        public List<HostCodecCapability> getHostCodecCapabilities() {
            List<HostCodecCapability> out = new ArrayList<HostCodecCapability>();
            String[] mimes;
            try {
                mimes = com.macapk.loader.NativeMedia.nativeVideoSupportedMimes();
            } catch (RuntimeException | LinkageError e) {
                // Loud, not silent (CLAUDE.md: "missing shims are silent
                // killers") -- a stale-binary UnsatisfiedLinkError here looks
                // identical to a genuine regression from the app's point of
                // view (video mimes just aren't in getCodecInfos()), so this
                // needs to be visible in the runtime log rather than a
                // quietly-empty list with no trace of why.
                try {
                    com.macapk.loader.NativeLog.w("MediaCodecList",
                            "video codec probe unavailable (" + e.getClass().getSimpleName()
                            + ": " + e.getMessage() + ") -- reporting zero video decoders");
                } catch (Throwable ignored) {}
                return out;
            }
            if (mimes == null) return out;
            for (String mime : mimes) {
                if (mime == null || mime.isEmpty()) continue;
                boolean hw;
                try {
                    hw = com.macapk.loader.NativeMedia.nativeVideoIsHardware(mime);
                } catch (RuntimeException | LinkageError e) {
                    hw = false;   // could not measure: the honest default is software, never a guessed true
                }
                int[] probe = null;
                if (PROFILE_LEVELS) {
                    try {
                        probe = com.macapk.loader.NativeMedia.nativeVideoCapabilities(mime);
                    } catch (RuntimeException | LinkageError e) {
                        // Loud for the same reason as the mime probe above: a
                        // stale native half would otherwise put the empty
                        // (reject-everything) table back without a trace.
                        try {
                            com.macapk.loader.NativeLog.w("MediaCodecList",
                                    "profile/level probe unavailable for " + mime + " ("
                                    + e.getClass().getSimpleName() + ": " + e.getMessage()
                                    + ") -- registering it without a profile/level table");
                        } catch (Throwable ignored) {}
                    }
                }
                out.add(videoToolboxEntry(mime, hw, probe));
            }
            return out;
        }

        /** One VideoToolbox decoder entry. `probe` is NativeMedia
         *  .nativeVideoCapabilities' answer ({maxWidth, maxHeight,
         *  maxMacroblocks, nPairs, profile0, level0, ...}, every pair proven
         *  by a real encode-then-decode round trip on this host -- see
         *  runtime/src/video_caps_probe.mm), or null for a mime that has no
         *  per-profile probe (MPEG-2 / MPEG-4 Part 2 / H.263), which keeps
         *  the previous profile-less entry. Package-private and pure so the
         *  offline gate debug/vtcaps_gate.sh can drive it with the real
         *  probe's output and no runtime. */
        static HostCodecCapability videoToolboxEntry(String mime, boolean hw, int[] probe) {
            if (probe == null || probe.length < 4 || probe[3] <= 0
                    || probe.length < 4 + 2 * probe[3]) {
                return HostCodecCapability.videoDecoder(
                        mime, hw,
                        SIZE_RANGE, SIZE_RANGE, FRAME_RATE_RANGE,
                        2, 2,   // width/height alignment: universal 4:2:0 chroma bound
                        null,   // no measured bitrate ceiling either; leave unbounded rather than guessed
                        new MediaCodecInfo.CodecProfileLevel[0]);
            }
            MediaCodecInfo.CodecProfileLevel[] pls = new MediaCodecInfo.CodecProfileLevel[probe[3]];
            for (int i = 0; i < pls.length; i++) {
                pls[i] = new MediaCodecInfo.CodecProfileLevel(probe[4 + 2 * i], probe[5 + 2 * i]);
            }
            // The largest frame that DECODED bounds both dimensions (a
            // portrait stream is the same frame turned on its side), and its
            // macroblock count bounds the area -- AOSP VideoCapabilities
            // .isSizeSupported checks the block count the same way
            // (mBlockCountRange), so 4096x4096 is not "supported" just because
            // 4096 is a supported width and a supported height.
            int maxDim = Math.max(probe[0], probe[1]);
            android.util.Range<Integer> dims = maxDim >= 2
                    ? new android.util.Range<Integer>(2, maxDim) : SIZE_RANGE;
            return HostCodecCapability.videoDecoder(
                    mime, hw, dims, dims, FRAME_RATE_RANGE, 2, 2, null, pls, probe[2]);
        }
    }

    /** Whether video decoders carry a real profile/level table (default ON).
     *  MACAPK_CODEC_PROFILE_LEVELS=0 restores the previous registry byte for
     *  byte: every video decoder with an EMPTY profileLevels and the declared
     *  2-4096 size range -- which ExoPlayer reads as "supports no profile"
     *  (NewPipe then played its live kiosk at 144p). */
    static final boolean PROFILE_LEVELS =
            !"0".equals(System.getenv("MACAPK_CODEC_PROFILE_LEVELS"));

    // ---- The verified-real libvpx VIDEO truth (VP8 + VP9), a SEPARATE ----
    // ---- provider from VideoToolboxDecoders -- see that class's doc.    --

    /** VP8 (runtime/src/video_vp8_decode.mm) and VP9
     *  (runtime/src/video_vp9_decode.mm) decode via libvpx, statically
     *  linked -- unlike every VideoToolboxDecoders entry, availability here
     *  is NOT a per-host probe result, it is unconditional (the same fact
     *  MediaCodec.java's isVideoMimeSupported() already encodes for both
     *  mimes; this provider is the MediaCodecList-side mirror of that
     *  same truth, closing the gap described in this file's header
     *  comment's 2026-08-17 correction). hardwareAccelerated is FALSE for
     *  both, always: software decode, honestly reported, matching every
     *  other engine's isHardwareAccelerated reasoning in this file.
     *
     *  Width/height/frame-rate ranges: libvpx exposes no queryable per-
     *  codec maximum either (same situation VideoToolboxDecoders' own doc
     *  describes), so the SAME conservative 2-4096 / 1-240 bounds were
     *  reused rather than invented fresh -- an optimistic bound fails safe
     *  here too, via the identical start()-throws-CodecException net.
     *  With MACAPK_CODEC_PROFILE_LEVELS on (default) the SIZE is instead
     *  AOSP's published limit for these same decoders, 2..2048 with at most
     *  16384 16x16 blocks (see libvpxProfileLevels); =0 keeps 2-4096. */
    private static final class LibvpxDecoders implements HostCodecProvider {
        private static final String MIME_VP8 = "video/x-vnd.on2.vp8";
        private static final String MIME_VP9 = "video/x-vnd.on2.vp9";
        /** media_codecs_google_c2_video.xml: size max 2048x2048, block-count 1-16384. */
        private static final android.util.Range<Integer> AOSP_VPX_SIZE_RANGE =
                new android.util.Range<Integer>(2, 2048);
        private static final int AOSP_VPX_MAX_BLOCKS = 16384;

        public List<HostCodecCapability> getHostCodecCapabilities() {
            List<HostCodecCapability> out = new ArrayList<HostCodecCapability>(2);
            for (String mime : new String[] { MIME_VP8, MIME_VP9 }) {
                if (PROFILE_LEVELS) {
                    // AOSP's own declaration for this same library:
                    // size 2x2..2048x2048, block-count 1-16384 (see
                    // libvpxProfileLevels' doc for the source lines).
                    out.add(HostCodecCapability.videoDecoder(
                            mime, /*hardwareAccelerated=*/false,
                            AOSP_VPX_SIZE_RANGE, AOSP_VPX_SIZE_RANGE,
                            VideoToolboxDecoders.FRAME_RATE_RANGE,
                            2, 2,
                            null,
                            libvpxProfileLevels(mime),
                            AOSP_VPX_MAX_BLOCKS));
                    continue;
                }
                out.add(HostCodecCapability.videoDecoder(
                        mime, /*hardwareAccelerated=*/false,
                        VideoToolboxDecoders.SIZE_RANGE, VideoToolboxDecoders.SIZE_RANGE,
                        VideoToolboxDecoders.FRAME_RATE_RANGE,
                        2, 2,   // width/height alignment: universal 4:2:0 chroma bound
                        null,   // no bitrate ceiling measured; leave unbounded rather than guessed
                        new MediaCodecInfo.CodecProfileLevel[0]));
            }
            return out;
        }

        /** libvpx IS AOSP's own software VP8/VP9 decoder, so its table is
         *  AOSP's declaration for that same library rather than a probe:
         *  - VP9: frameworks/av media/codec2/components/vpx/C2SoftVpxDec.cpp
         *    declares profiles {PROFILE_VP9_0, PROFILE_VP9_2} at levels
         *    LEVEL_VP9_1 .. LEVEL_VP9_5 (its `inRange(2, 4096)` is only the
         *    component PARAMETER range). Codec2InfoBuilder lists one pair per
         *    profile at the highest level ("values.back()"), i.e. Level5.
         *    What an app SEES for c2.android.vp9.decoder / vp8.decoder is the
         *    media_codecs_google_c2_video.xml limit applied on top of that
         *    (frameworks/av media/libstagefright/data/, fetched 2026-09-30):
         *      vp9: Limit size min="2x2" max="2048x2048",
         *           block-count range="1-16384", blocks-per-second 1-500000
         *      vp8: Limit size min="2x2" max="2048x2048",
         *           block-count range="1-16384", blocks-per-second 1-1000000
         *    so software libvpx is NOT offered above 2048 wide/high or 16384
         *    16x16 blocks (a 3840x2160 VP9 track is FORMAT_EXCEEDS_CAPABILITIES
         *    and ExoPlayer picks a smaller one, as on AOSP). blocks-per-second
         *    cannot be expressed by HostCodecCapability yet (docs note OPEN).
         *    Profile 2 (10-bit) is NOT listed here: our libvpx output path
         *    (runtime/src/video_vp9_decode.mm) converts VPX_IMG_FMT_I420
         *    only and logs-and-skips a high-bitdepth image, so claiming it
         *    would hand ExoPlayer a 10-bit stream we drop frame by frame.
         *  - VP8: Codec2InfoBuilder adds exactly
         *    caps->addProfileLevel(VP8ProfileMain, VP8Level_Version0) for a
         *    VP8 decoder (VP8 has one profile). */
        static MediaCodecInfo.CodecProfileLevel[] libvpxProfileLevels(String mime) {
            if (MIME_VP9.equals(mime)) {
                return new MediaCodecInfo.CodecProfileLevel[] {
                    new MediaCodecInfo.CodecProfileLevel(
                            MediaCodecInfo.CodecProfileLevel.VP9Profile0,
                            MediaCodecInfo.CodecProfileLevel.VP9Level5),
                };
            }
            return new MediaCodecInfo.CodecProfileLevel[] {
                new MediaCodecInfo.CodecProfileLevel(
                        MediaCodecInfo.CodecProfileLevel.VP8ProfileMain,
                        MediaCodecInfo.CodecProfileLevel.VP8Level_Version0),
            };
        }
    }
}
