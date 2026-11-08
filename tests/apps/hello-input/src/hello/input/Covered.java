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
        button.setOnClickListener(v -> System.out.println("[hello-input] covered dialog tapped, stateSaved="
                + below.getFragmentManager().isStateSaved()));
        dialog.setContentView(button);
        dialog.show();
        dialog.getWindow().setLayout(-1, -1);
        button.postDelayed(() -> below.startActivity(new Intent(below, Covered.class)), 1500);
        // a dialog shown once the activity is stopped (a late network answer): hidden with it, no taps
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        below.registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStopped(Activity a) { below.unregisterActivityLifecycleCallbacks(this); main.postDelayed(() -> showLate(below, main), 500); }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    private static void showLate(Activity below, android.os.Handler main) {
        Dialog late = new Dialog(below);
        Button lateButton = new Button(below);
        lateButton.setText("Late dialog of the stopped activity");
        lateButton.setOnClickListener(v -> System.out.println("[hello-input] covered dialog tapped (late)"));
        late.setContentView(lateButton);
        late.show();
        late.getWindow().setLayout(-1, -1);
        main.postDelayed(() -> System.out.println("[hello-input] late dialog shown=" + late.getWindow().getDecorView().isShown()), 500);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Button button = new Button(this);
        button.setText("Covering activity");
        button.setOnClickListener(v -> {
            System.out.println("[hello-input] covering activity tapped, stateSaved=" + getFragmentManager().isStateSaved());
            try {
                startActivity(new Intent("hello.input.NOBODY_HANDLES_THIS"));
            } catch (android.content.ActivityNotFoundException e) {
                System.out.println("[hello-input] unhandled start threw");
            }
        });
        setContentView(button);
        if (getIntent().getBooleanExtra("finish", false)) { // Sheet's stage 5: a round trip; red, and above the sheet of the activity below
            button.setBackgroundColor(android.graphics.Color.RED);
            button.postDelayed(() -> System.out.println("[hello-input] covering activity shown"), 500);
            button.postDelayed(this::finish, 1800);
        }
    }

    @Override protected void onResume() { super.onResume(); System.out.println("[hello-input] covering activity resumed"); }
    @Override protected void onPause() { super.onPause(); System.out.println("[hello-input] covering activity paused"); }
    @Override protected void onStop() { super.onStop(); System.out.println("[hello-input] covering activity stopped"); }
}
