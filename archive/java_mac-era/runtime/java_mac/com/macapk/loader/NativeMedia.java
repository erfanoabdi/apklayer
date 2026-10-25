// NativeMedia -- JNI surface for the AudioToolbox audio-decode backend in
// runtime/src/media_decode.mm, AND (below) the VideoToolbox video-decode
// backend in runtime/src/video_toolbox.mm. Backs android.media.MediaExtractor
// (demuxer) and MediaCodec (decoder) with real AAC/MP3 -> S16 PCM decode, and
// real H.264/HEVC/VP9/AV1/MPEG-4/MPEG-2/H.263 -> CVPixelBuffer video decode,
// hardware-accelerated wherever this host offers it (measured per codec, not
// assumed -- see nativeVideoIsHardware). Registered by
// native_methods.cpp::RegisterAllNativeMethods at boot (no System.loadLibrary;
// the JVM does not auto-resolve Java_* on macOS) -- two separate native
// libraries (media_decode.mm's RegisterNativeMediaMethods, video_toolbox.mm's
// RegisterNativeVideoMethods) register disjoint method sets against this one
// class, which is ordinary, safe JNI (RegisterNatives is additive per call).
package com.macapk.loader;

public final class NativeMedia {
    private NativeMedia() {}

    // ---- Demuxer (backs MediaExtractor): AudioToolbox AudioFile ----
    /** Opens a container; returns an opaque demuxer handle, or 0 on failure. */
    public static native long nativeDemuxOpen(String path);
    public static native int  nativeDemuxTrackCount(long h);   // 1 audio, 0 none
    public static native int  nativeDemuxSampleRate(long h);
    public static native int  nativeDemuxChannels(long h);
    public static native int  nativeDemuxFormatId(long h);     // 1=AAC, 2=MP3, 0=other
    public static native long nativeDemuxDurationUs(long h);
    /** The AAC magic cookie (esds / AudioSpecificConfig) = csd-0, or null. */
    public static native byte[] nativeDemuxCsd0(long h);
    /** The current (pre-read) compressed access unit, or null at EOS. */
    public static native byte[] nativeDemuxCurrent(long h);
    public static native long nativeDemuxSampleTime(long h);   // current AU pts (us), -1 EOS
    public static native int  nativeDemuxAdvance(long h);      // 1 = new current, 0 = EOS
    public static native void nativeDemuxSeek(long h, long us);
    public static native void nativeDemuxClose(long h);

    // ---- Decoder (backs MediaCodec): AudioToolbox AudioConverter ----
    /** Opens a decoder for (formatId, sampleRate, channels, csd-0); handle or 0. */
    public static native long nativeDecOpen(int formatId, int sampleRate, int channels, byte[] csd0);
    /** Feed one compressed AU (au may be null at eos); returns S16 PCM bytes (may be empty). */
    public static native byte[] nativeDecDecode(long h, byte[] au, boolean eos);
    public static native int  nativeDecOutSampleRate(long h);
    public static native int  nativeDecOutChannels(long h);
    public static native void nativeDecClose(long h);

    // ---- Video decoder (backs MediaCodec video mimes): VideoToolbox ----
    //
    // This is the single source of truth for "what video can Mac.apk decode
    // on THIS host, and is it hardware": MediaCodec.java calls these to
    // decide configure()/start() behavior, and MediaCodecList/MediaCodecInfo
    // are expected to answer findDecoderForFormat()/isHardwareAccelerated()
    // from the SAME two methods rather than a second, hand-maintained list --
    // see video_toolbox.mm's kMimeTable for the one place a new codec is
    // added on the native side.

    /** MIME strings this host can decode via VideoToolbox, probed once and
     *  cached (VTIsHardwareDecodeSupported + an actual trial session create,
     *  per codec). VP8 is deliberately never in this list -- VideoToolbox has
     *  no VP8 decoder on any macOS version; a software path, if present, is
     *  reported through a different mechanism, not this one. */
    public static native String[] nativeVideoSupportedMimes();
    /** True only if `mime` measured as using HARDWARE decode on this host
     *  (kVTDecompressionPropertyKey_UsingHardwareAcceleratedVideoDecoder read
     *  back after a real session create, not merely requested). False for a
     *  software VideoToolbox path or an unsupported mime. */
    public static native boolean nativeVideoIsHardware(String mime);
    /** The host-probed profile/level table for `mime`
     *  (runtime/src/video_caps_probe.mm): {maxWidth, maxHeight,
     *  maxMacroblocks, nPairs, profile0, level0, ...} in
     *  MediaCodecInfo.CodecProfileLevel constants, every pair proven by a real
     *  encode-then-decode round trip at that level's maximum frame size. Null
     *  when no per-profile probe exists for the mime. */
    public static native int[] nativeVideoCapabilities(String mime);

    /** Opens a decoder for `mime` at (width,height). csd0/csd1 are AOSP's
     *  MediaFormat "csd-0"/"csd-1" exactly as the app supplied them (Annex-B,
     *  start-code-prefixed, for avc/hevc; may be null for codecs that carry
     *  no out-of-band parameter sets). wantSurface is a hint only (today's
     *  destination format is BGRA either way). Returns an opaque native
     *  handle, or 0 on failure (unsupported mime, or csd that did not yield
     *  usable parameter sets). */
    public static native long nativeVidOpen(String mime, int width, int height,
                                            byte[] csd0, byte[] csd1, boolean wantSurface);
    /** Feeds one compressed access unit (Annex-B for avc/hevc -- converted to
     *  AVCC internally, see video_toolbox.mm's AnnexBToAvcc -- or the raw
     *  elementary-stream frame for every other supported mime). Fire-and-
     *  forget: VideoToolbox decodes asynchronously (with B-frame reordering
     *  enabled), so a queued frame's decoded output shows up later via
     *  nativeVidPoll, not synchronously here. Returns 0 on success. */
    public static native int nativeVidDecode(long h, byte[] au, long presentationTimeUs, boolean eos);
    /** Pops one ready decoded frame, if any: a 3-element
     *  {nativePixelBufferHandle, presentationTimeUs, bufferFlags} long[], or
     *  null if none is ready yet (the MediaCodec.dequeueOutputBuffer
     *  TRY_AGAIN_LATER case). The handle is a retained CVPixelBufferRef and
     *  MUST be passed to exactly one of nativeVidRender or
     *  nativeVidReleaseFrame (never both, never neither) or native memory
     *  leaks/dangles. */
    public static native long[] nativeVidPoll(long h);
    /** MediaCodec.releaseOutputBuffer(index, render=true): composites the
     *  polled frame onto the app's video layer. Does not itself free the
     *  frame -- call nativeVidReleaseFrame afterward regardless. */
    public static native void nativeVidRender(long pixbufHandle, int width, int height);
    /** Frees a polled frame's native memory. Required exactly once per
     *  nativeVidPoll result, whether or not it was rendered. */
    public static native void nativeVidReleaseFrame(long pixbufHandle);
    /** Best-effort raw-pixel readout of a polled (not yet released) frame as
     *  packed 32BGRA, for the no-Surface decode-to-buffer path
     *  (MediaCodec.getOutputImage). `out` must already be sized to fit;
     *  `outWidthHeightStride` is a caller-allocated int[3], filled with
     *  {width, height, rowStrideBytes} on success. Returns 0 on success. */
    public static native int nativeVidFrameBgra(long pixbufHandle, byte[] out, int[] outWidthHeightStride);
    /** MediaCodec.flush(): drops every queued-but-undelivered output frame
     *  (freeing their native memory) and anything still in flight inside
     *  VideoToolbox, matching AOSP's "all output buffers are immediately
     *  revoked at the point of the flush". */
    public static native void nativeVidFlush(long h);
    public static native void nativeVidClose(long h);

    // ---- VP8 video decoder (backs MediaCodec's video/x-vnd.on2.vp8): -----
    // libvpx, runtime/src/video_vp8_decode.mm. A SECOND video backend
    // parallel to the VideoToolbox one above -- VP8 has no VideoToolbox
    // decoder on any macOS version (see video_toolbox.mm's own header), so
    // this is a statically-linked software decode path instead. Signatures
    // mirror nativeVidOpen/_Decode/_Poll/_Flush/_Close exactly (same
    // handle/au/pts/eos shapes) so MediaCodec.java can dispatch to either
    // with a one-line ternary per call site -- see that class's isVp8().
    // render/releaseFrame/frameBgra need NO VP8 equivalents: a VP8 frame is
    // a byte-identical retained CVPixelBufferRef to a VideoToolbox one
    // (video_vp8_decode.mm produces the SAME BGRA CVPixelBuffer shape), so
    // nativeVidRender/nativeVidReleaseFrame/nativeVidFrameBgra above already
    // work on it unchanged.
    /** Opens a VP8 decoder at (width,height); csd0/csd1 are unused by VP8
     *  (no out-of-band parameter sets -- every frame is self-contained) but
     *  kept in the signature for a uniform call shape. wantSurface is a
     *  hint only, matching nativeVidOpen. Returns an opaque native handle,
     *  or 0 on failure. */
    public static native long nativeVp8Open(String mime, int width, int height,
                                            byte[] csd0, byte[] csd1, boolean wantSurface);
    /** Feeds one compressed VP8 frame. Returns 0 on success. */
    public static native int nativeVp8Decode(long h, byte[] au, long presentationTimeUs, boolean eos);
    /** Pops one ready decoded frame: {nativePixelBufferHandle,
     *  presentationTimeUs, bufferFlags}, or null if none is ready yet. */
    public static native long[] nativeVp8Poll(long h);
    public static native void nativeVp8Flush(long h);
    public static native void nativeVp8Close(long h);

    // ---- VP9 video decoder (backs MediaCodec's video/x-vnd.on2.vp9): -----
    // libvpx, runtime/src/video_vp9_decode.mm. A THIRD video backend,
    // parallel to VideoToolbox and VP8 above -- VideoToolbox probes VP9 as
    // genuinely absent on this host (OSStatus -12906
    // kVTCouldNotFindVideoDecoderErr even fed a real config record, see
    // docs/videotoolbox-probe-audit_2026-08-16.md section 3), so this is
    // the same rung-2 (clean third-party decoder) answer VP8 uses, reusing
    // the SAME statically-linked libvpx archive -- zero new dependency
    // surface. Signatures mirror nativeVp8Open/_Decode/_Poll/_Flush/_Close
    // exactly so MediaCodec.java can dispatch to any of the three engines
    // with a one-line branch per call site -- see that class's isVp9().
    // render/releaseFrame/frameBgra need NO VP9 equivalents, same reasoning
    // as VP8's: a VP9 frame is a byte-identical retained CVPixelBufferRef
    // (video_vp9_decode.mm produces the SAME BGRA CVPixelBuffer shape), so
    // nativeVidRender/nativeVidReleaseFrame/nativeVidFrameBgra above already
    // work on it unchanged.
    /** Opens a VP9 decoder at (width,height). csd0/csd1 (VP9's OPTIONAL
     *  WebM CodecPrivate data, per AOSP's own CSD table) are accepted for
     *  call-shape parity but unused -- libvpx reads profile/bit-depth/
     *  colour config directly from each frame's uncompressed header, so no
     *  out-of-band config is required to decode correctly. wantSurface is a
     *  hint only, matching nativeVidOpen/nativeVp8Open. Returns an opaque
     *  native handle, or 0 on failure. */
    public static native long nativeVp9Open(String mime, int width, int height,
                                            byte[] csd0, byte[] csd1, boolean wantSurface);
    /** Feeds one compressed VP9 access unit (one superframe or plain frame
     *  -- libvpx parses the superframe index internally). Returns 0 on
     *  success. */
    public static native int nativeVp9Decode(long h, byte[] au, long presentationTimeUs, boolean eos);
    /** Pops one ready decoded frame: {nativePixelBufferHandle,
     *  presentationTimeUs, bufferFlags}, or null if none is ready yet. */
    public static native long[] nativeVp9Poll(long h);
    public static native void nativeVp9Flush(long h);
    public static native void nativeVp9Close(long h);
}
