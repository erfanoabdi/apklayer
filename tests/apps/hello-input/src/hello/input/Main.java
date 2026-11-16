package hello.input;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Real widget text, IME and clipboard callbacks for the proxied GPU window. */
public class Main extends Activity {
    /** Sheet stage 7: a tap that reaches the activity's window (the orphaned popup above it has no window). */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        logPen(e);
        if (Sheet.orphaned && e.getActionMasked() == android.view.MotionEvent.ACTION_UP) Sheet.touched(this);
        return super.dispatchTouchEvent(e);
    }

    /** tests/vm/alt-ime.py: a dialog with FLAG_ALT_FOCUSABLE_IM takes keys into its field, but the input method stays off it (WindowManagerService's IME target). */
    private void altFocusableIm() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        EditText alt = new EditText(this);
        alt.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { System.out.println("[hello-input] alt text=" + s); }
            public void afterTextChanged(Editable s) {}
        });
        dialog.setContentView(alt);
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        dialog.show();
        alt.requestFocus();
        System.out.println("[hello-input] alt dialog shown");
    }

    /** tests/vm/window-output.py: the screen the app sees (Display.getRealMetrics), at start and at each DisplayListener callback. */
    private void displayLog() {
        android.hardware.display.DisplayManager dm = getSystemService(android.hardware.display.DisplayManager.class);
        Runnable log = () -> {
            android.util.DisplayMetrics m = new android.util.DisplayMetrics();
            dm.getDisplay(android.view.Display.DEFAULT_DISPLAY).getRealMetrics(m);
            System.out.println("[hello-input] display " + m.widthPixels + "x" + m.heightPixels + " dpi=" + m.densityDpi);
        };
        dm.registerDisplayListener(new android.hardware.display.DisplayManager.DisplayListener() {
            public void onDisplayAdded(int id) { System.out.println("[hello-input] display added " + id); }
            public void onDisplayRemoved(int id) { System.out.println("[hello-input] display removed " + id); }
            public void onDisplayChanged(int id) { System.out.println("[hello-input] display changed " + id); log.run(); }
        }, new android.os.Handler(android.os.Looper.getMainLooper()));
        log.run();
    }

    /** tests/vm/input-inject.py (-Dhello.backlog): Back is counted, not obeyed (the activity stays). */
    @Override public void onBackPressed() {
        if (System.getProperty("hello.backlog") != null) System.out.println("[hello-input] back pressed");
        else super.onBackPressed();
    }

    /** tests/vm/input-lang.py: a key that reaches the app (no text field has focus) with the character of its layout. */
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && e.getKeyCode() == android.view.KeyEvent.KEYCODE_PAGE_UP && System.getProperty("hello.backlog") != null) {
            // tests/vm/navbar-haptic.py: Page Up flips Settings.System haptic_feedback_enabled (Touch feedback)
            android.content.ContentResolver cr = getContentResolver();
            int on = android.provider.Settings.System.getInt(cr, android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0 ? 1 : 0;
            System.out.println("[hello-input] haptic setting " + on + " put=" + android.provider.Settings.System.putInt(cr, android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED, on));
            return true;
        }
        if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && e.getUnicodeChar() >= 128)
            System.out.println("[hello-input] key code=" + e.getKeyCode() + " unicode=" + e.getUnicodeChar());
        return super.dispatchKeyEvent(e);
    }

    /** A stylus event's axes (tests/vm/input-wheel-pen.py). */
    private static void logPen(android.view.MotionEvent e) {
        int tool = e.getToolType(0);
        if (tool != android.view.MotionEvent.TOOL_TYPE_STYLUS && tool != android.view.MotionEvent.TOOL_TYPE_ERASER) return;
        System.out.println("[hello-input] pen " + android.view.MotionEvent.actionToString(e.getActionMasked()) + " tool=" + tool + " x=" + e.getX() + " y=" + e.getY()
                + " pressure=" + e.getPressure() + " tilt=" + e.getAxisValue(android.view.MotionEvent.AXIS_TILT) + " orientation=" + e.getOrientation()
                + " distance=" + e.getAxisValue(android.view.MotionEvent.AXIS_DISTANCE) + " rotation=" + e.getAxisValue(android.view.MotionEvent.AXIS_GENERIC_1)
                + " slider=" + e.getAxisValue(android.view.MotionEvent.AXIS_WHEEL) + " wheel=" + e.getAxisValue(android.view.MotionEvent.AXIS_SCROLL)
                + " buttons=" + e.getButtonState() + " source=0x" + Integer.toHexString(e.getSource()));
    }
    @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent e) { logPen(e); return super.dispatchGenericMotionEvent(e); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        LinearLayout layout = new LinearLayout(this) {  // the wheel (tests/vm/input-wheel-pen.py) reaches the views under the pointer as onGenericMotionEvent
            @Override public boolean onGenericMotionEvent(android.view.MotionEvent e) {
                if (e.getActionMasked() != android.view.MotionEvent.ACTION_SCROLL) return false;
                System.out.println("[hello-input] wheel v=" + e.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL) + " h=" + e.getAxisValue(android.view.MotionEvent.AXIS_HSCROLL)
                        + " shift=" + ((e.getMetaState() & 1) != 0) + " source=0x" + Integer.toHexString(e.getSource()));
                return true;
            }
        };
        layout.setOrientation(LinearLayout.VERTICAL);
        EditText field = new EditText(this);
        field.setHint("Type or paste here");
        field.setTextSize(24);
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                System.out.println("[hello-input] text=" + s.toString().replace("\n", "\\n"));  // a newline as \n: one log line
            }
            public void afterTextChanged(Editable s) {}
        });
        android.view.ActionMode.Callback toolbar = new android.view.ActionMode.Callback() { // a long press's floating toolbar (selection or insertion)
            public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                System.out.println("[hello-input] action mode started");
                return true;
            }
            public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
            public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) { return false; }
            public void onDestroyActionMode(android.view.ActionMode mode) { System.out.println("[hello-input] action mode finished"); }
        };
        field.setCustomSelectionActionModeCallback(toolbar);
        field.setOnLongClickListener(v -> { System.out.println("[hello-input] long press"); return false; });  // false: the editor's word selection goes on
        field.setCustomInsertionActionModeCallback(toolbar);
        float dp = getResources().getDisplayMetrics().density;  // heights in dp: the tests tap logical (dp) coordinates at any host scale
        layout.addView(field, new LinearLayout.LayoutParams(-1, (int) (80 * dp)));
        Button copy = new Button(this);
        copy.setText("Copy Android text");
        copy.setOnClickListener(v -> clipboard.setPrimaryClip(ClipData.newPlainText("test", "android-copy")));
        layout.addView(copy, new LinearLayout.LayoutParams(-1, (int) (64 * dp)));
        Button paste = new Button(this);
        paste.setText("Paste host text");
        paste.setOnClickListener(v -> field.onTextContextMenuItem(android.R.id.paste));
        layout.addView(paste, new LinearLayout.LayoutParams(-1, (int) (64 * dp)));
        Button longCopy = new Button(this);
        longCopy.setText("Copy long text");
        longCopy.setOnClickListener(v -> {
            char[] data = new char[20000];
            java.util.Arrays.fill(data, 'L');
            clipboard.setPrimaryClip(ClipData.newPlainText("long", new String(data)));
        });
        layout.addView(longCopy, new LinearLayout.LayoutParams(-1, (int) (64 * dp)));
        // the other kinds of field (tests/vm/ime-fields.py, the phones' keyboards): single-line with an Enter action, password, number
        layout.addView(kind(1, android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                android.view.inputmethod.EditorInfo.IME_ACTION_GO, "Email (Enter: Go)"), new LinearLayout.LayoutParams(-1, (int) (48 * dp)));
        layout.addView(kind(2, android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                android.view.inputmethod.EditorInfo.IME_ACTION_DONE, "Password"), new LinearLayout.LayoutParams(-1, (int) (48 * dp)));
        layout.addView(kind(3, android.text.InputType.TYPE_CLASS_NUMBER, 0, "Number"), new LinearLayout.LayoutParams(-1, (int) (48 * dp)));
        Button hide = new Button(this);  // the app's own keyboard close (tests/vm/ime-fields.py): a button takes no focus in touch mode, the field keeps it
        hide.setText("Hide keyboard");
        hide.setOnClickListener(v -> System.out.println("[hello-input] hide=" + getSystemService(android.view.inputmethod.InputMethodManager.class)
                .hideSoftInputFromWindow(v.getWindowToken(), 0)));
        layout.addView(hide, new LinearLayout.LayoutParams(-1, (int) (48 * dp)));
        Button plain = new Button(this);  // no field with focus: keys reach the app as key events (tests/vm/input-lang.py)
        plain.setText("Plain keys");
        plain.setOnClickListener(v -> {
            for (int i = 0; i < layout.getChildCount(); i++) if (layout.getChildAt(i) instanceof EditText) layout.getChildAt(i).setEnabled(false);
            System.out.println("[hello-input] fields off");
        });
        layout.addView(plain, new LinearLayout.LayoutParams(-1, (int) (48 * dp)));
        // its centre in the window's logical pixels (the tests tap there: the window's size and the host's decorations move it)
        layout.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> { int[] at = new int[2]; plain.getLocationInWindow(at);
            System.out.println("[hello-input] plain keys at " + (int) ((at[0] + plain.getWidth() / 2) / dp) + "," + (int) ((at[1] + plain.getHeight() / 2) / dp)); });
        clipboard.addPrimaryClipChangedListener(() -> {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                String text = String.valueOf(clip.getItemAt(0).getText());
                if (text.startsWith("host-copy") || text.startsWith("android-copy") || text.startsWith("LLLL"))
                    System.out.println("[hello-input] clip=" + text.substring(0, Math.min(24, text.length()))
                            + " length=" + text.length());
                else System.out.println("[hello-input] other clip length=" + text.length());
            }
        });
        setContentView(layout);
        field.requestFocus();
        if (System.getProperty("hello.ime") != null) ImeInsets.watch(this, System.getProperty("hello.ime"));
        if (System.getProperty("hello.covered") != null) Covered.coverWithDialog(this);
        if (System.getProperty("hello.translucent") != null && state == null) Translucent.underTranslucent(this);
        if (System.getProperty("hello.altim") != null) field.postDelayed(this::altFocusableIm, 1000);
        if (System.getProperty("hello.display") != null) displayLog();

        if (System.getProperty("hello.sheet") != null) field.postDelayed(() -> Sheet.show(this, 1), 1500);
        if (System.getProperty("hello.magnifier") != null) {
            field.setText("magnify me");
            field.postDelayed(() -> magnify(field), 2000);
        }
    }

    /** Single-line field n: its text and editor actions go to the log. */
    private EditText kind(int n, int type, int action, String hint) {
        EditText f = new EditText(this);
        f.setHint(hint);
        f.setInputType(type);
        if (action != 0) f.setImeOptions(action);
        f.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { System.out.println("[hello-input] field" + n + " text=" + s.toString().replace("\n", "\\n")); }
            public void afterTextChanged(Editable s) {}
        });
        f.setOnEditorActionListener((v, id, event) -> { System.out.println("[hello-input] field" + n + " action=" + id); return true; });  // handled: the focus stays
        return f;
    }

    /** The surfaces a text drag makes (tests/vm/magnifier.sh): a child layer of the window's own layer and the text magnifier. */
    private void magnify(EditText field) {
        try {
            Object root = android.view.View.class.getMethod("getViewRootImpl").invoke(field);
            android.view.SurfaceControl parent = (android.view.SurfaceControl) root.getClass().getMethod("getSurfaceControl").invoke(root);
            android.view.SurfaceControl child = new android.view.SurfaceControl.Builder().setName("hello-child").setBufferSize(32, 32)
                    .setParent(parent).build();
            System.out.println("[hello-input] child layer valid=" + child.isValid());
            new android.view.SurfaceControl.Transaction().reparent(child, null).apply();
            child.release();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        android.widget.Magnifier magnifier = new android.widget.Magnifier(field);
        magnifier.show(field.getWidth() / 4f, field.getHeight() / 2f);
        System.out.println("[hello-input] magnifier shown");
        // what the magnifier reads: the window's last frame through PixelCopy
        android.graphics.Bitmap copy = android.graphics.Bitmap.createBitmap(field.getWidth(), field.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        android.view.PixelCopy.request(getWindow(), copy, result ->
                System.out.println("[hello-input] pixel copy result=" + result + " corner=" + Integer.toHexString(copy.getPixel(0, 0))),
                new android.os.Handler(getMainLooper()));
        field.postDelayed(() -> {
            magnifier.dismiss();
            System.out.println("[hello-input] magnifier dismissed");
        }, 1000);
    }
}
