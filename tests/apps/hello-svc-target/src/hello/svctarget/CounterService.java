package hello.svctarget;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;

/** Counts start commands; a Binder answers transaction 1 with {count, last text, last start id, pid}. */
public class CounterService extends Service {
    private int count, lastId;
    private String last = "";

    @Override public IBinder onBind(Intent intent) {
        return new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code != 1) return false;
                reply.writeInt(count); reply.writeString(last); reply.writeInt(lastId); reply.writeInt(Process.myPid());
                return true;
            }
        };
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        count++; lastId = startId;
        last = intent == null ? "(null)" : intent.getStringExtra("text");
        System.out.println("[svc-t] start id=" + startId + " text=" + last + " pid=" + Process.myPid());
        if (intent != null && intent.getBooleanExtra("selfstop", false)) stopSelf(startId);
        return intent == null ? START_STICKY : intent.getIntExtra("mode", START_NOT_STICKY);
    }

    @Override public void onDestroy() { System.out.println("[svc-t] destroyed"); }
}
