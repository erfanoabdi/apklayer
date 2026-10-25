// android.media.MediaMetadataRetriever -- pulls metadata + frames out of a
// media file. AOSP frameworks/base media/java/android/media/MediaMetadataRetriever.java.
//
// Mac.apk has no full (stagefright-class) demuxer/decoder backend wired into
// the JVM side, so frame extraction (getFrameAtTime and friends) and any key
// that needs a decoded video track (HAS_VIDEO / VIDEO_WIDTH / VIDEO_HEIGHT)
// stay null -- those need runtime/src/media_video.mm's macapk_vd_* core wired
// through a new native bridge (like com.macapk.loader.NativeMedia below, but
// for video), which is outside this file's ownership.
//
// The AUDIO side is not hypothetical, though: runtime/src/media_decode.mm's
// AudioToolbox-backed demuxer is already implemented and already registered
// as com.macapk.loader.NativeMedia (it backs MediaExtractor/MediaCodec). It
// answers real duration / sample-rate / channel-count / track-presence for
// any container AudioFile can open -- which includes plain audio files and,
// per AudioFileOpenURL's normal behavior, the audio track of many video
// containers too (AudioFile only ever reports the AUDIO stream; it has no
// opinion on whether a video stream also exists, so HAS_VIDEO/WIDTH/HEIGHT
// cannot be answered from it -- those legitimately stay null). We wire
// extractMetadata's audio-answerable keys (DURATION, HAS_AUDIO, BITRATE via
// file-size/duration, MIMETYPE via extension) to that already-shipped,
// already-registered core below. Every other key, and every frame/image
// method, keeps AOSP's other legal answer: null, exactly as AOSP itself
// returns null for a key a given file's container genuinely lacks.
package android.media;

public class MediaMetadataRetriever implements AutoCloseable {
    public static final int OPTION_PREVIOUS_SYNC = 0x00;
    public static final int OPTION_NEXT_SYNC = 0x01;
    public static final int OPTION_CLOSEST_SYNC = 0x02;
    public static final int OPTION_CLOSEST = 0x03;

    public static final int METADATA_KEY_CD_TRACK_NUMBER = 0;
    public static final int METADATA_KEY_ALBUM = 1;
    public static final int METADATA_KEY_ARTIST = 2;
    public static final int METADATA_KEY_AUTHOR = 3;
    public static final int METADATA_KEY_COMPOSER = 4;
    public static final int METADATA_KEY_DATE = 5;
    public static final int METADATA_KEY_GENRE = 6;
    public static final int METADATA_KEY_TITLE = 7;
    public static final int METADATA_KEY_YEAR = 8;
    public static final int METADATA_KEY_DURATION = 9;
    public static final int METADATA_KEY_NUM_TRACKS = 10;
    public static final int METADATA_KEY_WRITER = 11;
    public static final int METADATA_KEY_MIMETYPE = 12;
    public static final int METADATA_KEY_ALBUMARTIST = 13;
    public static final int METADATA_KEY_DISC_NUMBER = 14;
    public static final int METADATA_KEY_COMPILATION = 15;
    public static final int METADATA_KEY_HAS_AUDIO = 16;
    public static final int METADATA_KEY_HAS_VIDEO = 17;
    public static final int METADATA_KEY_VIDEO_WIDTH = 18;
    public static final int METADATA_KEY_VIDEO_HEIGHT = 19;
    public static final int METADATA_KEY_BITRATE = 20;
    public static final int METADATA_KEY_VIDEO_ROTATION = 24;
    public static final int METADATA_KEY_CAPTURE_FRAMERATE = 25;
    public static final int METADATA_KEY_HAS_IMAGE = 26;
    public static final int METADATA_KEY_IMAGE_COUNT = 27;
    public static final int METADATA_KEY_IMAGE_WIDTH = 29;
    public static final int METADATA_KEY_IMAGE_HEIGHT = 30;
    // 32, not 38. frameworks/base/media/java/android/media/MediaMetadataRetriever.java
    // (android-11.0.0_r48). extractMetadata() switches on this key, so a wrong value
    // reads back a different (or no) field.
    public static final int METADATA_KEY_VIDEO_FRAME_COUNT = 32;

    private String mPath;   // resolved local filesystem path, or an opaque URI string we cannot open
    private long   mDemux;  // com.macapk.loader.NativeMedia demux handle (audio-track metadata only); 0 = unavailable

    public MediaMetadataRetriever() {}

    // AOSP: "may be time-consuming" -- ours genuinely is now, since it opens
    // the file via AudioToolbox to answer extractMetadata below. AOSP also
    // validates the source up front and throws IllegalArgumentException for a
    // local path that plainly does not exist; we mirror that much. A path
    // that exists but that our audio-only backend cannot parse (a video-only
    // container, or a codec AudioFile does not recognize) is NOT an error
    // here -- we have no full stagefright-class demuxer to guarantee AOSP
    // could have opened it either, so extractMetadata degrades to null for
    // the keys that needed it, same as before this fix.
    public void setDataSource(String path) throws IllegalArgumentException {
        closeDemux();
        mPath = null;
        if (path == null) return;
        String p = path.startsWith("file://") ? path.substring(7) : path;
        if (p.indexOf("://") < 0) {
            if (!new java.io.File(p).exists())
                throw new IllegalArgumentException(p + " does not exist");
            mPath = p;
            mDemux = com.macapk.loader.NativeMedia.nativeDemuxOpen(p);
        } else {
            // A scheme we cannot resolve to a local file (content://, http://,
            // ...): keep the historical lenient behavior. mDemux stays 0, so
            // extractMetadata answers null for the keys that needed it.
            mPath = p;
        }
    }
    public void setDataSource(String uri, java.util.Map<String, String> headers) { setDataSource(uri); }
    public void setDataSource(java.io.FileDescriptor fd) {}
    public void setDataSource(java.io.FileDescriptor fd, long offset, long length) {}
    public void setDataSource(android.content.Context context, android.net.Uri uri) {
        if (uri == null) { closeDemux(); mPath = null; return; }
        String scheme = uri.getScheme();
        if (scheme == null || "file".equals(scheme)) {
            setDataSource(uri.getPath());
        } else {
            closeDemux();
            mPath = uri.toString();
        }
    }
    public void setDataSource(android.content.res.AssetFileDescriptor afd) {}
    public void setDataSource(MediaDataSource dataSource) {}

    public String extractMetadata(int keyCode) {
        switch (keyCode) {
            case METADATA_KEY_HAS_AUDIO:
                // AOSP: "If this key exists the media contains audio content" --
                // the value itself is the conventional presence marker "yes".
                return (mDemux != 0 && com.macapk.loader.NativeMedia.nativeDemuxTrackCount(mDemux) > 0)
                        ? "yes" : null;
            case METADATA_KEY_DURATION: {
                // AOSP: duration in ms, as a decimal string.
                if (mDemux == 0) return null;
                long us = com.macapk.loader.NativeMedia.nativeDemuxDurationUs(mDemux);
                return us >= 0 ? String.valueOf(us / 1000) : null;
            }
            case METADATA_KEY_BITRATE: {
                // AOSP: average bitrate in bits/sec. We have no per-track
                // bitrate from the container, but total-file-size / duration
                // is the same "average bitrate" quantity AOSP's own average
                // (as opposed to per-frame) bitrate answers.
                if (mDemux == 0 || mPath == null) return null;
                long us = com.macapk.loader.NativeMedia.nativeDemuxDurationUs(mDemux);
                if (us <= 0) return null;
                long bytes = new java.io.File(mPath).length();
                if (bytes <= 0) return null;
                return String.valueOf(Math.round(bytes * 8.0 * 1_000_000.0 / us));
            }
            case METADATA_KEY_MIMETYPE:
                // AOSP examples: "video/mp4", "audio/mp4", "audio/amr-wb" -- a
                // container-level mime, not a codec id, so this is derived
                // from the path's extension (independent of mDemux/audio
                // presence) rather than from the audio codec we detected.
                return mimeTypeFromPath();
            case METADATA_KEY_NUM_TRACKS: {
                // AOSP: "the number of tracks, such as audio, video, text"
                // in the media. Our demux is audio-only (see header), so this
                // reports the audio track count nativeDemuxTrackCount already
                // answers for METADATA_KEY_HAS_AUDIO above -- an honest lower
                // bound (it undercounts a video container's own video/text
                // tracks the same way HAS_VIDEO already stays null for one)
                // rather than the unconditional null this key returned before,
                // for data the extractor already had in hand.
                if (mDemux == 0) return null;
                int n = com.macapk.loader.NativeMedia.nativeDemuxTrackCount(mDemux);
                return n > 0 ? String.valueOf(n) : null;
            }
            default:
                return null;
        }
    }
    // ---- frame / image extraction -----------------------------------------
    // Every one of these returns null, and that is AOSP's own documented answer
    // when a frame "cannot be retrieved". The reason here is a real, single
    // absence: this class's demuxer (com.macapk.loader.NativeMedia's
    // nativeDemux* family) is AUDIO-ONLY -- see the file header -- so there is
    // no video track to pull a frame out of and no HEIF image grid to index
    // into. Returning a blank or placeholder Bitmap would put a wrong thumbnail
    // on the screen instead of letting the app fall back to its own.
    //
    // The BitmapParams-bearing overloads exist because an app compiled against
    // API 30+ calls exactly those; AOSP leaves params UNTOUCHED when it returns
    // null (it only writes actualConfig on success), and so does this -- so a
    // caller that inspects params afterwards sees its own preferred config
    // rather than a fabricated "actual" one.

    public android.graphics.Bitmap getFrameAtTime(long timeUs, int option) { return null; }
    public android.graphics.Bitmap getFrameAtTime(long timeUs) { return null; }
    public android.graphics.Bitmap getFrameAtTime() { return null; }
    public android.graphics.Bitmap getFrameAtTime(long timeUs, int option, BitmapParams params) {
        java.util.Objects.requireNonNull(params, "params must not be null");
        return null;
    }
    public android.graphics.Bitmap getScaledFrameAtTime(long timeUs, int option,
                                                        int dstWidth, int dstHeight) { return null; }
    public android.graphics.Bitmap getScaledFrameAtTime(long timeUs, int option,
            int dstWidth, int dstHeight, BitmapParams params) {
        java.util.Objects.requireNonNull(params, "params must not be null");
        return null;
    }
    public android.graphics.Bitmap getFrameAtIndex(int frameIndex) { return null; }
    public android.graphics.Bitmap getFrameAtIndex(int frameIndex, BitmapParams params) {
        java.util.Objects.requireNonNull(params, "params must not be null");
        return null;
    }
    /** AOSP: @NonNull -- "@throws IllegalStateException if the container does
     *  not have video or image tracks". None does here (audio-only demuxer), so
     *  that exception is the honest answer; a null from an @NonNull method would
     *  NPE somewhere else in the app instead. */
    public java.util.List<android.graphics.Bitmap> getFramesAtIndex(int frameIndex,
            int numFrames) {
        throw new IllegalStateException("The source has no video or image track");
    }
    public java.util.List<android.graphics.Bitmap> getFramesAtIndex(int frameIndex,
            int numFrames, BitmapParams params) {
        java.util.Objects.requireNonNull(params, "params must not be null");
        throw new IllegalStateException("The source has no video or image track");
    }
    public android.graphics.Bitmap getImageAtIndex(int imageIndex) { return null; }
    public android.graphics.Bitmap getImageAtIndex(int imageIndex, BitmapParams params) {
        java.util.Objects.requireNonNull(params, "params must not be null");
        return null;
    }
    public android.graphics.Bitmap getPrimaryImage() { return null; }
    public android.graphics.Bitmap getPrimaryImage(BitmapParams params) {
        java.util.Objects.requireNonNull(params, "params must not be null");
        return null;
    }

    /** API 30. Lets the caller ask for a particular Bitmap.Config and learn
     *  which one was actually produced. AOSP's default preferred config is
     *  ARGB_8888, and actualConfig starts equal to it. */
    public static final class BitmapParams {
        private android.graphics.Bitmap.Config mPreferred =
                android.graphics.Bitmap.Config.ARGB_8888;
        private android.graphics.Bitmap.Config mActual =
                android.graphics.Bitmap.Config.ARGB_8888;

        public BitmapParams() { }

        public void setPreferredConfig(android.graphics.Bitmap.Config config) {
            mPreferred = java.util.Objects.requireNonNull(config, "config must not be null");
        }
        public android.graphics.Bitmap.Config getPreferredConfig() { return mPreferred; }
        /** AOSP writes this only when a bitmap was actually produced; nothing
         *  here produces one, so it keeps its initial value. */
        public android.graphics.Bitmap.Config getActualConfig() { return mActual; }
    }
    public byte[] getEmbeddedPicture() { return null; }
    public void release() { closeDemux(); mPath = null; }
    @Override public void close() { release(); }

    private void closeDemux() {
        if (mDemux != 0) { com.macapk.loader.NativeMedia.nativeDemuxClose(mDemux); mDemux = 0; }
    }

    private String mimeTypeFromPath() {
        if (mPath == null) return null;
        int slash = Math.max(mPath.lastIndexOf('/'), mPath.lastIndexOf('\\'));
        int dot = mPath.lastIndexOf('.');
        if (dot <= slash || dot == mPath.length() - 1) return null;
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(mPath.substring(dot + 1));
    }
}
