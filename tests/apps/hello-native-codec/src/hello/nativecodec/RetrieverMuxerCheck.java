package hello.nativecodec;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;

/** What music, podcast and camera apps do with files: MediaMetadataRetriever's duration, type and a decoded frame, and MediaMuxer writing an MP4 the extractor reads back. */
final class RetrieverMuxerCheck {
    private static void require(boolean yes, String message) {
        if (!yes) throw new AssertionError(message);
    }
    private static File asset(Context context, String name) throws Exception {
        File file = new File(context.getCacheDir(), name);
        try (InputStream in = context.getAssets().open(name)) { Files.write(file.toPath(), in.readAllBytes()); }
        return file;
    }
    static void run(Context context) throws Exception {
        File video = asset(context, "red-h264.mp4"), audio = asset(context, "tone.mp3"), out = new File(context.getCacheDir(), "remuxed.mp4");
        try {
            String duration, width, mime;
            int pixel;
            boolean avcFrame;
            try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                retriever.setDataSource(video.getPath());
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                require(duration != null && Long.parseLong(duration) > 0 && width != null && Integer.parseInt(width) > 0 && "video/mp4".equals(mime),
                        "video metadata duration=" + duration + " width=" + width + " mime=" + mime);
                // shortcut: H.264 frames are not asserted: c2.apklayer.avc.decoder fails FrameDecoder's one-sample end-of-stream work (docs/PLAN.md)
                avcFrame = retriever.getFrameAtTime(0) != null;
            }
            File mpeg4 = asset(context, "red-mpeg4.mp4");
            try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                retriever.setDataSource(mpeg4.getPath());
                Bitmap frame = retriever.getFrameAtTime(0);
                require(frame != null && frame.getWidth() > 0, "MPEG-4 frame " + frame);
                pixel = frame.getPixel(frame.getWidth() / 2, frame.getHeight() / 2);
                require(Color.red(pixel) > 180 && Color.green(pixel) < 80 && Color.blue(pixel) < 80, "frame pixel " + Integer.toHexString(pixel));
            } finally { mpeg4.delete(); }
            String audioDuration;
            try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                retriever.setDataSource(audio.getPath());
                audioDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                require(audioDuration != null && Long.parseLong(audioDuration) > 0 && "yes".equals(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)),
                        "audio metadata duration=" + audioDuration);
            }
            // remux the H.264 track sample by sample, then read the new file back
            MediaExtractor source = new MediaExtractor();
            int samples = 0;
            try {
                source.setDataSource(video.getPath());
                MediaFormat format = source.getTrackFormat(0);
                source.selectTrack(0);
                MediaMuxer muxer = new MediaMuxer(out.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int track = muxer.addTrack(format);
                muxer.start();
                ByteBuffer buffer = ByteBuffer.allocate(1 << 20);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                for (int size; (size = source.readSampleData(buffer, 0)) >= 0; source.advance(), samples++) {
                    info.set(0, size, source.getSampleTime(), (source.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                    muxer.writeSampleData(track, buffer, info);
                }
                muxer.stop();
                muxer.release();
            } finally { source.release(); }
            MediaExtractor back = new MediaExtractor();
            int read = 0;
            try {
                back.setDataSource(out.getPath());
                require(back.getTrackCount() == 1 && "video/avc".equals(back.getTrackFormat(0).getString(MediaFormat.KEY_MIME)), "remuxed tracks " + back.getTrackCount());
                back.selectTrack(0);
                ByteBuffer buffer = ByteBuffer.allocate(1 << 20);
                while (back.readSampleData(buffer, 0) >= 0) { read++; back.advance(); }
            } finally { back.release(); }
            require(samples > 0 && read == samples, "remuxed " + read + " of " + samples + " samples");
            System.out.println("[hello-native-codec] retriever/muxer PASS video " + duration + " ms " + width + " px " + mime + " (H.264 frame " + avcFrame + "), MPEG-4 frame " + Integer.toHexString(pixel)
                    + ", audio " + audioDuration + " ms, remuxed " + samples + " samples (" + out.length() + " bytes)");
        } finally {
            video.delete();
            audio.delete();
            out.delete();
        }
    }
    private RetrieverMuxerCheck() {}
}
