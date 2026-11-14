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

/** A service in a process of its own (":other"): transaction 1 answers that process's name, its Settings.Secure.ANDROID_ID and the "android" package's certificate. */
public class OtherProcess extends Service {
    @Override public IBinder onBind(Intent intent) {
        return new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code == 2) {  // a toast of this process: reply with when it was shown and hidden (wall clock), once hidden (ToastQueueCheck)
                    long[] at = new long[2];
                    java.util.concurrent.CountDownLatch hidden = new java.util.concurrent.CountDownLatch(1);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        android.widget.Toast toast = android.widget.Toast.makeText(OtherProcess.this, "apklayer toast other", android.widget.Toast.LENGTH_SHORT);
                        toast.addCallback(new android.widget.Toast.Callback() {
                            @Override public void onToastShown() { at[0] = System.currentTimeMillis(); }
                            @Override public void onToastHidden() { at[1] = System.currentTimeMillis(); hidden.countDown(); }
                        });
                        toast.show();
                    });
                    try { hidden.await(20, TimeUnit.SECONDS); } catch (InterruptedException e) { return false; }
                    reply.writeLong(at[0]);
                    reply.writeLong(at[1]);
                    return true;
                }
                if (code != 1) return false;
                reply.writeString(Application.getProcessName());
                reply.writeString(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
                reply.writeString(platformCertificate(OtherProcess.this));
                return true;
            }
        };
    }

    /** The SHA-256 of the "android" package's signing certificate as this process sees it. */
    static String platformCertificate(Context context) {
        try {
            byte[] cert = context.getPackageManager().getPackageInfo("android", android.content.pm.PackageManager.GET_SIGNATURES).signatures[0].toByteArray();
            return new java.math.BigInteger(1, java.security.MessageDigest.getInstance("SHA-256").digest(cert)).toString(16);
        } catch (Exception failure) { return failure.toString(); }
    }

    /** The other process's ANDROID_ID. */
    static String androidId(Context context) throws Exception { return ask(context)[0]; }

    /** The other process's ANDROID_ID and "android" certificate (bound, asked, unbound); off the main thread. */
    static String[] ask(Context context) throws Exception {
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
                return new String[] {reply.readString(), reply.readString()};
            } finally { data.recycle(); reply.recycle(); }
        } finally { context.unbindService(connection); }
    }
}
