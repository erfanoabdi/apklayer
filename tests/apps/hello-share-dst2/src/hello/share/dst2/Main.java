package hello.share.dst2;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

/** P6 sample receiver: shows and logs the text another app shared with it. */
public class Main extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent i = getIntent();
        if ("hello.action.ASK".equals(i.getAction())) { // answer the caller and close
            System.out.println("[hello-share-dst2] asked: " + i.getStringExtra("q"));
            setResult(RESULT_OK, new Intent().putExtra("answer", "42 for " + i.getStringExtra("q")));
            finish();
            return;
        }
        String text = Intent.ACTION_SEND.equals(i.getAction()) ? i.getStringExtra(Intent.EXTRA_TEXT) : "(launched normally)";
        System.out.println("[hello-share-dst2] got text: " + text);
        TextView v = new TextView(this);
        v.setText("Received: " + text);
        v.setTextSize(24);
        setContentView(v);
    }
}
