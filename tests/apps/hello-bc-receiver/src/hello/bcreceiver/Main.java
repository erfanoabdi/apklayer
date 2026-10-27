package hello.bcreceiver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Process;
import android.widget.TextView;

/** Registers dynamic receivers (plain, ordered with a priority, protected by a permission, not exported) and says so. */
public class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("bc receiver");
        setContentView(view);
        BroadcastReceiver plain = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if ("hello.bc.ONCE".equals(intent.getAction())) { context.unregisterReceiver(this); System.out.println("[bcr] once: unregistered"); }
                System.out.println("[bcr] dynamic " + intent.getAction() + " pid=" + Process.myPid() + " ordered=" + isOrderedBroadcast()
                        + " extra=" + intent.getStringExtra("note") + " sticky=" + isInitialStickyBroadcast());
                if (isOrderedBroadcast() && System.getProperty("hello.trace") != null) new Throwable("[bcr] trace").printStackTrace(System.out);
                if (isOrderedBroadcast()) {
                    if ("hello.bc.ABORT".equals(intent.getAction())) { System.out.println("[bcr] ABORT reached the dynamic receiver (it must not)"); return; }
                    setResultData(getResultData() + "|D");
                    setResultCode(getResultCode() + 1);
                }
            }
        };
        IntentFilter filter = new IntentFilter("hello.bc.DYNAMIC");
        filter.addAction("hello.bc.ORDERED");
        filter.addAction("hello.bc.ABORT");
        filter.addAction("hello.bc.STICKY");
        filter.addAction("hello.bc.ONCE");
        filter.setPriority(5);
        registerReceiver(plain, filter, Context.RECEIVER_EXPORTED);
        BroadcastReceiver guarded = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { System.out.println("[bcr] guarded dynamic " + intent.getAction()); }
        };
        registerReceiver(guarded, new IntentFilter("hello.bc.GUARDED"), "hello.bcreceiver.permission.SEND", null, Context.RECEIVER_EXPORTED);
        BroadcastReceiver internal = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { System.out.println("[bcr] INTERNAL reached " + intent.getAction()); }
        };
        registerReceiver(internal, new IntentFilter("hello.bc.INTERNAL"), Context.RECEIVER_NOT_EXPORTED);
        System.out.println("[bcr] ready pid=" + Process.myPid());
    }
}
