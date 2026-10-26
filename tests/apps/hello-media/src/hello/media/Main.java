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

            // AudioTrack: a streaming write of the same tone from a worker thread
            new Thread(() -> {
                android.media.AudioTrack t = new android.media.AudioTrack(android.media.AudioManager.STREAM_MUSIC, rate,
                        android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, 8192, android.media.AudioTrack.MODE_STREAM);
                t.play();
                byte[] pcm = java.util.Arrays.copyOfRange(d.array(), 44, 44 + rate * 2); // 0.5 s
                System.out.println("[hello-media] audiotrack wrote " + t.write(pcm, 0, pcm.length));
                t.release();
            }).start();
            // SoundPool: load the file, play once it reports loaded
            android.media.SoundPool sp = new android.media.SoundPool.Builder().setMaxStreams(2).build();
            sp.setOnLoadCompleteListener((pool, id, status) -> System.out.println("[hello-media] soundpool loaded status=" + status
                    + " stream=" + pool.play(id, 1, 1, 1, 0, 1)));
            sp.load(wav.getPath(), 1);

            // connectivity: the callback must fire for the desktop's network
            android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
            cm.registerDefaultNetworkCallback(new android.net.ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(android.net.Network n) {
                    System.out.println("[hello-media] network available, metered=" + cm.isActiveNetworkMetered()
                            + " wifi=" + (cm.getActiveNetworkInfo().getType() == android.net.ConnectivityManager.TYPE_WIFI));
                }
            });

            // DownloadManager: fetch a local file through a file: URL; the completion broadcast must arrive
            android.app.DownloadManager dm = getSystemService(android.app.DownloadManager.class);
            registerReceiver(new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context c, android.content.Intent i) {
                    System.out.println("[hello-media] download complete id=" + i.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1));
                }
            }, new android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED);
            dm.enqueue(new android.app.DownloadManager.Request(android.net.Uri.parse("http://127.0.0.1:18080/hostname"))
                    .setDestinationUri(android.net.Uri.fromFile(new File(getCacheDir(), "hostname.txt"))));

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
