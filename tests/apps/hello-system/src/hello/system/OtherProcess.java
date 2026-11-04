package hello.system;

import android.app.Application;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.provider.Settings;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A service in a process of its own (":other"): transaction 1 answers that process's name and its Settings.Secure.ANDROID_ID. */
public class OtherProcess extends Service {
    @Override public IBinder onBind(Intent intent) {
        return new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code != 1) return false;
                reply.writeString(Application.getProcessName());
                reply.writeString(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
                return true;
            }
        };
    }

    /** The other process's ANDROID_ID (bound, asked, unbound); off the main thread. */
    static String androidId(Context context) throws Exception {
        ArrayBlockingQueue<IBinder> bound = new ArrayBlockingQueue<>(1);
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { bound.offer(service); }
            @Override public void onServiceDisconnected(ComponentName name) {}
        };
        Check.require(context.bindService(new Intent(context, OtherProcess.class), connection, Context.BIND_AUTO_CREATE), "bindService refused");
        try {
            IBinder service = bound.poll(30, TimeUnit.SECONDS);
            Check.require(service != null, "the other process did not connect");
            Parcel data = Parcel.obtain(), reply = Parcel.obtain();
            try {
                service.transact(1, data, reply, 0);
                String process = reply.readString();
                Check.require((context.getPackageName() + ":other").equals(process), "process name " + process);
                return reply.readString();
            } finally { data.recycle(); reply.recycle(); }
        } finally { context.unbindService(connection); }
    }
}
