package hello.pushgms;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

/** Stands in for microG's McsService: delivers a cloud message to the app the way it does (an ordered broadcast of com.google.android.c2dm.intent.RECEIVE to the app's package). */
public class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("push-gms");
        setContentView(view);
        Intent message = new Intent("com.google.android.c2dm.intent.RECEIVE").setPackage("hello.push");
        message.putExtra("from", "1234567890").putExtra("google.message_id", "0:1700000000000000%7a1b2c3d").putExtra("message", "hello from the other phone");
        sendOrderedBroadcast(message, null);
        System.out.println("[pushgms] SENT");
    }
}
