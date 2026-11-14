package hello.recreate;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Calls recreate() once in onResume (NewPipe's MainActivity) and logs the lifecycle; the second instance must get the saved state. */
public class Main extends Activity {
    private static boolean done;

    private void log(String s) { System.out.println("[recreate] " + hashCode() + " " + s); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        log("onCreate state=" + (state == null ? null : state.getString("k")));
        TextView view = new TextView(this);
        view.setText("recreate");
        setContentView(view);
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("k", "saved"); log("onSaveInstanceState"); }
    @Override protected void onStart() { super.onStart(); log("onStart"); }
    @Override protected void onStop() { super.onStop(); log("onStop"); }
    @Override protected void onPause() { super.onPause(); log("onPause"); }
    @Override protected void onDestroy() { super.onDestroy(); log("onDestroy"); }
    @Override protected void onResume() {
        super.onResume();
        log("onResume");
        if (!done) { done = true; recreate(); return; }
        new android.os.Handler().postDelayed(() -> {
            boolean ok = getWindow().getDecorView().isAttachedToWindow();
            System.out.println("[recreate] " + (ok ? "PASS" : "FAIL") + " second instance resumed, window attached=" + ok);
            System.out.println("[recreate] END");
        }, 1500);
    }
}
