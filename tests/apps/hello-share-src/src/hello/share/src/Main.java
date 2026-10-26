package hello.share.src;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

/** P6 sample sender: after a few seconds shares some text (ACTION_SEND text/plain) with whoever handles it. */
public class Main extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView v = new TextView(this);
        v.setText("Sharing in 3 s...");
        v.setTextSize(24);
        setContentView(v);
        new Handler(Looper.getMainLooper()).postDelayed(() -> startActivity(
                new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "hello from the sender")), 3000);
    }
}
