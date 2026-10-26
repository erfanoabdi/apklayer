package hello.media;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** P5.2 sample: writes a 2 s 440 Hz tone as a WAV file and plays it with MediaPlayer, logging what the app sees. */
public class Main extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            File wav = new File(getCacheDir(), "tone.wav");
            int rate = 22050, n = rate * 2;
            ByteBuffer d = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN);
            d.put("RIFF".getBytes()).putInt(36 + n * 2).put("WAVEfmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                    .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(n * 2);
            for (int i = 0; i < n; i++) d.putShort((short) (Math.sin(2 * Math.PI * 440 * i / rate) * 8000));
            try (FileOutputStream out = new FileOutputStream(wav)) { out.write(d.array()); }

            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(wav.getPath());
            mp.setOnCompletionListener(m -> System.out.println("[hello-media] completed"));
            mp.setOnPreparedListener(m -> {
                System.out.println("[hello-media] prepared, duration=" + m.getDuration());
                m.start();
            });
            mp.prepareAsync();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
