// android.media.HostCodecProvider -- NOT an AOSP class. This is Mac.apk's own
// seam for wiring real, host-measured codec capability into MediaCodecList.
//
// WHY THIS EXISTS: MediaCodecList.getCodecInfos() used to return an empty
// array, so ExoPlayer/media3 (which selects a decoder by walking
// MediaCodecList) concluded this device has no codecs at all before
// attempting any decode -- see the class comment on MediaCodecList. Fixing
// that honestly requires knowing, per MIME, whether this HOST can actually
// open a working decoder for it, and whether that decode is hardware or
// software. Mac.apk has exactly two things that can answer that for real:
//   - VideoToolbox (VTIsHardwareDecodeSupported per CMVideoCodecType, plus a
//     live VTDecompressionSession's kVTDecompressionPropertyKey_
//     UsingHardwareAcceleratedVideoDecoder readback) for video;
//   - AudioToolbox (kAudioFormatProperty_DecodeFormatIDs) for audio beyond
//     the AAC/MP3 pair MediaCodec.java already opens for real.
// Neither probe is reachable from this package (MediaCodec.java,
// NativeMedia.java and native_methods.cpp -- where a new JNI bridge for
// either would have to be registered -- belong to those efforts), so this
// interface is the contract they implement and register instead of either
// (a) hand-building MediaCodecInfo objects themselves, which would give us
// two divergent code paths turning capability into a MediaCodecInfo, or
// (b) a second, competing registration mechanism. Implement this against a
// REAL probe and call MediaCodecList.registerHostCodecProvider(...); do not
// implement it against a guess or a hardcoded list -- see HostCodecCapability
// and the class comment on MediaCodecList for why that is actively worse
// than reporting nothing.
//
// Adding a codec this way is DATA (build a HostCodecCapability from a real
// query and hand it to a provider), never CODE inside MediaCodecList or
// MediaCodecInfo -- both turn every registered entry into a real
// MediaCodecInfo/CodecCapabilities through the exact same path, so
// getCodecInfos(), findDecoderForFormat()/findEncoderForFormat() and
// MediaCodec.getCodecInfo() can never drift out of sync with each other the
// way three independently hand-rolled lists could.
//
// Never register a ".secure" variant or FEATURE_SecurePlayback: Mac.apk does
// not implement DRM (CLAUDE.md, "Explicitly OUT of scope"), so a provider
// must not claim it does.
package android.media;

import java.util.List;

public interface HostCodecProvider {
    /**
     * Returns this provider's current set of REAL host codec capabilities.
     * Called each time MediaCodecList rebuilds its registry (registration is
     * cheap and this list is small, so there is no caching contract to
     * honor here -- return fresh, accurate data every call). An empty list
     * is a completely valid answer (e.g. a probe that found nothing openable
     * on this host) and is exactly as valid as never registering at all.
     */
    List<HostCodecCapability> getHostCodecCapabilities();
}
