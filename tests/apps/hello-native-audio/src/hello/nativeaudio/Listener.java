package hello.nativeaudio;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.IBinder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

/** Another process of the app (android:process=":listener"): it must hear of the main process's call audio changes (HubCallAudio's
 *  ENDPOINT_CALL_AUDIO, ACTION_MICROPHONE_MUTE_CHANGED), as an app does of another app's. Writes what it heard to files/listener. */
public final class Listener extends Service {
    private static File log(Context context) { return new File(context.getFilesDir(), "listener"); }

    private void note(String line) {
        try (FileWriter out = new FileWriter(log(this), true)) { out.write(line + "\n"); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }

    @Override public void onCreate() {
        AudioManager audio = getSystemService(AudioManager.class);
        audio.addOnModeChangedListener(getMainExecutor(), mode -> note("mode " + mode));
        audio.addOnCommunicationDeviceChangedListener(getMainExecutor(), device -> note("device " + (device == null ? "none" : device.getType())));
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { note("mute " + getSystemService(AudioManager.class).isMicrophoneMute()); }
        }, new IntentFilter(AudioManager.ACTION_MICROPHONE_MUTE_CHANGED), RECEIVER_EXPORTED);
        note("ready");
    }
    @Override public IBinder onBind(Intent intent) { return null; }

    /** From the main process: starts the listener and waits (at most 15 s) until it listens. */
    static void start(Context context) throws Exception {
        log(context).delete();
        context.startService(new Intent(context, Listener.class));
        await(context, "ready");
    }

    /** Until the listener has heard {@code line}, at most 15 s. */
    static void await(Context context, String line) throws Exception {
        for (int i = 0; i < 150; i++) {
            if (log(context).exists() && Files.readAllLines(log(context).toPath()).contains(line)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("the other process did not hear \"" + line + "\": " + (log(context).exists() ? Files.readAllLines(log(context).toPath()) : "nothing"));
    }
}
