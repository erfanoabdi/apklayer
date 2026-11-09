package hello.input;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;

/**
 * tests/vm/stopped-windows.py translucent: an activity with a full-window dialog is covered by a translucent activity, so it stays paused and
 * visible (ActivityRecord stays visible behind a translucent one), and shows a second dialog while paused. Both dialogs stack with their
 * activity, below the newer one: the screen shows the translucent activity's red and taps reach it.
 */
public class Translucent extends Activity {
    static void underTranslucent(Activity below) {
        Handler main = new Handler(Looper.getMainLooper());
        dialog(below, "Dialog of the paused activity", Color.BLUE);
        main.postDelayed(() -> below.startActivity(new Intent(below, Translucent.class)), 1500);
        below.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityPaused(Activity a) {
                below.unregisterActivityLifecycleCallbacks(this);
                main.postDelayed(() -> {
                    Dialog late = dialog(below, "Late dialog of the paused activity", Color.GREEN);
                    main.postDelayed(() -> System.out.println("[hello-input] paused late dialog windowVisibility="
                            + late.getWindow().getDecorView().getWindowVisibility()), 500);
                }, 500);
            }
            @Override public void onActivityStopped(Activity a) { if (!a.isChangingConfigurations()) System.out.println("[hello-input] paused activity stopped"); }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    private static Dialog dialog(Activity below, String text, int color) {
        Dialog dialog = new Dialog(below);
        Button button = new Button(below);
        button.setText(text);
        button.setBackgroundColor(color);
        button.setOnClickListener(v -> System.out.println("[hello-input] paused activity's dialog tapped: " + text));
        dialog.setContentView(button);
        dialog.show();
        dialog.getWindow().setLayout(-1, -1);
        return dialog;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Button button = new Button(this);
        button.setText("Translucent activity");
        button.setBackgroundColor(Color.RED);
        button.setOnClickListener(v -> System.out.println("[hello-input] translucent activity tapped"));
        setContentView(button);
    }

    @Override protected void onResume() { super.onResume(); System.out.println("[hello-input] translucent activity resumed"); }
}
