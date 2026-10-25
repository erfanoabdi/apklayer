// android.media.HostCodecCapability -- NOT an AOSP class. One MIME's worth of
// REAL, measured host decode/encode capability: the DATA unit a
// HostCodecProvider hands to MediaCodecList.registerHostCodecProvider(). See
// HostCodecProvider's class comment for the full rationale.
//
// Every field here is meant to be filled from an actual host query -- a live
// VTIsHardwareDecodeSupported/VTDecompressionSession probe, a live
// AudioConverter open, an AudioFormatGetProperty(kAudioFormatProperty_
// DecodeFormatIDs) enumeration -- never a guess and never copied from a spec
// sheet for a codec nothing on this host has actually opened. In particular
// `hardwareAccelerated` must come from a real per-codec readback: a wrong
// `true` here is a lie MediaCodecInfo.isHardwareAccelerated() will repeat to
// every app that asks, and some (WebRTC's HardwareVideoDecoderFactory is the
// concrete example already in this codebase's history) make routing
// decisions based on it.
//
// Two focused factories (audioDecoder / videoDecoder) instead of one
// telescoping constructor, because the audio and video fields are mutually
// exclusive per entry (an entry describes one MIME's decode path, and a MIME
// is audio or video, never both) and AOSP's own MediaCodecInfo.CodecCapabilities
// keeps the same split (getAudioCapabilities() / getVideoCapabilities(),
// exactly one non-null per real codec).
package android.media;

import android.util.Range;

public final class HostCodecCapability {
    public final String mime;
    public final boolean encoder;
    /** From a real per-codec hardware/software readback. Never assume true. */
    public final boolean hardwareAccelerated;

    // ---- audio (both null/0 for a video entry) ----
    /** Discrete supported sample rates (Hz), or null to use sampleRateRange
     *  instead. Mutually exclusive with sampleRateRange; set at most one. */
    public final int[] sampleRates;
    /** A continuous supported sample-rate span, or null if sampleRates (a
     *  discrete set) is used instead. */
    public final Range<Integer> sampleRateRange;
    /** 0 if this is not an audio entry; otherwise the highest input channel
     *  count this decode path accepts (channels 1..maxChannels are assumed
     *  supported, matching AOSP's own AudioCapabilities default model). */
    public final int maxChannels;

    // ---- video (all null/0 for an audio entry) ----
    public final Range<Integer> widthRange;
    public final Range<Integer> heightRange;
    public final Range<Integer> frameRateRange;
    /** Required width/height alignment in pixels. 0 defaults to 2 (the
     *  universal YUV 4:2:0 chroma-subsampling alignment) when applied. */
    public final int widthAlignment;
    public final int heightAlignment;
    /** Largest decodable frame AREA in 16x16 macroblocks, 0 = unbounded. AOSP
     *  VideoCapabilities.isSizeSupported bounds the block count as well as
     *  each dimension (mBlockCountRange); a probed decoder that proved
     *  4096x2304 has not proved 4096x4096. */
    public final int maxMacroblocks;

    // ---- shared ----
    /** May be null (unknown/unbounded); never used to REJECT a format unless
     *  the caller specifies a bitrate, matching AOSP's supportsBitrate. */
    public final Range<Integer> bitrateRange;
    /** Never null; empty means "this format has no meaningful profile/level
     *  concept" (true of MP3) rather than "nothing is supported". */
    public final MediaCodecInfo.CodecProfileLevel[] profileLevels;

    private HostCodecCapability(String mime, boolean encoder, boolean hardwareAccelerated,
            int[] sampleRates, Range<Integer> sampleRateRange, int maxChannels,
            Range<Integer> widthRange, Range<Integer> heightRange, Range<Integer> frameRateRange,
            int widthAlignment, int heightAlignment,
            Range<Integer> bitrateRange, MediaCodecInfo.CodecProfileLevel[] profileLevels,
            int maxMacroblocks) {
        if (mime == null) throw new NullPointerException("mime == null");
        this.mime = mime;
        this.encoder = encoder;
        this.hardwareAccelerated = hardwareAccelerated;
        this.sampleRates = sampleRates;
        this.sampleRateRange = sampleRateRange;
        this.maxChannels = maxChannels;
        this.widthRange = widthRange;
        this.heightRange = heightRange;
        this.frameRateRange = frameRateRange;
        this.widthAlignment = widthAlignment;
        this.heightAlignment = heightAlignment;
        this.maxMacroblocks = maxMacroblocks > 0 ? maxMacroblocks : 0;
        this.bitrateRange = bitrateRange;
        this.profileLevels = profileLevels != null ? profileLevels : new MediaCodecInfo.CodecProfileLevel[0];
    }

    public static HostCodecCapability audioDecoder(String mime, boolean hardwareAccelerated,
            int[] sampleRates, Range<Integer> sampleRateRange, int maxChannels,
            Range<Integer> bitrateRange, MediaCodecInfo.CodecProfileLevel[] profileLevels) {
        return new HostCodecCapability(mime, false, hardwareAccelerated,
                sampleRates, sampleRateRange, maxChannels,
                null, null, null, 0, 0,
                bitrateRange, profileLevels, 0);
    }

    public static HostCodecCapability videoDecoder(String mime, boolean hardwareAccelerated,
            Range<Integer> widthRange, Range<Integer> heightRange, Range<Integer> frameRateRange,
            int widthAlignment, int heightAlignment,
            Range<Integer> bitrateRange, MediaCodecInfo.CodecProfileLevel[] profileLevels) {
        return videoDecoder(mime, hardwareAccelerated, widthRange, heightRange, frameRateRange,
                widthAlignment, heightAlignment, bitrateRange, profileLevels, 0);
    }

    /** As above, plus a frame-area bound in macroblocks (0 = unbounded). */
    public static HostCodecCapability videoDecoder(String mime, boolean hardwareAccelerated,
            Range<Integer> widthRange, Range<Integer> heightRange, Range<Integer> frameRateRange,
            int widthAlignment, int heightAlignment,
            Range<Integer> bitrateRange, MediaCodecInfo.CodecProfileLevel[] profileLevels,
            int maxMacroblocks) {
        return new HostCodecCapability(mime, false, hardwareAccelerated,
                null, null, 0,
                widthRange, heightRange, frameRateRange, widthAlignment, heightAlignment,
                bitrateRange, profileLevels, maxMacroblocks);
    }

    public boolean isAudio() { return sampleRates != null || sampleRateRange != null; }
    public boolean isVideo() { return widthRange != null || heightRange != null; }

    @Override public String toString() {
        return "HostCodecCapability{" + mime + (encoder ? " encoder" : " decoder")
                + (hardwareAccelerated ? " hw" : " sw") + "}";
    }
}
