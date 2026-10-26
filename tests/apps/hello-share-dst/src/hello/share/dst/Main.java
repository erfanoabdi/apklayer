package hello.share.dst;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

/** P6 sample receiver: shows and logs the text another app shared with it. */
public class Main extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent i = getIntent();
        String text = Intent.ACTION_SEND.equals(i.getAction()) ? i.getStringExtra(Intent.EXTRA_TEXT) : "(launched normally)";
        System.out.println("[hello-share-dst] got text: " + text);
        TextView v = new TextView(this);
        v.setText("Received: " + text);
        v.setTextSize(24);
        setContentView(v);
    }
}
