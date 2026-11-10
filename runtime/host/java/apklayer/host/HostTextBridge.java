package apklayer.host;

import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowInputMethodManager;
import org.robolectric.util.ReflectionHelpers;

/** Text input and clipboard between the app and the shell: the IME on and off for the focused field, composing text, the system selection. */
final class HostTextBridge {
    ShellClient shell;
    private View imeField;             // the focused field the shell's input method serves (null: none)
    private String surrounding = "";   // its cursor, anchor and text as last sent (followSurrounding)
    private String lastClipboard = ""; // last text exchanged with the shell, to avoid echo loops
    private View composingIn;          // the field holding the IME's preedit (text-input-v3: a done replaces only an existing preedit)

    /** Tells the shell when a text field gets or loses focus, with its EditorInfo's input type and IME options (the keyboard's layout and Enter key). */
    void followTextInput() {
        View editor = HostWindows.focusedEditor();
        HostInputMethodClient.follow(editor);
        if (shell == null) return;
        if (editor != imeField) {
            imeField = editor;
            surrounding = "";
            EditorInfo info = new EditorInfo();
            if (editor != null) editor.onCreateInputConnection(info);
            shell.sendTextInput(editor == null ? 0 : 1, info.inputType, info.imeOptions);
            if (Boolean.getBoolean("apklayer.debugkeys"))
                System.out.println("[apklayer] text input " + (editor != null ? "on, type 0x" + Integer.toHexString(info.inputType) + " options 0x" + Integer.toHexString(info.imeOptions) : "off"));
        }
        followSurrounding();
    }

    /** The focused field's text around the cursor and its selection, when they change: the phones' keyboards (Maliit) need them for Backspace
     * after a cursor move or with a selection, and for capitals at a sentence's start. Not while composing (Qt's excludes the preedit), not a password.
     * shortcut: TextViews only (a WebView's field sends none), 500 characters each side; upgrade with an InputConnection's getSurroundingText if needed */
    private void followSurrounding() {
        if (!(imeField instanceof android.widget.TextView tv) || !(tv.getText() instanceof android.text.Spannable text)) return;
        int s = tv.getSelectionStart(), e = tv.getSelectionEnd();
        if (s < 0 || android.view.inputmethod.BaseInputConnection.getComposingSpanStart(text) >= 0) return;
        int variation = tv.getInputType() & (android.text.InputType.TYPE_MASK_CLASS | android.text.InputType.TYPE_MASK_VARIATION);
        boolean hidden = variation == 0x81 || variation == 0x91 || variation == 0xe1 || variation == 0x12;  // the password variations
        int from = hidden ? s : Math.max(0, Math.min(s, e) - 500), to = hidden ? s : Math.min(text.length(), Math.max(s, e) + 500);
        if (hidden) e = s;
        String around = text.subSequence(from, to).toString(), state = (s - from) + "," + (e - from) + "," + around;
        if (state.equals(surrounding)) return;
        surrounding = state;
        shell.sendSurrounding(e - from, s - from, around);  // Qt's cursor is the selection's moving end (Android's end), its anchor the start
    }

    /** InputMethodManager.showSoftInput (TextView's tap in a focused field, or the app's own call): the shell shows a keyboard the user closed again;
     * hideSoftInputFromWindow (ImeShadow): the shell hides it, the field keeps focus. */
    void followShowRequests() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getSystemService(InputMethodManager.class)).setSoftInputVisibilityHandler(visible -> {
            ReflectionHelpers.setStaticField(ShadowInputMethodManager.class, "softInputVisible", false);  // the shadow reports changes only: every call counts
            if (shell == null || imeField == null) return;
            shell.sendTextInput(visible ? 2 : 4, 0, 0);
            if (Boolean.getBoolean("apklayer.debugkeys")) System.out.println("[apklayer] text input " + (visible ? "show" : "hide") + " request");
        });
    }

    /** Every hideSoftInputFromWindow reaches the visibility handler (the stock shadow reports only a change from visible, which the handler resets).
     * shortcut: any window token hides it (Android ignores a token of a window that is not the served one); check the token if an app needs it */
    @org.robolectric.annotation.Implements(value = InputMethodManager.class, isInAndroidSdk = false)
    public static final class ImeShadow extends ShadowInputMethodManager {
        @org.robolectric.annotation.Implementation
        @Override protected boolean hideSoftInputFromWindow(android.os.IBinder token, int flags, android.os.ResultReceiver receiver) {
            ReflectionHelpers.setStaticField(ShadowInputMethodManager.class, "softInputVisible", true);
            return super.hideSoftInputFromWindow(token, flags, receiver);
        }
    }

    /**
     * IME event (zwp_text_input_v3 done): delete surrounding text, commit, then the new preedit,
     * through the focused field's InputConnection (PLAN P2.6).
     */
    void ime(int before, int after, String commit, String preedit) {
        View f = HostWindows.focusedEditor();
        if (f == null) return;
        EditorInfo info = new EditorInfo();
        android.view.inputmethod.InputConnection ic = f.onCreateInputConnection(info);
        if (ic == null) return;
        boolean hadPreedit = composingIn == f;
        composingIn = preedit.isEmpty() ? null : f;
        Runnable edit = () -> {
            ic.beginBatchEdit();
            CharSequence selected = (before > 0 || after > 0) && commit.isEmpty() && preedit.isEmpty() && !hadPreedit ? ic.getSelectedText(0) : null;
            // a keyboard's Backspace or Delete with text selected deletes the selection (Android's IMEs know it; ours send a plain deletion)
            if (selected != null && selected.length() > 0) ic.commitText("", 1);
            else if (before > 0 || after > 0) ic.deleteSurroundingText(before, after);
            int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
            // a keyboard's Enter (maliit.c commits "\n"): the field's editor action unless it takes newlines, as LatinIME does
            if (commit.equals("\n") && (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 && action != EditorInfo.IME_ACTION_NONE
                    && action != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(action);
            else if (!commit.isEmpty()) ic.commitText(commit, 1);
            if (!preedit.isEmpty()) ic.setComposingText(preedit, 1);
            // preedit cancelled; with none placed an empty done (the desktop's IME on enable) is no edit: setComposingText("") would replace the selection
            else if (commit.isEmpty() && hadPreedit) ic.setComposingText("", 1);
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
