package hello.push;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

/** A cloud message (com.google.android.c2dm.intent.RECEIVE) to an app that is not running: shows it as a notification. */
public class PushReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String text = intent.getStringExtra("message");
        System.out.println("[push] RECEIVE pid=" + Process.myPid() + " from=" + intent.getStringExtra("from") + " id=" + intent.getStringExtra("google.message_id") + " message=" + text);
        Notify.post(context, 1, "push-msg", text);
    }
}
