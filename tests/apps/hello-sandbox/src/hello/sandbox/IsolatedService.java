package hello.sandbox;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Process;
import java.io.File;
import java.nio.file.Files;

/**
 * An android:isolatedProcess service (tests/vm/sandbox.sh): its own uid (99000 and up), no access to the app's data dir. Main sends it the paths to try
 * (message 1: "data" is the app's file, "other" another app's); it answers with what it saw and what BackService, the app's own process, saw of it.
 */
public final class IsolatedService extends Service {
    @Override public IBinder onBind(Intent intent) {
        return new Messenger(new Handler(Looper.getMainLooper(), message -> {
            Bundle in = message.getData(), out = new Bundle();
            out.putInt("uid", Process.myUid());
            out.putBoolean("isolated", Process.isIsolated());
            out.putString("os", osUid());
            out.putString("data", probe(in.getString("data")));
            out.putString("other", probe(in.getString("other")));
            Messenger reply = message.replyTo;
            // back to the app's own process through the hub
            bindService(new Intent(this, BackService.class), new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                    Parcel data = Parcel.obtain(), result = Parcel.obtain();
                    try {
                        binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, result, 0);
                        out.putString("back", result.readString());
                    } catch (Exception e) { out.putString("back", "failed " + e);
                    } finally { data.recycle(); result.recycle(); }
                    try { Message m = Message.obtain(); m.setData(out); reply.send(m); } catch (Exception e) { System.out.println("[sandbox] iso reply failed " + e); }
                    unbindService(this);
                }
                @Override public void onServiceDisconnected(ComponentName name) {}
            }, Context.BIND_AUTO_CREATE);
            return true;
        })).getBinder();
    }

    private static String probe(String path) {
        if (path == null) return "unset";
        File f = new File(path);
        if (f.isDirectory()) { String[] names = f.list(); return names == null ? "denied" : "listed " + names.length; }
        try { return "read " + Files.readString(f.toPath()).trim(); }
        catch (Exception e) { return "denied " + e.getClass().getSimpleName(); }
    }

    private static String osUid() {
        try {
            for (String line : Files.readAllLines(new File("/proc/self/status").toPath())) if (line.startsWith("Uid:")) return line.substring(4).trim().split("\\s+")[0];
        } catch (Exception e) { /* below */ }
        return "?";
    }
}
