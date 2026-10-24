// MediaSelfTest -- end-to-end validation of the audio decode path
// (android.media.MediaExtractor -> MediaCodec -> NativeMedia -> AudioToolbox).
// Gated: runs at Boot when MACAPK_MEDIA_SELFTEST=1 (file via
// MACAPK_MEDIA_SELFTEST_FILE, default /tmp/mdtest.m4a), logs frames + RMS, and
// returns (the APK is not launched). Exercises the FULL JVM->JNI->native chain
// the way a real app drives it (the synchronous dequeue/queue decode loop).
package com.macapk.loader;

import android.media.MediaExtractor;
import android.media.MediaCodec;
import android.media.MediaFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class MediaSelfTest {
    private static final String TAG = "MediaSelfTest";
    private MediaSelfTest() {}

    public static void run() {
        String path = System.getenv("MACAPK_MEDIA_SELFTEST_FILE");
        if (path == null || path.isEmpty()) path = "/tmp/mdtest.m4a";
        try {
            MediaExtractor ex = new MediaExtractor();
            ex.setDataSource(path);
            int tracks = ex.getTrackCount();
            NativeLog.i(TAG, "file=" + path + " tracks=" + tracks);
            if (tracks < 1) { NativeLog.e(TAG, "RESULT -> FAIL (no tracks)"); return; }

            MediaFormat fmt = ex.getTrackFormat(0);
            String mime = fmt.getString(MediaFormat.KEY_MIME);
            int sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE, 0);
            int ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 0);
            boolean hasCsd = fmt.getByteBuffer("csd-0", null) != null;
            NativeLog.i(TAG, "format mime=" + mime + " sr=" + sr + " ch=" + ch + " csd0=" + hasCsd);

            ex.selectTrack(0);
            MediaCodec codec = MediaCodec.createDecoderByType(mime);
            codec.configure(fmt, null, null, 0);
            codec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long totalFrames = 0, sampleCount = 0; double sumSq = 0;
            boolean inEos = false, outEos = false; int guard = 0;
            while (!outEos && guard++ < 1_000_000) {
                if (!inEos) {
                    int ii = codec.dequeueInputBuffer(0);
                    if (ii >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(ii);
                        int n = (ib != null) ? ex.readSampleData(ib, 0) : -1;
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inEos = true;
                        } else {
                            codec.queueInputBuffer(ii, 0, n, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int oi = codec.dequeueOutputBuffer(info, 0);
                if (oi >= 0) {
                    ByteBuffer ob = codec.getOutputBuffer(oi);
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset); ob.limit(info.offset + info.size);
                        ByteBuffer s16 = ob.slice().order(ByteOrder.LITTLE_ENDIAN);
                        while (s16.remaining() >= 2) { short v = s16.getShort(); sumSq += (double) v * v; sampleCount++; }
                        totalFrames += (info.size / 2) / Math.max(1, ch);
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outEos = true;
                    codec.releaseOutputBuffer(oi, false);
                }
            }
            long rms = sampleCount > 0 ? (long) Math.sqrt(sumSq / sampleCount) : 0;
            boolean pass = totalFrames > 1000 && rms > 1;
            NativeLog.i(TAG, "RESULT frames=" + totalFrames + " rms=" + rms
                + " -> " + (pass ? "PASS" : "FAIL"));
            codec.stop(); codec.release(); ex.release();
        } catch (Throwable t) {
            NativeLog.e(TAG, "RESULT -> FAIL (exception): " + t);
            t.printStackTrace();
        }
    }
}
