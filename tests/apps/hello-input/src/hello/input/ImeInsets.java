package hello.input;

import android.app.Activity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

/** tests/vm/ime-insets.py (-Dhello.ime=resize|pan): the window's softInputMode, and what the app sees of the host keyboard each frame: the
 * IME insets (visible, bottom), the content's height (adjustResize shrinks it) and where the focused view sits in the window (adjustPan
 * scrolls it into sight). */
final class ImeInsets {
    private ImeInsets() {}

    static void watch(Activity activity, String mode) {
        activity.getWindow().setSoftInputMode("pan".equals(mode) ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                : WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        View content = activity.findViewById(android.R.id.content);
        String[] last = {""};
        content.getViewTreeObserver().addOnPreDrawListener(() -> {
            WindowInsets insets = content.getRootWindowInsets();
            View focus = content.findFocus();
            int[] at = new int[2];
            if (focus != null) focus.getLocationInWindow(at);
            String now = insets == null ? "no insets" : "visible=" + insets.isVisible(WindowInsets.Type.ime()) + " bottom="
                    + insets.getInsets(WindowInsets.Type.ime()).bottom + " content=" + content.getHeight() + " focus=" + at[1];
            if (!now.equals(last[0])) System.out.println("[hello-input] ime " + (last[0] = now));
            return true;
        });
    }
}
