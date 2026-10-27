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
        layout.addView(field, new LinearLayout.LayoutParams(-1, 80));
        Button copy = new Button(this);
        copy.setText("Copy Android text");
        copy.setOnClickListener(v -> clipboard.setPrimaryClip(ClipData.newPlainText("test", "android-copy")));
        layout.addView(copy, new LinearLayout.LayoutParams(-1, 64));
        Button paste = new Button(this);
        paste.setText("Paste host text");
        paste.setOnClickListener(v -> field.onTextContextMenuItem(android.R.id.paste));
        layout.addView(paste, new LinearLayout.LayoutParams(-1, 64));
        Button longCopy = new Button(this);
        longCopy.setText("Copy long text");
        longCopy.setOnClickListener(v -> {
            char[] data = new char[20000];
            java.util.Arrays.fill(data, 'L');
            clipboard.setPrimaryClip(ClipData.newPlainText("long", new String(data)));
        });
        layout.addView(longCopy, new LinearLayout.LayoutParams(-1, 64));
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
    }
}
