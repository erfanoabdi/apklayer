package apklayer.host;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.view.Surface;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.robolectric.shadows.MediaCodecInfoBuilder;
import org.robolectric.shadows.ShadowMediaCodec;
import org.robolectric.shadows.ShadowMediaCodecList;

/**
 * MediaCodec decoders (PLAN P5.2). Robolectric's codec is a pass-through simulation with a plug-in point; here each
 * advertised decoder hands every input buffer to a GStreamer engine process (bridge/media/apklayer-codec.py) and
 * puts the answer in the output buffer: an RGBA frame for video, PCM for audio. A video frame reaches the app's
 * Surface when the app releases the buffer with render = true ({@link ShadowHostMediaCodec}).
 */
final class HostCodecs {
    private HostCodecs() {}

    /** name, mime, is video. */
    private static final String[][] DECODERS = {
        {"apklayer.vp9.decoder", "video/x-vnd.on2.vp9", "1"}, {"apklayer.vp8.decoder", "video/x-vnd.on2.vp8", "1"},
        {"apklayer.opus.decoder", "audio/opus", "0"}, {"apklayer.mp3.decoder", "audio/mpeg", "0"},
    };
    /** By MIME type. */
    private static final Map<String, EngineCodec> codecs = new HashMap<>();
    private static final String GLDIR = System.getenv("APKLAYER_GLDIR");

    static void install() {
        for (String[] d : DECODERS) {
            boolean video = d[2].equals("1");
            EngineCodec codec = new EngineCodec(d[1], video);
            codecs.put(d[1], codec);
            ShadowMediaCodec.CodecConfig config = new ShadowMediaCodec.CodecConfig(video ? 4 << 20 : 64 << 10, video ? 640 * 480 * 4 : 32 << 10, codec);
            ShadowMediaCodec.addDecoder(d[0], config); // createByCodecName
            ShadowMediaCodec.addDecoder(d[1], config); // createDecoderByType (the shadow looks the type up as the name)
            MediaFormat f = video ? MediaFormat.createVideoFormat(d[1], 1920, 1080) : MediaFormat.createAudioFormat(d[1], 48000, 2);
            MediaCodecInfo.CodecCapabilities caps = MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
                    .setMediaFormat(f).setIsEncoder(false)
                    .setColorFormats(video ? new int[] {MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface} : new int[0]).build();
            ShadowMediaCodecList.addCodec(MediaCodecInfoBuilder.newBuilder().setName(d[0]).setIsEncoder(false)
                    .setIsSoftwareOnly(true).setCapabilities(caps).build());
        }
    }

    /** Shows the frame in an output buffer on the codec's Surface (called when the app releases it with render). */
    static void render(MediaCodec codec, int index) {
        EngineCodec engine = codecs.get(codec.getOutputFormat().getString(MediaFormat.KEY_MIME));
        ByteBuffer frame = codec.getOutputBuffer(index);
        if (engine != null && frame != null) engine.render(frame);
    }

    private static final class EngineCodec implements ShadowMediaCodec.CodecConfig.Codec {
        final String mime;
        final boolean video;
        Process process;
        OutputStream in;
        InputStream out;
        Path surfaceFile;
        int frameW, frameH, seq;

        EngineCodec(String mime, boolean video) {
            this.mime = mime;
            this.video = video;
        }

        @Override
        public void onConfigured(MediaFormat format, Surface surface, MediaCrypto crypto, int flags) {
            if (process != null) process.destroy();
            int w = video ? format.getInteger(MediaFormat.KEY_WIDTH) : 0, h = video ? format.getInteger(MediaFormat.KEY_HEIGHT) : 0;
            double scale = video ? Math.min(1.0, Math.min(640.0 / w, 480.0 / h)) : 1;
            int tw = Math.max(2, (int) (w * scale) & ~1), th = Math.max(2, (int) (h * scale) & ~1);
            int rate = video ? 0 : format.getInteger(MediaFormat.KEY_SAMPLE_RATE), ch = video ? 0 : format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            surfaceFile = surface != null && GLDIR != null ? Path.of(GLDIR, surface.hashCode() + ".rgba") : null;
            try {
                process = new ProcessBuilder("python3", System.getProperty("apklayer.codec", "bridge/media/apklayer-codec.py"))
                        .redirectError(ProcessBuilder.Redirect.INHERIT).start();
                in = process.getOutputStream();
                out = new BufferedInputStream(process.getInputStream(), 1 << 20);
                in.write(("cfg " + mime + " " + w + " " + h + " " + tw + " " + th + " " + rate + " " + ch + "\n").getBytes(StandardCharsets.UTF_8));
                in.flush();
            } catch (IOException e) {
                System.out.println("[apklayer] cannot start the codec engine: " + e);
                process = null;
            }
        }

        @Override
        public synchronized void process(ByteBuffer input, ByteBuffer output) {
            if (process == null) return;
            try {
                byte[] data = new byte[input.remaining()];
                input.get(data);
                in.write(("in " + data.length + "\n").getBytes(StandardCharsets.UTF_8));
                in.write(data);
                in.flush();
                String[] header = readLine().split(" ");
                int size = Integer.parseInt(header[1]);
                byte[] decoded = out.readNBytes(size);
                if (size > 0 && size <= output.remaining()) {
                    output.put(decoded);
                    frameW = Integer.parseInt(header[2]);
                    frameH = Integer.parseInt(header[3]);
                }
            } catch (IOException | RuntimeException e) {
                System.out.println("[apklayer] codec engine for " + mime + ": " + e);
                process.destroy();
                process = null;
            }
        }

        private String readLine() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (int c; (c = out.read()) != '\n'; ) {
                if (c < 0) throw new IOException("engine ended");
                sb.append((char) c);
            }
            return sb.toString();
        }

        void render(ByteBuffer frame) {
            if (surfaceFile == null || frameW == 0 || frame.remaining() < frameW * frameH * 4) return;
            try {
                ByteBuffer file = ByteBuffer.allocate(12 + frameW * frameH * 4).order(ByteOrder.LITTLE_ENDIAN);
                file.putInt(frameW).putInt(frameH).putInt(++seq);
                ByteBuffer src = frame.duplicate();
                src.limit(src.position() + frameW * frameH * 4);
                file.put(src);
                Path tmp = Path.of(surfaceFile + ".part");
                Files.write(tmp, file.array());
                Files.move(tmp, surfaceFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // the frame is dropped
            }
        }
    }
}
