package android.media;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
// android.media.MediaFormat -- a typed string-keyed bag of media parameters.
// Mirrors AOSP frameworks/base media/java/android/media/MediaFormat.java: the
// real class is a thin wrapper over a Map<String,Object> with per-type
// accessors. We back it with a real map so a created format actually carries
// its data (the prior stub dropped every value, so getInteger after
// createAudioFormat returned 0 and apps that read back width/height/sample-rate
// off an extractor track format saw nothing).
public final class MediaFormat {
    public static final String KEY_MIME = "mime";
    public static final String KEY_CODECS_STRING = "codecs-string";
    public static final String KEY_SAMPLE_RATE = "sample-rate";
    public static final String KEY_CHANNEL_COUNT = "channel-count";
    public static final String KEY_CHANNEL_MASK = "channel-mask";
    public static final String KEY_BIT_RATE = "bitrate";
    public static final String KEY_MAX_BIT_RATE = "max-bitrate";
    public static final String KEY_DURATION = "durationUs";
    public static final String KEY_WIDTH = "width";
    public static final String KEY_HEIGHT = "height";
    public static final String KEY_MAX_WIDTH = "max-width";
    public static final String KEY_MAX_HEIGHT = "max-height";
    public static final String KEY_MAX_INPUT_SIZE = "max-input-size";
    public static final String KEY_BIT_RATE_MODE = "bitrate-mode";
    public static final String KEY_COLOR_FORMAT = "color-format";
    public static final String KEY_COLOR_RANGE = "color-range";
    public static final String KEY_COLOR_STANDARD = "color-standard";
    public static final String KEY_COLOR_TRANSFER = "color-transfer";
    // Values for the three KEY_COLOR_* keys above. Genuinely missing until
    // MediaCodec's no-Surface getOutputImage() path needed to report them
    // (NativeVideoImage's own doc); values fetched verbatim from AOSP
    // frameworks/base media/java/android/media/MediaFormat.java
    // (aosp-mirror/platform_frameworks_base, main, 2026-08-16), not guessed.
    /** BT.709 color chromacity coordinates with KR = 0.2126, KB = 0.0722. */
    public static final int COLOR_STANDARD_BT709 = 1;
    /** BT.601 625 color chromacity coordinates with KR = 0.299, KB = 0.114. */
    public static final int COLOR_STANDARD_BT601_PAL = 2;
    /** BT.601 525 color chromacity coordinates with KR = 0.299, KB = 0.114.
     *  Identical KR/KB to BT601_PAL above -- the PAL/NTSC split is line
     *  count (625 vs 525), not a different colour matrix. */
    public static final int COLOR_STANDARD_BT601_NTSC = 4;
    /** BT.2020 color chromacity coordinates with KR = 0.2627, KB = 0.0593. */
    public static final int COLOR_STANDARD_BT2020 = 6;
    /** Linear transfer characteristic curve. */
    public static final int COLOR_TRANSFER_LINEAR = 1;
    /** SMPTE 170M transfer characteristic curve used by BT.601/BT.709/
     *  BT.2020. This is the curve used by most non-HDR video content. */
    public static final int COLOR_TRANSFER_SDR_VIDEO = 3;
    /** SMPTE ST 2084 transfer function, used by some HDR video content. */
    public static final int COLOR_TRANSFER_ST2084 = 6;
    /** ARIB STD-B67 hybrid-log-gamma transfer function, used by some HDR
     *  video content. */
    public static final int COLOR_TRANSFER_HLG = 7;
    /** Limited range. Y is 16-235, Cb/Cr is 16-240, for 8-bit content.
     *  This is the default for video content. */
    public static final int COLOR_RANGE_LIMITED = 2;
    /** Full range. Y, Cb and Cr are all 0-255 for 8-bit content. */
    public static final int COLOR_RANGE_FULL = 1;
    public static final String KEY_FRAME_RATE = "frame-rate";
    public static final String KEY_CAPTURE_RATE = "capture-rate";
    public static final String KEY_I_FRAME_INTERVAL = "i-frame-interval";
    public static final String KEY_INTRA_REFRESH_PERIOD = "intra-refresh-period";
    public static final String KEY_LATENCY = "latency";
    public static final String KEY_LEVEL = "level";
    public static final String KEY_PROFILE = "profile";
    public static final String KEY_AAC_PROFILE = "aac-profile";
    public static final String KEY_AAC_SBR_MODE = "aac-sbr-mode";
    public static final String KEY_AAC_DRC_TARGET_REFERENCE_LEVEL = "aac-target-ref-level";
    public static final String KEY_AAC_ENCODED_TARGET_LEVEL = "aac-encoded-target-level";
    public static final String KEY_AAC_DRC_BOOST_FACTOR = "aac-drc-boost-level";
    public static final String KEY_AAC_DRC_ATTENUATION_FACTOR = "aac-drc-cut-level";
    public static final String KEY_AAC_DRC_HEAVY_COMPRESSION = "aac-drc-heavy-compression";
    public static final String KEY_AAC_MAX_OUTPUT_CHANNEL_COUNT = "aac-max-output-channel_count";
    public static final String KEY_IS_ADTS = "is-adts";
    public static final String KEY_IS_AUTOSELECT = "is-autoselect";
    public static final String KEY_IS_DEFAULT = "is-default";
    public static final String KEY_IS_FORCED_SUBTITLE = "is-forced-subtitle";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_PCM_ENCODING = "pcm-encoding";
    public static final String KEY_PUSH_BLANK_BUFFERS_ON_STOP = "push-blank-buffers-on-shutdown";
    public static final String KEY_REPEAT_PREVIOUS_FRAME_AFTER = "repeat-previous-frame-after";
    public static final String KEY_OPERATING_RATE = "operating-rate";
    public static final String KEY_PRIORITY = "priority";
    public static final String KEY_ROTATION = "rotation-degrees";
    public static final String KEY_GRID_ROWS = "grid-rows";
    public static final String KEY_GRID_COLUMNS = "grid-cols";
    public static final String KEY_TILE_WIDTH = "tile-width";
    public static final String KEY_TILE_HEIGHT = "tile-height";
    public static final String KEY_TEMPORAL_LAYERING = "ts-schema";
    public static final String KEY_FLAC_COMPRESSION_LEVEL = "flac-compression-level";
    public static final String KEY_TRACK_ID = "track-id";
    public static final String KEY_HDR_STATIC_INFO = "hdr-static-info";
    public static final String KEY_PIXEL_ASPECT_RATIO_WIDTH = "sar-width";
    public static final String KEY_PIXEL_ASPECT_RATIO_HEIGHT = "sar-height";

    // Common MIME constants (AOSP MediaFormat MIMETYPE_*).
    public static final String MIMETYPE_AUDIO_AAC = "audio/mp4a-latm";
    public static final String MIMETYPE_AUDIO_MPEG = "audio/mpeg";
    public static final String MIMETYPE_AUDIO_OPUS = "audio/opus";
    public static final String MIMETYPE_AUDIO_VORBIS = "audio/vorbis";
    public static final String MIMETYPE_AUDIO_FLAC = "audio/flac";
    public static final String MIMETYPE_AUDIO_RAW = "audio/raw";
    public static final String MIMETYPE_AUDIO_AC3 = "audio/ac3";
    public static final String MIMETYPE_AUDIO_EAC3 = "audio/eac3";
    public static final String MIMETYPE_AUDIO_AMR_NB = "audio/3gpp";
    public static final String MIMETYPE_AUDIO_AMR_WB = "audio/amr-wb";
    public static final String MIMETYPE_AUDIO_G711_ALAW = "audio/g711-alaw";
    public static final String MIMETYPE_AUDIO_G711_MLAW = "audio/g711-mlaw";
    /** No AOSP constant exists for this MIME (apps pass the literal string;
     *  confirmed absent from frameworks/base MediaFormat.java on
     *  2026-08-16, aosp-mirror/platform_frameworks_base, main), even though
     *  a c2.android.alac.decoder exists on-device. Named here anyway so
     *  MediaCodec's mime dispatch has one place to read it from like every
     *  other format, per this file's own "genuinely missing" convention. */
    public static final String MIMETYPE_AUDIO_ALAC = "audio/alac";
    public static final String MIMETYPE_VIDEO_AVC = "video/avc";
    public static final String MIMETYPE_VIDEO_HEVC = "video/hevc";
    public static final String MIMETYPE_VIDEO_VP8 = "video/x-vnd.on2.vp8";
    public static final String MIMETYPE_VIDEO_VP9 = "video/x-vnd.on2.vp9";
    public static final String MIMETYPE_VIDEO_AV1 = "video/av01";
    public static final String MIMETYPE_VIDEO_MPEG4 = "video/mp4v-es";
    public static final String MIMETYPE_VIDEO_MPEG2 = "video/mpeg2";
    public static final String MIMETYPE_VIDEO_H263 = "video/3gpp";
    public static final String MIMETYPE_TEXT_VTT = "text/vtt";
    public static final String MIMETYPE_TEXT_SUBRIP = "application/x-subrip";

    public static final int COLOR_FormatYUV420Flexible = 0x7f420888;
    public static final int COLOR_FormatSurface = 0x7f000789;

    private final Map<String, Object> mMap;

    public MediaFormat() { mMap = new HashMap<String, Object>(); }
    // AOSP has a package-private MediaFormat(Map) used internally by the codec;
    // we expose nothing extra. createAudioFormat / createVideoFormat populate
    // the standard keys the way AOSP does.
    public static MediaFormat createAudioFormat(String mime, int sampleRate, int channelCount) {
        MediaFormat f = new MediaFormat();
        f.setString(KEY_MIME, mime);
        f.setInteger(KEY_SAMPLE_RATE, sampleRate);
        f.setInteger(KEY_CHANNEL_COUNT, channelCount);
        return f;
    }
    public static MediaFormat createVideoFormat(String mime, int width, int height) {
        MediaFormat f = new MediaFormat();
        f.setString(KEY_MIME, mime);
        f.setInteger(KEY_WIDTH, width);
        f.setInteger(KEY_HEIGHT, height);
        return f;
    }
    public static MediaFormat createSubtitleFormat(String mime, String language) {
        MediaFormat f = new MediaFormat();
        f.setString(KEY_MIME, mime);
        f.setString(KEY_LANGUAGE, language);
        return f;
    }

    /** API 29. AOSP: "Creates a copy of a MediaFormat." A shallow copy of the
     *  key/value map, exactly as AOSP does (MediaFormat.java,
     *  MediaFormat(MediaFormat)) -- the ByteBuffer values are shared, which is
     *  AOSP's behaviour too. */
    public MediaFormat(MediaFormat other) {
        this();
        if (other != null) mMap.putAll(other.mMap);
    }

    public boolean containsKey(String name) { return mMap.containsKey(name); }

    /** API 29. Whether a "feature-<name>" entry is present at all -- distinct
     *  from getFeatureEnabled(), which asks whether it is present AND set. */
    public boolean containsFeature(String feature) {
        return mMap.containsKey("feature-" + feature);
    }

    /** API 29. The feature names this format carries, without the internal
     *  "feature-" prefix (AOSP MediaFormat.java, getFeatures). */
    public java.util.Set<String> getFeatures() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String key : mMap.keySet()) {
            if (key.startsWith("feature-")) out.add(key.substring("feature-".length()));
        }
        return out;
    }

    /** API 29. Removes a feature entry. AOSP throws IllegalArgumentException on
     *  a null feature name. */
    public void removeFeature(String feature) {
        if (feature == null) throw new IllegalArgumentException("Invalid null feature name");
        mMap.remove("feature-" + feature);
    }

    /**
     * API 29. Reads a numeric key WITHOUT the caller having to know whether the
     * producer stored it as an Integer, a Long or a Float. This exists because
     * KEY_FRAME_RATE in particular is legally either an int or a float depending
     * on who set it, and a getInteger() on a Float value is a ClassCastException.
     * AOSP's body is exactly `return (Number) mMap.get(name);`, so an absent
     * key yields null (its @NonNull annotation is what turns that into the
     * NullPointerException its javadoc promises, at the caller's unboxing) and a
     * non-numeric value is a ClassCastException. Mirrored verbatim.
     */
    public Number getNumber(String name) {
        return (Number) mMap.get(name);
    }

    public Number getNumber(String name, Number defaultValue) {
        Object v = mMap.get(name);
        return (v instanceof Number) ? (Number) v : defaultValue;
    }
    public int getKeyType(String name) {
        Object v = mMap.get(name);
        if (v instanceof Integer) return TYPE_INTEGER;
        if (v instanceof Long) return TYPE_LONG;
        if (v instanceof Float) return TYPE_FLOAT;
        if (v instanceof String) return TYPE_STRING;
        if (v instanceof ByteBuffer) return TYPE_BYTE_BUFFER;
        return TYPE_NULL;
    }
    // AOSP getValueTypeForKey (API 29) is the same instanceof chain as
    // getKeyType (mMap only ever holds the 5 types set* below, so the two
    // are behaviorally identical for every reachable state; AOSP's own
    // impl throws on a truly unrecognized value type, which mMap can never
    // contain here since it is only ever populated via our setString/
    // setInteger/setLong/setFloat/setByteBuffer).
    public final int getValueTypeForKey(String name) { return getKeyType(name); }
    public static final int TYPE_NULL = 0;
    public static final int TYPE_INTEGER = 1;
    public static final int TYPE_LONG = 2;
    public static final int TYPE_FLOAT = 3;
    public static final int TYPE_STRING = 4;
    public static final int TYPE_BYTE_BUFFER = 5;

    public java.util.Set<String> getKeys() { return mMap.keySet(); }

    public void setString(String name, String value) { mMap.put(name, value); }
    public void setInteger(String name, int value)   { mMap.put(name, Integer.valueOf(value)); }
    public void setLong(String name, long value)     { mMap.put(name, Long.valueOf(value)); }
    public void setFloat(String name, float value)   { mMap.put(name, Float.valueOf(value)); }
    public void setByteBuffer(String name, ByteBuffer bytes) { mMap.put(name, bytes); }
    public void removeKey(String name) { mMap.remove(name); }
    public void setFeatureEnabled(String feature, boolean enabled) {
        mMap.put("feature-" + feature, Integer.valueOf(enabled ? 1 : 0));
    }

    // AOSP throws NullPointerException when the key is absent (no default
    // overload existed pre-API-29). Mirror that contract for the no-default
    // forms; provide the API-29 default-bearing overloads too.
    public String getString(String name) {
        Object v = mMap.get(name);
        return v == null ? null : v.toString();
    }
    public String getString(String name, String defaultValue) {
        Object v = mMap.get(name);
        return v == null ? defaultValue : v.toString();
    }
    public int getInteger(String name) {
        return ((Integer) mMap.get(name)).intValue();
    }
    public int getInteger(String name, int defaultValue) {
        Object v = mMap.get(name);
        return v instanceof Integer ? ((Integer) v).intValue() : defaultValue;
    }
    public long getLong(String name) {
        return ((Long) mMap.get(name)).longValue();
    }
    public long getLong(String name, long defaultValue) {
        Object v = mMap.get(name);
        return v instanceof Long ? ((Long) v).longValue() : defaultValue;
    }
    public float getFloat(String name) {
        return ((Float) mMap.get(name)).floatValue();
    }
    public float getFloat(String name, float defaultValue) {
        Object v = mMap.get(name);
        return v instanceof Float ? ((Float) v).floatValue() : defaultValue;
    }
    public ByteBuffer getByteBuffer(String name) {
        return (ByteBuffer) mMap.get(name);
    }
    public ByteBuffer getByteBuffer(String name, ByteBuffer defaultValue) {
        Object v = mMap.get(name);
        return v instanceof ByteBuffer ? (ByteBuffer) v : defaultValue;
    }
    public boolean getFeatureEnabled(String feature) {
        Object v = mMap.get("feature-" + feature);
        return v instanceof Integer && ((Integer) v).intValue() != 0;
    }

    // Package-private: AOSP's MediaCodecInfo.CodecCapabilities.isFormatSupported
    // reads the raw map directly ("Map<String, Object> map = format.getMap();",
    // frameworks/base media/java/android/media/MediaCodecInfo.java) rather than
    // going through the typed getters, so a single KEY_MIME/KEY_SAMPLE_RATE/
    // KEY_CHANNEL_COUNT/feature- lookup can be written once instead of one
    // getInteger/getString call per key. Same package (android.media), so this
    // stays invisible outside it.
    Map<String, Object> getMap() { return mMap; }

    /**
     * A region-of-interest QP offset: a rectangle plus the quantiser delta the
     * ENCODER should apply inside it (API 34). Pure app-supplied request data
     * with a defined flat-string encoding, so it is implemented in full --
     * including AOSP's exact "top,left-bottom,right=offset" wire form, because
     * that string is what actually reaches a codec through
     * MediaFormat.KEY_QP_OFFSET_RECTS and a wrong shape would be silently
     * ignored.
     *
     * There is no encoder on this host (MediaCodec's native backing is
     * decode-only), so nothing consumes the key -- but building and flattening
     * one behaves exactly as on Android.
     *
     * Source: frameworks/base/media/java/android/media/MediaFormat.java,
     * QpOffsetRect.
     */
    public static final class QpOffsetRect {
        private android.graphics.Rect mContour;
        private int mQpOffset;

        public QpOffsetRect(android.graphics.Rect contour, int qpOffset) {
            set(contour, qpOffset);
        }

        public void set(android.graphics.Rect contour, int qpOffset) {
            mContour = java.util.Objects.requireNonNull(contour, "contour must not be null");
            mQpOffset = qpOffset;
        }

        /** AOSP's format: "top,left-bottom,right=qpOffset". */
        public String flattenToString() {
            return mContour.top + "," + mContour.left + "-"
                    + mContour.bottom + "," + mContour.right + "=" + mQpOffset;
        }

        /** AOSP joins several rects with ";". */
        public static String flattenToString(java.util.List<QpOffsetRect> qpOffsetRects) {
            java.util.Objects.requireNonNull(qpOffsetRects, "list must not be null");
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < qpOffsetRects.size(); i++) {
                if (i > 0) builder.append(";");
                builder.append(qpOffsetRects.get(i).flattenToString());
            }
            return builder.toString();
        }
    }

    @Override public String toString() { return mMap.toString(); }
}
