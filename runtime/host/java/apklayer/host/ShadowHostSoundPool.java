package apklayer.host;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowSoundPool;

/**
 * SoundPool that loads and plays (PLAN P5.2): Robolectric's version never reports a load as complete and plays
 * nothing. Sounds become files (raw resources are extracted once), onLoadComplete is posted, and play() starts
 * gst-play-1.0 for the file.
 */
@Implements(android.media.SoundPool.class)
public class ShadowHostSoundPool extends ShadowSoundPool {
    @org.robolectric.annotation.RealObject private android.media.SoundPool real;
    private final Map<Integer, File> files = new HashMap<>();
    private final Map<Integer, Process> streams = new HashMap<>();
    private int nextStream = 1;
    private android.media.SoundPool.OnLoadCompleteListener loadListener;

    @Implementation @Override protected int load(String path, int priority) {
        int id = super.load(path, priority);
        files.put(id, new File(path));
        completeLater(id);
        return id;
    }

    @Implementation @Override protected int load(Context context, int resId, int priority) {
        int id = super.load(context, resId, priority);
        try (InputStream in = context.getResources().openRawResource(resId)) {
            File f = File.createTempFile("sound" + resId + "-", ".bin");
            f.deleteOnExit();
            Files.copy(in, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            files.put(id, f);
        } catch (IOException | RuntimeException e) {
            System.out.println("[apklayer] cannot load sound " + resId + ": " + e);
        }
        completeLater(id);
        return id;
    }

    @Implementation @Override protected void setOnLoadCompleteListener(android.media.SoundPool.OnLoadCompleteListener l) {
        super.setOnLoadCompleteListener(l);
        loadListener = l;
    }

    private void completeLater(int id) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (loadListener != null) loadListener.onLoadComplete(real, id, files.containsKey(id) ? 0 : 1);
        });
    }

    @Implementation(maxSdk = 33)
    @Override protected int _play(int soundId, float left, float right, int priority, int loop, float rate) {
        super._play(soundId, left, right, priority, loop, rate);
        File f = files.get(soundId);
        if (f == null) return 0;
        try {
            int stream = nextStream++;
            streams.put(stream, new ProcessBuilder("gst-play-1.0", "--quiet", "--no-interactive", f.getPath())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start());
            return stream;
        } catch (IOException e) {
            return 0;
        }
    }

    @Implementation protected void _stop(int streamId) {
        Process p = streams.remove(streamId);
        if (p != null) p.destroy();
    }
}
