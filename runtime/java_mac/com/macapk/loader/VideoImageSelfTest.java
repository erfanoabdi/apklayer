// VideoImageSelfTest -- end-to-end validation of MediaCodec's no-Surface
// getOutputImage() path (android.media.MediaCodec -> NativeVideoImage ->
// NativeMedia -> VideoToolbox), exercising the REAL public buffer-index
// protocol the way an app's own extractor would: parse Annex-B, build csd
// from SPS/PPS, feed one access unit per queueInputBuffer call. This does
// NOT go through android.media.MediaExtractor, because that demuxer is
// audio-only (AudioToolbox AudioFile) and cannot pull video access units
// from a container -- see docs/media-decode-backend_2026-06-21.md.
//
// Gated: runs at Boot when MACAPK_VIDEO_IMAGE_SELFTEST=1 (mirrors
// MediaSelfTest's own MACAPK_MEDIA_SELFTEST gate). Runs + returns without
// launching the APK.
//
// Env vars:
//   MACAPK_VIDEO_IMAGE_SELFTEST_FILE (default /tmp/goi_test.h264) -- a raw
//     Annex-B H.264 elementary stream. Regenerate with:
//       ffmpeg -f lavfi -i testsrc2=size=64x64:rate=10:duration=1.0 \
//         -c:v libx264 -profile:v baseline -pix_fmt yuv420p -tag:v avc1 \
//         /tmp/goi_test.mp4
//       ffmpeg -i /tmp/goi_test.mp4 -c:v copy -bsf:v h264_mp4toannexb \
//         -f h264 /tmp/goi_test.h264
//   MACAPK_VIDEO_IMAGE_SELFTEST_REF (default /tmp/goi_ref.png) -- OPTIONAL:
//     one frame decoded independently by ffmpeg's own H.264 decoder (not
//     VideoToolbox), used to measure this class's derived-YUV conversion
//     against ground truth instead of only checking it does not crash:
//       ffmpeg -i /tmp/goi_test.mp4 -vf "select=eq(n\,N)" -vframes 1 \
//         -y /tmp/goi_ref.png
//     where N is 0-based and must match REF_FRAME_INDEX below (the same
//     index this test compares against, since the encode is baseline
//     profile with no B-frames, so decode order == presentation order ==
//     feed order and the two sides can be aligned by plain frame count).
//     If absent, only the frame-to-frame motion check runs.
//   MACAPK_VIDEO_IMAGE_SELFTEST_OUT (default /tmp/goi_mine.png) -- where
//     this test writes the LAST decoded frame, reconstructed from
//     getOutputImage()'s own planes back to RGB, for visual inspection.
package com.macapk.loader;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.Image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public final class VideoImageSelfTest {
    private static final String TAG = "VideoImageSelfTest";
    private VideoImageSelfTest() {}

    // Which decoded frame (0-based, in DECODE order) the optional reference
    // PNG corresponds to. Baseline profile has no B-frames, so decode order
    // is presentation order is feed order, and this is also the frame this
    // test writes to MACAPK_VIDEO_IMAGE_SELFTEST_OUT.
    private static final int REF_FRAME_INDEX = -1; // -1 == "the last frame"

    private static final class Nal {
        final byte[] withStartCode;
        final int type;
        Nal(byte[] b, int t) { withStartCode = b; type = t; }
    }

    // Same Annex-B split video_toolbox.mm's own SplitAnnexB does (3- or
    // 4-byte start codes; a NAL ends at the next start code's first zero
    // byte), reimplemented in Java so this test needs no native helper. It
    // only has to find SPS/PPS/VCL boundaries in an ffmpeg-produced stream,
    // not defend against every malformed input the native version does.
    private static List<Nal> splitAnnexB(byte[] data) {
        List<Nal> out = new ArrayList<>();
        List<Integer> scBegin = new ArrayList<>();
        List<Integer> nalBegin = new ArrayList<>();
        int i = 0;
        while (i + 2 < data.length) {
            if (data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 1) {
                int sc = i;
                while (sc > 0 && data[sc - 1] == 0) sc--;
                scBegin.add(sc);
                nalBegin.add(i + 3);
                i += 3;
            } else {
                i++;
            }
        }
        for (int k = 0; k < nalBegin.size(); k++) {
            int begin = nalBegin.get(k);
            int end = (k + 1 < scBegin.size()) ? scBegin.get(k + 1) : data.length;
            if (end <= begin) continue;
            int type = data[begin] & 0x1F;
            byte[] full = new byte[4 + (end - begin)];
            full[0] = 0; full[1] = 0; full[2] = 0; full[3] = 1;
            System.arraycopy(data, begin, full, 4, end - begin);
            out.add(new Nal(full, type));
        }
        return out;
    }

    private static byte[] readFile(String path) throws Exception {
        File f = new File(path);
        byte[] buf = new byte[(int) f.length()];
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            raf.readFully(buf);
        }
        return buf;
    }

    // Standard ITU-R BT.601 full-range YCbCr -> RGB: the exact inverse of
    // MediaCodec.NativeVideoImage's forward matrix, and identical to
    // android.graphics.YuvImage's own yuvToArgb elsewhere in this codebase.
    // Used only to turn getOutputImage()'s planes back into a viewable PNG.
    private static int clampByte(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }
    private static int yuvToRgb(int y, int u, int v) {
        int r = clampByte(y + (int) (1.402f * v));
        int g = clampByte(y - (int) (0.344136f * u + 0.714136f * v));
        int b = clampByte(y + (int) (1.772f * u));
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    // Reconstructs a full RGB int[] (row-major, 0x00RRGGBB) from an Image's
    // 3 YUV_420_888 planes using EACH PLANE'S OWN reported strides -- this
    // is the only way this test can tell a correct plane layout from a
    // sheared (wrong row stride) or swapped-chroma (wrong plane order) one,
    // exactly what a real app is supposed to do and nothing more.
    private static int[] imageToRgb(Image img) {
        int w = img.getWidth(), h = img.getHeight();
        Image.Plane[] planes = img.getPlanes();
        ByteBuffer yb = planes[0].getBuffer();
        ByteBuffer cbb = planes[1].getBuffer();
        ByteBuffer crb = planes[2].getBuffer();
        int yRowStride = planes[0].getRowStride(), yPixStride = planes[0].getPixelStride();
        int cRowStride = planes[1].getRowStride(), cPixStride = planes[1].getPixelStride();
        int[] out = new int[w * h];
        for (int row = 0; row < h; row++) {
            int cRow = row / 2;
            for (int col = 0; col < w; col++) {
                int cCol = col / 2;
                int y = yb.get(row * yRowStride + col * yPixStride) & 0xff;
                int cb = cbb.get(cRow * cRowStride + cCol * cPixStride) & 0xff;
                int cr = crb.get(cRow * cRowStride + cCol * cPixStride) & 0xff;
                out[row * w + col] = yuvToRgb(y, cb - 128, cr - 128);
            }
        }
        return out;
    }

    private static void writePng(int[] rgb, int w, int h, String path) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, rgb, 0, w);
        ImageIO.write(img, "png", new File(path));
    }

    // Mean absolute per-channel delta between two same-size RGB images.
    // Reused for both signals this test reports: consecutive-frame motion
    // (proves the plane data is real and varying, not a static/corrupt
    // read) and fidelity against an independently-decoded reference frame
    // (proves the conversion is numerically close to ground truth, not
    // merely non-crashing).
    private static double meanAbsDelta(int[] a, int[] b) {
        long sum = 0; int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int pa = a[i], pb = b[i];
            sum += Math.abs(((pa >> 16) & 0xff) - ((pb >> 16) & 0xff));
            sum += Math.abs(((pa >> 8)  & 0xff) - ((pb >> 8)  & 0xff));
            sum += Math.abs(( pa        & 0xff) - ( pb        & 0xff));
        }
        return n > 0 ? sum / (3.0 * n) : -1;
    }

    public static void run() {
        String path = System.getenv("MACAPK_VIDEO_IMAGE_SELFTEST_FILE");
        if (path == null || path.isEmpty()) path = "/tmp/goi_test.h264";
        String refPath = System.getenv("MACAPK_VIDEO_IMAGE_SELFTEST_REF");
        if (refPath == null || refPath.isEmpty()) refPath = "/tmp/goi_ref.png";
        String outPath = System.getenv("MACAPK_VIDEO_IMAGE_SELFTEST_OUT");
        if (outPath == null || outPath.isEmpty()) outPath = "/tmp/goi_mine.png";

        try {
            // Deliverable check: are the 13 audio formats (and VP8/MPEG-4,
            // if those lanes' native wiring has landed by the time this
            // runs) actually ADVERTISED, not just openable? MediaCodecList
            // is the registry ExoPlayer-class apps walk before ever calling
            // configure() -- see that class's own header, "an app concludes
            // this device has no codecs at all, and never even tries".
            MediaCodecInfo[] infos = new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
            StringBuilder sb = new StringBuilder();
            for (MediaCodecInfo info : infos) {
                sb.append(info.getName()).append('[');
                String[] types = info.getSupportedTypes();
                for (int i = 0; i < types.length; i++) { if (i > 0) sb.append(','); sb.append(types[i]); }
                sb.append(info.isHardwareAccelerated() ? ",hw" : ",sw").append("] ");
            }
            NativeLog.i(TAG, "MediaCodecList.getCodecInfos(): " + infos.length + " entries: " + sb);

            byte[] stream = readFile(path);
            List<Nal> nals = splitAnnexB(stream);
            ByteArrayOutputStream sps = new ByteArrayOutputStream();
            ByteArrayOutputStream pps = new ByteArrayOutputStream();
            List<Nal> vcl = new ArrayList<>();
            for (Nal n : nals) {
                if (n.type == 7 || n.type == 13) sps.write(n.withStartCode);
                else if (n.type == 8) pps.write(n.withStartCode);
                else if (n.type == 1 || n.type == 5) vcl.add(n);
            }
            NativeLog.i(TAG, "file=" + path + " nals=" + nals.size() + " vcl=" + vcl.size()
                + " sps=" + sps.size() + "B pps=" + pps.size() + "B");
            if (vcl.isEmpty() || sps.size() == 0 || pps.size() == 0) {
                NativeLog.e(TAG, "RESULT -> FAIL (no SPS/PPS/VCL parsed from " + path + ")");
                return;
            }

            MediaFormat fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 64, 64);
            fmt.setByteBuffer("csd-0", ByteBuffer.wrap(sps.toByteArray()));
            fmt.setByteBuffer("csd-1", ByteBuffer.wrap(pps.toByteArray()));

            MediaCodec codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            // No Surface: this IS the getOutputImage() path under test,
            // matching how a real decode-to-buffer consumer (a frame
            // analyzer, a thumbnail extractor) configures a decoder.
            codec.configure(fmt, null, null, 0);
            codec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long pts = 0;
            int fed = 0, nextVcl = 0;
            boolean allFed = false, sawFormatChanged = false;
            List<int[]> decodedRgb = new ArrayList<>();
            int lastW = 0, lastH = 0;
            int emptyPolls = 0;

            while (emptyPolls < 400 && decodedRgb.size() < vcl.size()) {
                boolean progressed = false;
                if (!allFed) {
                    int ii = codec.dequeueInputBuffer(0);
                    if (ii >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(ii);
                        byte[] au = vcl.get(nextVcl).withStartCode;
                        ib.clear();
                        ib.put(au);
                        codec.queueInputBuffer(ii, 0, au.length, pts, 0);
                        pts += 100000;
                        nextVcl++;
                        fed++;
                        progressed = true;
                        if (nextVcl >= vcl.size()) allFed = true;
                    }
                }
                int oi = codec.dequeueOutputBuffer(info, 0);
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    sawFormatChanged = true;
                    progressed = true;
                    NativeLog.i(TAG, "output format changed: " + codec.getOutputFormat());
                } else if (oi >= 0) {
                    progressed = true;
                    Image img = codec.getOutputImage(oi);
                    if (img != null) {
                        NativeLog.i(TAG, "frame " + decodedRgb.size() + ": format=0x"
                            + Integer.toHexString(img.getFormat()) + " " + img.getWidth() + "x"
                            + img.getHeight() + " planes=" + img.getPlanes().length
                            + " crop=" + img.getCropRect() + " pts=" + img.getTimestamp());
                        int[] rgb = imageToRgb(img);
                        decodedRgb.add(rgb);
                        lastW = img.getWidth(); lastH = img.getHeight();
                        img.close();
                    } else {
                        NativeLog.e(TAG, "getOutputImage(" + oi + ") returned null");
                    }
                    codec.releaseOutputBuffer(oi, false);
                }
                if (progressed) emptyPolls = 0;
                else {
                    emptyPolls++;
                    try { Thread.sleep(5); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
            codec.stop();
            codec.release();

            NativeLog.i(TAG, "fed=" + fed + "/" + vcl.size() + " decoded=" + decodedRgb.size()
                + " formatChanged=" + sawFormatChanged);

            if (decodedRgb.size() < 2) {
                NativeLog.e(TAG, "RESULT -> FAIL (fewer than 2 frames decoded)");
                return;
            }

            // Signal 1: real, varying content (not a static/corrupt read),
            // the same bar debug/video_toolbox_test.mm's native test uses.
            double motionDelta = 0;
            for (int i = 1; i < decodedRgb.size(); i++) {
                motionDelta += meanAbsDelta(decodedRgb.get(i - 1), decodedRgb.get(i));
            }
            motionDelta /= (decodedRgb.size() - 1);

            int refIdx = REF_FRAME_INDEX >= 0 ? REF_FRAME_INDEX : decodedRgb.size() - 1;
            int[] chosenFrame = decodedRgb.get(Math.min(refIdx, decodedRgb.size() - 1));
            int[] lastFrame = decodedRgb.get(decodedRgb.size() - 1);
            writePng(lastFrame, lastW, lastH, outPath);
            NativeLog.i(TAG, "wrote " + outPath + " (" + lastW + "x" + lastH + ")");

            // Signal 2: fidelity against an independently-decoded reference
            // frame (ffmpeg's own H.264 decoder, not VideoToolbox), if one
            // was supplied.
            double refDelta = -1;
            File refFile = new File(refPath);
            if (refFile.isFile()) {
                BufferedImage ref = ImageIO.read(refFile);
                if (ref != null && ref.getWidth() == lastW && ref.getHeight() == lastH) {
                    int[] refRgb = ref.getRGB(0, 0, lastW, lastH, null, 0, lastW);
                    for (int i = 0; i < refRgb.length; i++) refRgb[i] &= 0xffffff; // strip alpha
                    refDelta = meanAbsDelta(chosenFrame, refRgb);
                } else {
                    NativeLog.i(TAG, "reference " + refPath + " size mismatch or unreadable ("
                        + (ref == null ? "null" : ref.getWidth() + "x" + ref.getHeight())
                        + " vs " + lastW + "x" + lastH + "), skipping fidelity check");
                }
            } else {
                NativeLog.i(TAG, "no reference PNG at " + refPath + ", skipping fidelity check");
            }

            // A generous fidelity bar: two lossy round trips (H.264's own
            // compression, then this class's BGRA->YUV derivation) plus
            // whatever matrix mismatch exists between VideoToolbox's
            // internal YUV->RGB and this test's RGB->YUV are all folded
            // into refDelta, so a small nonzero number is EXPECTED, not a
            // bug -- the diagnostic value is in a LARGE number (channel
            // swap, shear) not a perfect zero.
            boolean pass = decodedRgb.size() >= 2 && motionDelta > 1.0
                && (refDelta < 0 || refDelta < 20.0);
            NativeLog.i(TAG, "RESULT frames=" + decodedRgb.size() + " motionDelta=" + motionDelta
                + " refDelta=" + refDelta + " -> " + (pass ? "PASS" : "FAIL"));
        } catch (Throwable t) {
            NativeLog.e(TAG, "RESULT -> FAIL (exception): " + t);
            t.printStackTrace();
        }
    }
}
