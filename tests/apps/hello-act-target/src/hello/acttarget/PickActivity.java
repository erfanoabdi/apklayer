package hello.acttarget;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.widget.TextView;

/** Answers hello.act.PICK: prints what arrived (full Parcel payloads), then finishes with a result that carries Parcelables back. */
public class PickActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("pick");
        setContentView(view);
        handle(getIntent(), "create");
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent, "newintent");
    }

    private void handle(Intent intent, String how) {
        Bundle nested = intent.getBundleExtra("nested");
        int[] numbers = intent.getIntArrayExtra("numbers");
        Uri uri = intent.getParcelableExtra("uri", Uri.class);
        System.out.println("[act-t] " + how + " pid=" + Process.myPid() + " text=" + intent.getStringExtra("text") + " numbers=" + (numbers == null ? null : numbers.length)
                + " uri=" + uri + " nested=" + (nested == null ? null : nested.getString("k") + "/" + nested.getLong("l")));
        Intent result = new Intent("hello.act.RESULT").putExtra("pid", Process.myPid()).putExtra("echo", intent.getStringExtra("text") + "!")
                .putExtra("sum", numbers == null ? -1 : java.util.Arrays.stream(numbers).sum()).putExtra("uri", uri).putExtra("how", how)
                .putExtra("nested", nested);
        if (intent.getBooleanExtra("stay", false)) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> { setResult(RESULT_OK, result); finish(); }, 800);
    }
}
