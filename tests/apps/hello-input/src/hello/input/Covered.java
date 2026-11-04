package hello.input;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

/**
 * tests/vm/stopped-windows.py: a dialog left open on an activity that another one covers. Android hides the stopped activity's windows,
 * so taps reach the covering activity; a tap on the dialog would run with the stopped activity's fragments stopped (Google Meet's
 * TikTok FuturesMixin crashed so). The covering activity then starts an intent nobody handles: its lifecycle stays as it is.
 */
public class Covered extends Activity {
    static void coverWithDialog(Activity below) {
        Dialog dialog = new Dialog(below);
        Button button = new Button(below);
        button.setText("Dialog of the covered activity");
        button.setOnClickListener(v -> System.out.println("[hello-input] covered dialog tapped, fragments stopped="
                + below.getFragmentManager().isStateSaved()));
        dialog.setContentView(button);
        dialog.show();
        dialog.getWindow().setLayout(-1, -1);
        button.postDelayed(() -> below.startActivity(new Intent(below, Covered.class)), 1500);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Button button = new Button(this);
        button.setText("Covering activity");
        button.setOnClickListener(v -> {
            System.out.println("[hello-input] covering activity tapped, fragments stopped=" + getFragmentManager().isStateSaved());
            try {
                startActivity(new Intent("hello.input.NOBODY_HANDLES_THIS"));
            } catch (android.content.ActivityNotFoundException e) {
                System.out.println("[hello-input] unhandled start threw");
            }
        });
        setContentView(button);
    }

    @Override protected void onResume() { super.onResume(); System.out.println("[hello-input] covering activity resumed"); }
    @Override protected void onPause() { super.onPause(); System.out.println("[hello-input] covering activity paused"); }
    @Override protected void onStop() { super.onStop(); System.out.println("[hello-input] covering activity stopped"); }
}
