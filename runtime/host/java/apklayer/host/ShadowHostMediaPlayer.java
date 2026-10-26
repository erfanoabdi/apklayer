package apklayer.host;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowMediaPlayer;

/**
 * MediaPlayer with real playback (PLAN P5.2): Robolectric's state machine and listener timing stay as they are
 * (the duration comes from gst-discoverer), and the transport calls drive a GStreamer engine process
 * (bridge/media/apklayer-media.py) that plays the sound.
 */
@Implements(android.media.MediaPlayer.class)
public class ShadowHostMediaPlayer extends ShadowMediaPlayer {
    private static String pendingUri;
    private String uri;
    private Process engine;
    private OutputStream in;
    private boolean loaded;

    /** The MediaInfoProvider: how long is the data source that setDataSource just got. */
    static MediaInfo mediaInfo(org.robolectric.shadows.util.DataSource ds) {
        return new MediaInfo(pendingUri == null ? 0 : durationMs(pendingUri), 0);
    }

    private static int durationMs(String uri) {
        try {
            Process p = new ProcessBuilder("gst-discoverer-1.0", "-t", "10", uri).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                for (String line; (line = r.readLine()) != null; ) {
                    line = line.trim();
                    if (line.startsWith("Duration: ")) { // Duration: 0:03:25.123456789
                        String[] t = line.substring(10).split("[:.]");
                        return (Integer.parseInt(t[0]) * 3600 + Integer.parseInt(t[1]) * 60 + Integer.parseInt(t[2])) * 1000 + Integer.parseInt(t[3].substring(0, 3));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("[apklayer] cannot probe " + uri + ": " + e.getMessage());
        }
        return 0;
    }

    private static String toUri(String path) {
        return path.contains("://") ? path : new java.io.File(path).toURI().toString();
    }

    @Implementation protected void setDataSource(String path) throws IOException { pendingUri = uri = toUri(path); super.setDataSource(path); }

    @Implementation protected void setDataSource(String path, Map<String, String> headers) throws IOException {
        pendingUri = uri = toUri(path);
        super.setDataSource(path, headers);
    }

    @Implementation protected void setDataSource(Context context, Uri u) throws IOException {
        pendingUri = uri = "content".equals(u.getScheme()) || u.getScheme() == null ? null : u.toString();
        super.setDataSource(context, u);
    }

    @Implementation protected void setDataSource(Context context, Uri u, Map<String, String> headers) throws IOException {
        pendingUri = uri = "content".equals(u.getScheme()) || u.getScheme() == null ? null : u.toString();
        super.setDataSource(context, u, headers);
    }

    /** setDisplay/setSurface: the engine writes the video frames where HostSurfaces composes them over the view. */
    @Implementation protected void _setVideoSurface(android.view.Surface surface) {
        String dir = System.getenv("APKLAYER_GLDIR");
        if (surface != null && dir != null) send("surface " + java.nio.file.Path.of(dir, surface.hashCode() + ".rgba"));
    }

    private void send(String line) {
        if (uri == null) return;
        try {
            if (engine == null) {
                engine = new ProcessBuilder("python3", System.getProperty("apklayer.media", "bridge/media/apklayer-media.py"))
                        .redirectError(ProcessBuilder.Redirect.INHERIT).start();
                in = engine.getOutputStream();
            }
            if (!loaded) {
                loaded = true;
                in.write(("load " + uri + "\n").getBytes(StandardCharsets.UTF_8));
            }
            in.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            in.flush();
        } catch (IOException e) {
            System.out.println("[apklayer] media engine: " + e.getMessage());
        }
    }

    private void quit() {
        if (engine != null) engine.destroy();
        engine = null;
        loaded = false;
    }

    @Implementation @Override protected void start() { super.start(); send("play"); }
    @Implementation @Override protected void _pause() { super._pause(); send("pause"); }
    @Implementation @Override protected void _stop() { super._stop(); if (engine != null) send("stop"); loaded = false; }
    @Implementation @Override protected void seekTo(int ms) { super.seekTo(ms); send("seek " + ms); }
    @Implementation @Override protected void setVolume(float l, float r) { super.setVolume(l, r); send("volume " + Math.max(l, r)); }
    @Implementation @Override protected void setLooping(boolean looping) { super.setLooping(looping); send("loop " + (looping ? 1 : 0)); }
    @Implementation @Override protected void _reset() { super._reset(); quit(); }
    @Implementation @Override protected void _release() { super._release(); quit(); }
}
