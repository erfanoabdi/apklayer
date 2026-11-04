package hello.input;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * tests/vm/bottom-sheet.py: dialogs that sit at the bottom of the screen (Google Meet's "Share this joining info" sheet). Stage 1 and 2: a
 * floating dialog with gravity BOTTOM and WRAP height (its button, an outside tap, Back). Stage 3 and 4: what Material's
 * BottomSheetDialog builds from plain views: a full-screen dimmed dialog whose outside area cancels on a click and whose sheet sits at the
 * bottom (its button, an outside tap, a downward drag on its handle). Each stage shows the next one when its dialog goes away.
 */
final class Sheet {
    private Sheet() {}

    static void show(Activity a, int stage) {
        if (stage > 4) {
            System.out.println("[hello-input] sheet stages done");
            return;
        }
        Dialog d = stage <= 2 ? new Dialog(a) : new Dialog(a, android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Button button = new Button(a);
        button.setText(stage <= 2 ? "Bottom button" : "Join");
        button.setOnClickListener(v -> System.out.println("[hello-input] sheet " + stage + " button tapped"));
        View sheet;
        if (stage <= 2) {
            d.setContentView(button, new FrameLayout.LayoutParams(-1, 100));
            sheet = button;
        } else {
            FrameLayout root = new FrameLayout(a);
            View outside = new View(a); // Material's touch_outside
            outside.setOnClickListener(v -> d.cancel());
            root.addView(outside, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout body = new LinearLayout(a);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setBackgroundColor(Color.WHITE);
            View handle = new View(a);
            handle.setBackgroundColor(Color.GRAY);
            float[] down = new float[1];
            handle.setOnTouchListener((v, e) -> { // a downward drag dismisses, as BottomSheetBehavior's STATE_HIDDEN
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) down[0] = e.getRawY();
                else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) body.setTranslationY(Math.max(0, e.getRawY() - down[0]));
                else if (e.getActionMasked() == MotionEvent.ACTION_UP && e.getRawY() - down[0] > 60) {
                    System.out.println("[hello-input] sheet " + stage + " dragged down " + (int) (e.getRawY() - down[0]));
                    d.dismiss();
                }
                return true;
            });
            body.addView(handle, new LinearLayout.LayoutParams(-1, 60));
            body.addView(button, new LinearLayout.LayoutParams(-1, 100));
            root.addView(body, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            d.setContentView(root);
            sheet = body;
        }
        d.setCanceledOnTouchOutside(true);
        d.setOnCancelListener(x -> System.out.println("[hello-input] sheet " + stage + " cancelled"));
        d.setOnDismissListener(x -> button.postDelayed(() -> show(a, stage + 1), 500));
        d.show();
        Window w = d.getWindow();
        if (stage <= 2) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(-1, -2);
        } else {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(-1, -1);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.5f);
        }
        button.postDelayed(() -> {
            int[] at = new int[2], win = new int[2];
            sheet.getLocationOnScreen(at);
            w.getDecorView().getLocationOnScreen(win);
            System.out.println("[hello-input] sheet " + stage + " shown sheet=" + at[0] + "," + at[1] + "," + sheet.getWidth() + "," + sheet.getHeight()
                    + " window=" + win[0] + "," + win[1] + "," + w.getDecorView().getWidth() + "," + w.getDecorView().getHeight());
        }, 800);
    }
}
