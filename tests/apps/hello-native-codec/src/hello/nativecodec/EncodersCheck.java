package hello.nativecodec;

import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** The tree's software encoders (AAC, Opus, FLAC, H.264) through MediaCodec, each checked by decoding the output again (round trip). */
final class EncodersCheck {
    private static void require(boolean yes, String message) {
        if (!yes) throw new AssertionError(message);
    }

    private static final class Packet {
        final byte[] data; final long pts; final int flags;
        Packet(byte[] data, long pts, int flags) { this.data = data; this.pts = pts; this.flags = flags; }
    }

    static void run() throws Exception {
        audio("audio/mp4a-latm", "c2.android.aac.encoder", 48000, 2, 128000, "c2.apklayer.aac.decoder");
        audio("audio/opus", "c2.android.opus.encoder", 48000, 2, 64000, "c2.android.opus.decoder");
        audio("audio/flac", "c2.android.flac.encoder", 48000, 2, 0, "c2.android.flac.decoder");
        try { video(); } catch (RuntimeException e) { e.printStackTrace(System.out); throw e; }
    }

    private static List<Packet> drain(MediaCodec codec, MediaFormat[] outFormat, long budgetNs, Runnable feed, boolean[] inputDone) {
        List<Packet> packets = new ArrayList<>();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long deadline = System.nanoTime() + budgetNs;
        boolean eos = false;
        while (!eos) {
            require(System.nanoTime() < deadline, "encoder exceeded " + budgetNs / 1_000_000_000L + " s");
            feed.run();
            int out = codec.dequeueOutputBuffer(info, 10_000);
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outFormat[0] = codec.getOutputFormat();
            else if (out >= 0) {
                byte[] bytes = new byte[info.size];
                ByteBuffer b = codec.getOutputBuffer(out);
                b.position(info.offset).limit(info.offset + info.size);
                b.get(bytes);
                if (info.size > 0 || (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) packets.add(new Packet(bytes, info.presentationTimeUs, info.flags));
                eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(out, false);
            }
        }
        return packets;
    }

    private static void audio(String mime, String encoderName, int rate, int channels, int bitrate, String decoderName) throws Exception {
        MediaCodec encoder = MediaCodec.createEncoderByType(mime);
        require(encoder.getName().equals(encoderName), mime + ": encoder " + encoder.getName());
        MediaFormat format = MediaFormat.createAudioFormat(mime, rate, channels);
        if (bitrate > 0) format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        if (mime.equals("audio/mp4a-latm")) format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        if (mime.equals("audio/flac")) format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        final int total = rate * 2;
        final int[] sent = {0};
        final boolean[] done = {false};
        MediaFormat[] outFormat = new MediaFormat[1];
        List<Packet> packets;
        try {
            packets = drain(encoder, outFormat, 20_000_000_000L, () -> {
                if (done[0]) return;
                int index = encoder.dequeueInputBuffer(0);
                if (index < 0) return;
                ByteBuffer in = encoder.getInputBuffer(index);
                int frames = Math.min(1024, total - sent[0]);
                if (frames <= 0) { encoder.queueInputBuffer(index, 0, 0, sent[0] * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM); done[0] = true; return; }
                in.order(ByteOrder.LITTLE_ENDIAN);
                for (int n = 0; n < frames; n++) {
                    short v = (short) (Math.sin(2 * Math.PI * 440 * (sent[0] + n) / rate) * 8000);
                    for (int c = 0; c < channels; c++) in.putShort(v);
                }
                encoder.queueInputBuffer(index, 0, frames * channels * 2, sent[0] * 1_000_000L / rate, 0);
                sent[0] += frames;
            }, done);
        } finally { encoder.stop(); encoder.release(); }
        require(outFormat[0] != null, mime + ": no output format");
        int data = 0;
        for (Packet p : packets) if ((p.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && p.data.length > 0) data++;
        require(data >= 20, mime + ": only " + data + " encoded packets"); // FLAC blocks are 4096 samples
        // round trip through a decoder configured from the encoder's output format
        MediaCodec decoder = MediaCodec.createDecoderByType(mime);
        require(decoder.getName().equals(decoderName), mime + ": decoder " + decoder.getName());
        decoder.configure(outFormat[0], null, null, 0);
        decoder.start();
        short[] pcm = new short[rate * 4];
        int samples = 0, next = 0, outChannels = channels;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean eos = false;
        long deadline = System.nanoTime() + 20_000_000_000L;
        try {
            while (!eos) {
                require(System.nanoTime() < deadline, mime + ": round trip exceeded 20 s");
                if (next < packets.size()) {
                    int index = decoder.dequeueInputBuffer(10_000);
                    if (index >= 0) {
                        Packet p = packets.get(next++);
                        boolean last = next == packets.size();
                        decoder.getInputBuffer(index).put(p.data);
                        int flags = p.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
                        if (last) flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        decoder.queueInputBuffer(index, 0, p.data.length, p.pts, flags);
                    }
                }
                int out = decoder.dequeueOutputBuffer(info, 10_000);
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outChannels = decoder.getOutputFormat().getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                else if (out >= 0) {
                    if (info.size > 0) {
                        ByteBuffer b = decoder.getOutputBuffer(out).order(ByteOrder.LITTLE_ENDIAN);
                        b.position(info.offset).limit(info.offset + info.size);
                        while (b.remaining() >= 2 * outChannels && samples < pcm.length) { pcm[samples++] = b.getShort(); for (int c = 1; c < outChannels; c++) b.getShort(); }
                    }
                    eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(out, false);
                }
            }
        } finally { decoder.stop(); decoder.release(); }
        require(samples >= total - 4800 && samples <= total + 4800, mime + ": round trip " + samples + " samples");
        int from = samples / 2 - 24000, to = from + 48000, crossings = 0;
        double sum = 0;
        for (int n = from; n < to; n++) { sum += (double) pcm[n] * pcm[n]; if (n > from && (pcm[n - 1] < 0) != (pcm[n] < 0)) crossings++; }
        double rms = Math.sqrt(sum / (to - from)), hertz = crossings / 2.0;
        require(rms > 4500 && rms < 7000, mime + ": round trip rms " + rms);
        require(hertz > 430 && hertz < 450, mime + ": round trip " + hertz + " Hz");
        System.out.println("[hello-native-codec] ENCODER PASS " + mime + " via " + encoderName + ": " + data + " packets, round trip " + samples + " samples, rms="
                + (int) rms + ", " + (int) hertz + " Hz");
    }

    private static void video() throws Exception {
        final int w = 128, h = 96, frames = 12;
        MediaCodec encoder = MediaCodec.createEncoderByType("video/avc");
        require(encoder.getName().equals("c2.android.avc.encoder"), "AVC encoder " + encoder.getName());
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", w, h);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 400000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        final int[] sent = {0};
        final boolean[] done = {false};
        MediaFormat[] outFormat = new MediaFormat[1];
        List<Packet> packets;
        try {
            packets = drain(encoder, outFormat, 20_000_000_000L, () -> {
                if (done[0]) return;
                int index = encoder.dequeueInputBuffer(0);
                if (index < 0) return;
                long pts = sent[0] * 40_000L;
                if (sent[0] >= frames) { encoder.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); done[0] = true; return; }
                Image image = encoder.getInputImage(index);
                // solid red: Y 81, U 90, V 240
                int[] value = {81, 90, 240};
                for (int p = 0; p < 3; p++) {
                    Image.Plane plane = image.getPlanes()[p];
                    ByteBuffer b = plane.getBuffer();
                    int pw = p == 0 ? w : w / 2, ph = p == 0 ? h : h / 2;
                    for (int y = 0; y < ph; y++) for (int x = 0; x < pw; x++) b.put(y * plane.getRowStride() + x * plane.getPixelStride(), (byte) value[p]);
                }
                image.close();
                encoder.queueInputBuffer(index, 0, w * h * 3 / 2, pts, 0);
                sent[0]++;
            }, done);
        } catch (RuntimeException | AssertionError e) {
            System.out.println("[hello-native-codec] AVC encode failed first: " + e);
            e.printStackTrace(System.out);
            throw e;
        } finally { try { encoder.stop(); } catch (RuntimeException ignored) {} encoder.release(); }
        require(outFormat[0] != null && outFormat[0].containsKey("csd-0"), "AVC encoder output format has SPS/PPS");
        int data = 0;
        for (Packet p : packets) if ((p.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && p.data.length > 0) data++;
        require(data == frames, "AVC encoded frames " + data);
        // round trip: the host decoder
        MediaCodec decoder = MediaCodec.createDecoderByType("video/avc");
        MediaFormat decodeFormat = MediaFormat.createVideoFormat("video/avc", w, h);
        decodeFormat.setByteBuffer("csd-0", outFormat[0].getByteBuffer("csd-0"));
        if (outFormat[0].containsKey("csd-1")) decodeFormat.setByteBuffer("csd-1", outFormat[0].getByteBuffer("csd-1"));
        decodeFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        decoder.configure(decodeFormat, null, null, 0);
        decoder.start();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int next = 0, decoded = 0;
        double luma = 0;
        boolean eos = false;
        long deadline = System.nanoTime() + 20_000_000_000L;
        try {
            while (!eos) {
                require(System.nanoTime() < deadline, "AVC round trip exceeded 20 s");
                if (next < packets.size()) {
                    int index = decoder.dequeueInputBuffer(10_000);
                    if (index >= 0) {
                        Packet p = packets.get(next++);
                        if ((p.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) { decoder.queueInputBuffer(index, 0, 0, 0, 0); continue; }
                        boolean last = next == packets.size();
                        decoder.getInputBuffer(index).put(p.data);
                        decoder.queueInputBuffer(index, 0, p.data.length, p.pts, last ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    }
                }
                int out = decoder.dequeueOutputBuffer(info, 10_000);
                if (out >= 0) {
                    if (info.size > 0) {
                        Image image = decoder.getOutputImage(out);
                        Image.Plane y = image.getPlanes()[0];
                        ByteBuffer b = y.getBuffer();
                        int sum = 0;
                        for (int row = 0; row < image.getHeight(); row++) for (int col = 0; col < image.getWidth(); col++) sum += b.get(row * y.getRowStride() + col * y.getPixelStride()) & 255;
                        luma += (double) sum / (image.getWidth() * image.getHeight());
                        decoded++;
                        image.close();
                    }
                    eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(out, false);
                }
            }
        } finally { decoder.stop(); decoder.release(); }
        luma /= Math.max(decoded, 1);
        require(decoded == frames, "AVC round trip decoded " + decoded);
        require(luma > 50 && luma < 100, "AVC round trip luma " + luma); // a 400 kbit/s encoder of 12 tiny frames lands near, not on, the source 81
        System.out.println("[hello-native-codec] ENCODER PASS video/avc via c2.android.avc.encoder: " + data + " frames, round trip luma " + (int) luma);
    }

    private EncodersCheck() {}
}
