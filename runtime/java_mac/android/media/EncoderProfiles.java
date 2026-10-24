// android.media.EncoderProfiles (API 31), with its nested VideoProfile and
// AudioProfile.
//
// The richer successor to CamcorderProfile: instead of one audio codec and one
// video codec per quality, it carries a LIST of each, so an app can pick HEVC
// over AVC, or a 10-bit HDR profile over SDR, without guessing. Apps obtain one
// from CamcorderProfile.getAll(cameraId, quality).
//
// This is a REAL implementation. AOSP's three classes are pure immutable value
// holders whose only logic is two codec-to-MIME-type switches and one
// codec-to-AAC-profile switch; none of them touch the camera or the media HAL,
// so all of it is reproduced exactly. What is device-dependent is who
// CONSTRUCTS one -- on a real device that is native_get_camcorder_profiles()
// reading media_profiles.xml. See CamcorderProfile.getAll for what we build and
// why.
//
// Mirrored from
// frameworks/base/media/java/android/media/EncoderProfiles.java
// (aosp-mirror/platform_frameworks_base, main): class L57,
// getDefaultDurationSeconds L64, getRecommendedFileFormat L72, VideoProfile
// L79-352 (getCodec L84, getMediaType L92-112, getBitrate L142, getFrameRate
// L172, getWidth L179, getHeight L186, getProfile L198, getBitDepth L208,
// getChromaSubsampling L222, getHdrFormat L236, HDR_* L296-323, YUV_* L339-351,
// both package-private constructors L240-280), getAudioProfiles L361,
// getVideoProfiles L372, AudioProfile L379-467 (getCodec L384, getMediaType
// L392-408, getBitrate L413, getSampleRate L420, getChannels L427, getProfile
// L437-446, constructor L449-462), and the EncoderProfiles constructor L475-486.
//
// Four constants AOSP references here do not exist in this tree's
// MediaRecorder/MediaFormat/MediaCodecInfo (which other lanes own), so their
// AOSP values are inlined at the use site with the source cited rather than
// added to files this lane does not own. Each is flagged below.
package android.media;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class EncoderProfiles {

    private final int durationSecs;
    private final int fileFormat;
    private final List<AudioProfile> audioProfiles;
    private final List<VideoProfile> videoProfiles;

    // AOSP's constructor is package-private and called from JNI and
    // CamcorderProfile; kept package-private for the same reason, so an app can
    // only ever obtain one from CamcorderProfile.getAll.
    EncoderProfiles(int duration, int fileFormat, VideoProfile[] videoProfiles,
            AudioProfile[] audioProfiles) {
        this.durationSecs = duration;
        this.fileFormat = fileFormat;
        this.videoProfiles = Collections.unmodifiableList(Arrays.asList(videoProfiles));
        this.audioProfiles = Collections.unmodifiableList(Arrays.asList(audioProfiles));
    }

    /** Default recording duration in seconds before the session is terminated;
     *  0 when there is no default. */
    public int getDefaultDurationSeconds() {
        return durationSecs;
    }

    /** Recommended output file format -- a MediaRecorder.OutputFormat value. */
    public int getRecommendedFileFormat() {
        return fileFormat;
    }

    /** The defined audio encoder profiles. May be empty; when not, the FIRST
     *  entry is the default. */
    public List<AudioProfile> getAudioProfiles() {
        return audioProfiles;
    }

    /** The defined video encoder profiles. May be empty; when not, the FIRST
     *  entry is the default. */
    public List<VideoProfile> getVideoProfiles() {
        return videoProfiles;
    }

    /** Configuration for a video encoder. */
    public static final class VideoProfile {
        /** Not HDR (SDR). */
        public static final int HDR_NONE = 0;
        /** Hybrid Log-Gamma. */
        public static final int HDR_HLG = 1;
        public static final int HDR_HDR10 = 2;
        public static final int HDR_HDR10PLUS = 3;
        public static final int HDR_DOLBY_VISION = 4;

        public static final int YUV_420 = 0;
        public static final int YUV_422 = 1;
        public static final int YUV_444 = 2;

        private final int codec;
        private final int width;
        private final int height;
        private final int frameRate;
        private final int bitrate;
        private final int profile;
        private final int chromaSubsampling;
        private final int bitDepth;
        private final int hdrFormat;

        VideoProfile(int codec, int width, int height, int frameRate, int bitrate, int profile,
                int chromaSubsampling, int bitDepth, int hdrFormat) {
            this.codec = codec;
            this.width = width;
            this.height = height;
            this.frameRate = frameRate;
            this.bitrate = bitrate;
            this.profile = profile;
            this.chromaSubsampling = chromaSubsampling;
            this.bitDepth = bitDepth;
            this.hdrFormat = hdrFormat;
        }

        VideoProfile(int codec, int width, int height, int frameRate, int bitrate, int profile) {
            this(codec, width, height, frameRate, bitrate, profile, YUV_420, 8, HDR_NONE);
        }

        /** The MediaRecorder.VideoEncoder value used for the video track. */
        public int getCodec() {
            return codec;
        }

        /** The MediaFormat MIME type for getCodec(). AOSP throws
         *  RuntimeException("Unknown codec") on an unrecognised codec rather
         *  than returning null, and that is preserved: a null MIME type would
         *  surface as an NPE somewhere far away from the real mistake. */
        public String getMediaType() {
            if (codec == MediaRecorder.VideoEncoder.H263) {
                // MediaFormat.MIMETYPE_VIDEO_H263 (AOSP MediaFormat.java L170).
                // Absent from this tree's MediaFormat, which another lane owns.
                return "video/3gpp";
            } else if (codec == MediaRecorder.VideoEncoder.H264) {
                return MediaFormat.MIMETYPE_VIDEO_AVC;
            } else if (codec == MediaRecorder.VideoEncoder.MPEG_4_SP) {
                return MediaFormat.MIMETYPE_VIDEO_MPEG4;
            } else if (codec == MediaRecorder.VideoEncoder.VP8) {
                return MediaFormat.MIMETYPE_VIDEO_VP8;
            } else if (codec == MediaRecorder.VideoEncoder.HEVC) {
                return MediaFormat.MIMETYPE_VIDEO_HEVC;
            } else if (codec == 6) {
                // MediaRecorder.VideoEncoder.VP9 (AOSP MediaRecorder.java L712);
                // absent from this tree's MediaRecorder, which another lane owns.
                return MediaFormat.MIMETYPE_VIDEO_VP9;
            } else if (codec == 7) {
                // MediaRecorder.VideoEncoder.DOLBY_VISION (AOSP L713) ->
                // MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION (AOSP L173). Both
                // absent from this tree.
                return "video/dolby-vision";
            } else if (codec == 8) {
                // MediaRecorder.VideoEncoder.AV1 (AOSP L714).
                return MediaFormat.MIMETYPE_VIDEO_AV1;
            }
            // we should never be here
            throw new RuntimeException("Unknown codec");
        }

        /** Target video bitrate in bits per second. */
        public int getBitrate() {
            return bitrate;
        }

        public int getFrameRate() {
            return frameRate;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        /** The encoder profile; NEGATIVE when the codec defines none. */
        public int getProfile() {
            return profile;
        }

        /** Effectively 8 or 10, though a device may report more. */
        public int getBitDepth() {
            return bitDepth;
        }

        /** One of YUV_420 / YUV_422 / YUV_444. */
        public int getChromaSubsampling() {
            return chromaSubsampling;
        }

        /** One of the HDR_* values. */
        public int getHdrFormat() {
            return hdrFormat;
        }
    }

    /** Configuration for an audio encoder. */
    public static final class AudioProfile {
        private final int codec;
        private final int channels;
        private final int sampleRate;
        private final int bitrate;
        private final int profile;

        AudioProfile(int codec, int channels, int sampleRate, int bitrate, int profile) {
            this.codec = codec;
            this.channels = channels;
            this.sampleRate = sampleRate;
            this.bitrate = bitrate;
            this.profile = profile;
        }

        /** The MediaRecorder.AudioEncoder value used for the audio track. */
        public int getCodec() {
            return codec;
        }

        /** The MediaFormat MIME type for getCodec(). Note the three AAC
         *  variants all map to the same MIME type -- they are distinguished by
         *  getProfile(), not by media type. */
        public String getMediaType() {
            if (codec == MediaRecorder.AudioEncoder.AMR_NB) {
                // MediaFormat.MIMETYPE_AUDIO_AMR_NB (AOSP MediaFormat.java
                // L176); absent from this tree's MediaFormat.
                return "audio/3gpp";
            } else if (codec == MediaRecorder.AudioEncoder.AMR_WB) {
                // MediaFormat.MIMETYPE_AUDIO_AMR_WB (AOSP L177).
                return "audio/amr-wb";
            } else if (codec == MediaRecorder.AudioEncoder.AAC
                    || codec == MediaRecorder.AudioEncoder.HE_AAC
                    || codec == MediaRecorder.AudioEncoder.AAC_ELD) {
                return MediaFormat.MIMETYPE_AUDIO_AAC;
            } else if (codec == MediaRecorder.AudioEncoder.VORBIS) {
                return MediaFormat.MIMETYPE_AUDIO_VORBIS;
            } else if (codec == MediaRecorder.AudioEncoder.OPUS) {
                return MediaFormat.MIMETYPE_AUDIO_OPUS;
            }
            // we should never be here
            throw new RuntimeException("Unknown codec");
        }

        public int getBitrate() {
            return bitrate;
        }

        public int getSampleRate() {
            return sampleRate;
        }

        public int getChannels() {
            return channels;
        }

        /** The encoder profile. For the three AAC codecs AOSP OVERRIDES the
         *  stored value with a fixed AAC object type, so the stored value is
         *  only used for everything else. The three constants are
         *  MediaCodecInfo.CodecProfileLevel.AACObject{Main,HE,ELD} == 1, 5, 39
         *  (AOSP MediaCodecInfo.java L4378, L4382, L4388); this tree's
         *  CodecProfileLevel carries no constants and belongs to another lane,
         *  so the values are inlined. */
        public int getProfile() {
            if (codec == MediaRecorder.AudioEncoder.AAC) {
                return 1;   // AACObjectMain
            } else if (codec == MediaRecorder.AudioEncoder.HE_AAC) {
                return 5;   // AACObjectHE
            } else if (codec == MediaRecorder.AudioEncoder.AAC_ELD) {
                return 39;  // AACObjectELD
            }
            return profile;
        }
    }
}
