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
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        EditText field = new EditText(this);
        field.setHint("Type or paste here");
        field.setTextSize(24);
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                System.out.println("[hello-input] text=" + s);
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
        if (System.getProperty("hello.covered") != null) Covered.coverWithDialog(this);
        if (System.getProperty("hello.magnifier") != null) {
            field.setText("magnify me");
            field.postDelayed(() -> magnify(field), 2000);
        }
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
