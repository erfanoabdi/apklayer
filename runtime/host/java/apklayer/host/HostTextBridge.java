package apklayer.host;

import android.view.View;
import org.robolectric.RuntimeEnvironment;

/** Text input and clipboard between the app and the shell: the IME on and off for the focused field, composing text, the system selection. */
final class HostTextBridge {
    ShellClient shell;
    private boolean imeEnabled;        // text input enabled in the shell for the focused field
    private String lastClipboard = ""; // last text exchanged with the shell, to avoid echo loops

    /** Tells the shell when a text field gets or loses focus, so the IME turns on and off. */
    void followTextInput() {
        if (shell == null) return;
        boolean editor = HostWindows.focusedEditor() != null;
        if (editor != imeEnabled) {
            imeEnabled = editor;
            shell.sendTextInput(editor);
            if (Boolean.getBoolean("apklayer.debugkeys")) System.out.println("[apklayer] text input " + (editor ? "on" : "off"));
        }
    }

    /**
     * IME event (zwp_text_input_v3 done): delete surrounding text, commit, then the new preedit,
     * through the focused field's InputConnection (PLAN P2.6).
     */
    void ime(int before, int after, String commit, String preedit) {
        View f = HostWindows.focusedEditor();
        if (f == null) return;
        android.view.inputmethod.InputConnection ic = f.onCreateInputConnection(new android.view.inputmethod.EditorInfo());
        if (ic == null) return;
        Runnable edit = () -> {
            ic.beginBatchEdit();
            if (before > 0 || after > 0) ic.deleteSurroundingText(before, after);
            if (!commit.isEmpty()) ic.commitText(commit, 1);
            if (!preedit.isEmpty()) ic.setComposingText(preedit, 1);
            else if (commit.isEmpty()) ic.setComposingText("", 1); // preedit cancelled
            ic.endBatchEdit();
        };
        // an InputConnection names the thread its methods must run on (getHandler): Chromium's (a WebView text field) asserts it
        android.os.Handler handler = ic.getHandler();
        if (handler == null || handler.getLooper() == android.os.Looper.myLooper()) edit.run();
        else {
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            handler.post(() -> { try { edit.run(); } finally { done.countDown(); } });
            try { done.await(2, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
        if (Boolean.getBoolean("apklayer.debugkeys") && f instanceof android.widget.TextView tv)
            System.out.println("[apklayer] ime applied, field text: " + tv.getText());
    }

    /** The system selection changed (from the shell): make it the app's primary clip. */
    void clipboardIn(String text) {
        if (text.equals(lastClipboard)) return;
        lastClipboard = text;
        System.out.println("[apklayer] clipboard from the system (" + text.length() + " chars)");
        android.content.ClipboardManager cm =
                RuntimeEnvironment.getApplication().getSystemService(android.content.ClipboardManager.class);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("apklayer", text));
    }

    /** The app changed its primary clip: hand the text to the system selection. */
    void onAppClipboardChanged() {
        android.content.ClipboardManager cm =
                RuntimeEnvironment.getApplication().getSystemService(android.content.ClipboardManager.class);
        android.content.ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(RuntimeEnvironment.getApplication());
        if (text == null || text.length() == 0 || text.toString().equals(lastClipboard)) return;
        lastClipboard = text.toString();
        System.out.println("[apklayer] clipboard to the system (" + text.length() + " chars)");
        if (shell != null) shell.sendClipboard(lastClipboard);
    }

    /** Script step: the app itself copies text. */
    void clipboardCopy(String text) {
        RuntimeEnvironment.getApplication().getSystemService(android.content.ClipboardManager.class)
                .setPrimaryClip(android.content.ClipData.newPlainText("apklayer", text));
    }
}
