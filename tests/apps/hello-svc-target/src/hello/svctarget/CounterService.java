package hello.svctarget;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;

/** Counts start commands; a Binder answers transaction 1 with {count, last text, last start id, pid}. */
public class CounterService extends Service {
    private int count, lastId, binds, rebinds;
    private String last = "";

    /** Transaction 1: {count, last text, last start id, pid}; 2: {onBind calls, onRebind calls, the action this binder was made for}. */
    @Override public IBinder onBind(Intent intent) {
        binds++;
        String action = String.valueOf(intent.getAction());
        return new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code == 1) { reply.writeInt(count); reply.writeString(last); reply.writeInt(lastId); reply.writeInt(Process.myPid()); return true; }
                if (code == 2) { reply.writeInt(binds); reply.writeInt(rebinds); reply.writeString(action); return true; }
                return false;
            }
        };
    }

    @Override public boolean onUnbind(Intent intent) { return true; }  // ask for onRebind
    @Override public void onRebind(Intent intent) { rebinds++; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        count++; lastId = startId;
        last = intent == null ? "(null)" : intent.getStringExtra("text");
        System.out.println("[svc-t] start id=" + startId + " text=" + last + " pid=" + Process.myPid());
        if (intent != null && intent.getBooleanExtra("selfstop", false)) stopSelf(startId);
        return intent == null ? START_STICKY : intent.getIntExtra("mode", START_NOT_STICKY);
    }

    @Override public void onDestroy() { System.out.println("[svc-t] destroyed"); }
}
