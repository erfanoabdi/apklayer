package hello.bcsender;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.widget.TextView;

/** Sends the broadcasts the test checks; the scenario comes from the "scenario" system property (-Dhello.bc=...), one scenario per run. */
public class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("bc sender");
        setContentView(view);
        String scenario = System.getProperty("hello.bc", "basic");
        System.out.println("[bcs] sender pid=" + Process.myPid() + " scenario=" + scenario);
        new Handler(Looper.getMainLooper()).postDelayed(() -> run(scenario), 500);
    }

    private void send(String action, String note) {
        Intent intent = new Intent(action);
        intent.putExtra("note", note);
        sendBroadcast(intent);
        System.out.println("[bcs] sent " + action);
    }

    private void run(String scenario) {
        try {
            switch (scenario) {
                case "manifest" -> send("hello.bc.MANIFEST", "to a package that is not running");
                case "basic" -> {
                    send("hello.bc.DYNAMIC", "dynamic");
                    send("hello.bc.MANIFEST", "manifest");
                    send("hello.bc.PROTECTED", "needs the permission");
                    send("hello.bc.GUARDED", "dynamic receiver with a permission");
                    send("hello.bc.PRIVATE", "private manifest receiver");
                    send("hello.bc.INTERNAL", "not exported dynamic receiver");
                    Intent explicit = new Intent("hello.bc.MANIFEST").setComponent(new ComponentName("hello.bcreceiver", "hello.bcreceiver.ManifestReceiver"));
                    explicit.putExtra("note", "explicit component");
                    sendBroadcast(explicit);
                    System.out.println("[bcs] sent explicit");
                }
                case "ordered" -> {
                    Intent intent = new Intent("hello.bc.ORDERED");
                    BroadcastReceiver last = new BroadcastReceiver() {
                        @Override public void onReceive(Context context, Intent i) {
                            System.out.println("[bcs] ordered result code=" + getResultCode() + " data=" + getResultData());
                        }
                    };
                    sendOrderedBroadcast(intent, null, last, null, 0, "start", null);
                    Intent abort = new Intent("hello.bc.ABORT");
                    BroadcastReceiver lastAbort = new BroadcastReceiver() {
                        @Override public void onReceive(Context context, Intent i) {
                            System.out.println("[bcs] abort result code=" + getResultCode() + " data=" + getResultData());
                        }
                    };
                    sendOrderedBroadcast(abort, null, lastAbort, null, 0, "start", null);
                }
                case "async" -> {
                    BroadcastReceiver last = new BroadcastReceiver() {
                        @Override public void onReceive(Context context, Intent i) {
                            System.out.println("[bcs] async result code=" + getResultCode() + " data=" + getResultData());
                        }
                    };
                    sendOrderedBroadcast(new Intent("hello.bc.ASYNC"), null, last, null, 0, "start", null);
                }
                case "once" -> { send("hello.bc.ONCE", "first"); send("hello.bc.ONCE", "second"); }
                case "sticky" -> {
                    Intent intent = new Intent("hello.bc.STICKY").putExtra("note", "sticky before the receiver exists");
                    sendStickyBroadcast(intent);
                    System.out.println("[bcs] sent sticky");
                }
                default -> System.out.println("[bcs] unknown scenario " + scenario);
            }
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
            System.out.println("[bcs] FAIL " + failure);
        }
        System.out.println("[bcs] done");
    }
}
