package hello.settings;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.TextView;

/** Started over Main by a tap, finished by the next: "[set] top" lines (not counted as Main's creations). */
public final class Top extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText("top");
        setContentView(text);
        System.out.println("[set] top fontScale=" + getResources().getConfiguration().fontScale);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) finish();
        return true;
    }
}
