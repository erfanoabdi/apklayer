package hello.system;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.widget.Toast;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** One toast queue over the instance (the hub's TOAST_SLOT): the app's toast is on screen, then the toast of its ":other" process asks; it shows only after the first is hidden. */
final class ToastQueueCheck implements Check {
    @Override public String run(Context context) throws Exception {
        ArrayBlockingQueue<IBinder> bound = new ArrayBlockingQueue<>(1);
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { bound.offer(service); }
            @Override public void onServiceDisconnected(ComponentName name) {}
        };
        Check.require(context.bindService(new Intent(context, OtherProcess.class), connection, Context.BIND_AUTO_CREATE), "bindService refused");
        try {
            IBinder other = bound.poll(30, TimeUnit.SECONDS);
            Check.require(other != null, "the other process did not connect");
            long[] mine = new long[2];
            CountDownLatch shown = new CountDownLatch(1), hidden = new CountDownLatch(1);
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast toast = Toast.makeText(context, "apklayer toast mine", Toast.LENGTH_SHORT);
                toast.addCallback(new Toast.Callback() {
                    @Override public void onToastShown() { mine[0] = System.currentTimeMillis(); shown.countDown(); }
                    @Override public void onToastHidden() { mine[1] = System.currentTimeMillis(); hidden.countDown(); }
                });
                toast.show();
            });
            Check.require(shown.await(20, TimeUnit.SECONDS), "my toast was not shown");
            Parcel data = Parcel.obtain(), reply = Parcel.obtain();
            try {
                other.transact(2, data, reply, 0);  // while mine is on screen
                long otherShown = reply.readLong(), otherHidden = reply.readLong();
                Check.require(hidden.await(5, TimeUnit.SECONDS) && otherShown > 0 && otherHidden > 0, "toasts not both shown and hidden: " + otherShown + "/" + otherHidden);
                Check.require(otherShown >= mine[1] - 50, "the other process's toast came on screen " + (mine[1] - otherShown) + " ms before mine was hidden");
                return "mine " + (mine[1] - mine[0]) + " ms on screen, the other one's toast shown " + (otherShown - mine[1]) + " ms after it, for " + (otherHidden - otherShown) + " ms";
            } finally { data.recycle(); reply.recycle(); }
        } finally { context.unbindService(connection); }
    }
}
