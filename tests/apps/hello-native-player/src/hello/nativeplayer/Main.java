package hello.nativeplayer;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Original MediaPlayer -> remote NuPlayer -> Codec2 -> actual SurfaceView. */
public class Main extends Activity implements SurfaceHolder.Callback {
    private TextView status;
    private MediaPlayer player;
    private int completions, discontinuities;
    private long furthestMediaTime, startedAt;
    private static void require(boolean yes, String message) {
        if (!yes) throw new AssertionError(message);
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this); status.setText("Preparing original native MediaPlayer…");
        layout.addView(status, new LinearLayout.LayoutParams(-1, 70));
        SurfaceView view = new SurfaceView(this); view.getHolder().addCallback(this);
        layout.addView(view, new LinearLayout.LayoutParams(-1, -1)); setContentView(layout);
    }
    public void surfaceCreated(SurfaceHolder holder) {
        System.out.println("[hello-native-player] created valid=" + holder.getSurface().isValid());
    }
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (player != null) return;
        try {
            File source = new File(getCacheDir(), "red-h264.mp4");
            try (var input = getAssets().open("red-h264.mp4"); var output = new FileOutputStream(source)) {
                input.transferTo(output);
            }
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build());
            player.setDisplay(holder);
            player.setOnErrorListener((p, what, extra) -> { fail(new AssertionError("native player error " + what + "/" + extra)); return true; });
            player.setOnVideoSizeChangedListener((p, w, h) -> System.out.println("[hello-native-player] size=" + w + "x" + h));
            player.setOnInfoListener((p, what, extra) -> { System.out.println("[hello-native-player] info=" + what + "/" + extra); return false; });
            player.setOnPreparedListener(p -> {
                try {
                    require(p.getVideoWidth() == 64 && p.getVideoHeight() == 48, "native dimensions");
                    require(p.getDuration() >= 390 && p.getDuration() <= 410, "native duration " + p.getDuration());
                    require(p.getAudioSessionId() > 0, "native session");
                    MediaPlayer.TrackInfo[] tracks = p.getTrackInfo();
                    require(tracks.length == 1 && tracks[0].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_VIDEO,
                            "original track metadata");
                    p.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                    System.out.println("[hello-native-player] prepared tracks=" + tracks.length + " duration=" + p.getDuration());
                    startedAt = System.nanoTime();
                    p.start(); p.pause(); require(!p.isPlaying(), "native paused state"); p.start();
                } catch (Throwable error) { fail(error); }
            });
            player.setOnMediaTimeDiscontinuityListener((p, timestamp) -> {
                discontinuities++;
                furthestMediaTime = Math.max(furthestMediaTime, timestamp.getAnchorMediaTimeUs());
                System.out.println("[hello-native-player] time=" + timestamp.getAnchorMediaTimeUs()
                        + " clock=" + timestamp.getAnchorSytemNanoTime() + " rate=" + timestamp.getMediaClockRate());
            });
            player.setOnSeekCompleteListener(p -> {
                System.out.println("[hello-native-player] seek complete position=" + p.getCurrentPosition()); p.start();
            });
            player.setOnCompletionListener(p -> {
                try {
                    completions++;
                    System.out.println("[hello-native-player] completion=" + completions + " position=" + p.getCurrentPosition());
                    if (completions == 1) {
                        require(System.nanoTime() - startedAt >= 250000000L, "actual video playback pacing");
                        p.seekTo(100, MediaPlayer.SEEK_CLOSEST); return; }
                    require(discontinuities > 0 && furthestMediaTime >= 300000, "native callback Parcel timestamp " + furthestMediaTime);
                    var metrics = p.getMetrics();
                    require(metrics != null, "native metrics bundle");
                    System.out.println("[hello-native-player] metrics=" + metrics);
                    status.setText("PASS: original MediaPlayer → SurfaceView\nprepare, tracks, seek/replay, completion");
                    System.out.println("[hello-native-player] PLAYER PASS");
                } catch (Throwable error) { fail(error); }
            });
            try (FileInputStream input = new FileInputStream(source)) {
                player.setDataSource(input.getFD());
            }
            player.prepareAsync();
        } catch (Throwable error) { fail(error); }
    }
    private void fail(Throwable error) { error.printStackTrace(); System.out.println("[hello-native-player] FAIL " + error); }
    public void surfaceDestroyed(SurfaceHolder holder) { System.out.println("[hello-native-player] destroyed"); }
    @Override protected void onDestroy() {
        if (player != null) { player.release(); player = null; System.out.println("[hello-native-player] released"); }
        super.onDestroy();
    }
}
