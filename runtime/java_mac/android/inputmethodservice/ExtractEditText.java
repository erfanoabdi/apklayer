package android.inputmethodservice;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.ExtractedText;
import android.widget.EditText;

/** The editor an IME puts up in fullscreen ("extract") mode: it holds a copy of
 *  the application's text and mirrors edits back through the InputConnection.
 *
 *  AOSP wires this to the IME through a package-private setIME() called from
 *  InputMethodService.setExtractView(), which finds it by the internal id
 *  com.android.internal.R.id.inputExtractEditText. Mac.apk ships no internal
 *  resources, so setExtractView() locates it by TYPE instead (a recursive scan
 *  for an ExtractEditText instance) -- the same object AOSP's id names, found a
 *  different way, with no per-app or internal-resource dependency.
 *
 *  The re-entrancy counter is the load-bearing part and is faithful: while the
 *  IME is pushing text INTO this editor, selection changes must not be
 *  reported back out, or the IME and the application fight over the cursor.
 *
 *  API 3. Source: frameworks/base
 *  core/java/android/inputmethodservice/ExtractEditText.java (android16-release). */
public class ExtractEditText extends EditText {

    // AOSP tests these against android.R.id.selectAll / android.R.id.replaceText.
    // This tree ships no android.R, so the two public framework ids are named
    // here as literals. Values read out of the SDK's own android.jar with
    // `javap -constants android.R$id` (android-30), which is the same table an
    // app compiles against -- not guessed, and stable since they are public
    // platform ids.
    private static final int ID_SELECT_ALL   = 16908319; // 0x0102001f
    private static final int ID_REPLACE_TEXT = 16908340; // 0x01020034

    private InputMethodService mIME;
    private int mSettingExtractedText;

    /** AOSP: `super(context, null);` -- deliberately no attrs, so the default
     *  EditText style does not apply to the single-arg form. */
    public ExtractEditText(Context context) {
        super(context, null);
    }

    /** AOSP passes com.android.internal.R.attr.editTextStyle as the default
     *  style attribute. That attr is platform-internal and unavailable here, so
     *  the attrs are applied without it; EditText's own default styling is what
     *  remains. */
    public ExtractEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExtractEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** AOSP chains to EditText(Context, AttributeSet, int, int). This tree's
     *  EditText stops at the three-argument form, so the explicit default-style
     *  RESOURCE (as opposed to the default-style ATTRIBUTE) is dropped; every
     *  other constructor above already behaves that way, so this is consistent
     *  with the rest of the widget package rather than a local shortcut. The
     *  four-argument entry point still exists, which is what an app compiled
     *  against the SDK needs in order to link. */
    public ExtractEditText(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr);
    }

    /** Package-private in AOSP; InputMethodService.setExtractView() is the only
     *  caller. */
    void setIME(InputMethodService ime) {
        mIME = ime;
    }

    /** Start making changes that will not be reported to the client. Callers
     *  must pair this with finishInternalChanges(); AOSP does not balance it
     *  with try/finally either, so the counter is a plain increment. */
    public void startInternalChanges() {
        mSettingExtractedText += 1;
    }

    public void finishInternalChanges() {
        mSettingExtractedText -= 1;
    }

    /** AOSP brackets the super call so an onSelectionChanged fired from inside
     *  it is suppressed, and uses try/finally so a throwing super still
     *  restores the count. */
    @Override public void setExtractedText(ExtractedText text) {
        try {
            mSettingExtractedText++;
            super.setExtractedText(text);
        } finally {
            mSettingExtractedText--;
        }
    }

    /** Report a selection change back to the application, but only when the
     *  change did NOT originate from the IME writing into this editor. */
    @Override protected void onSelectionChanged(int selStart, int selEnd) {
        if (mSettingExtractedText == 0 && mIME != null && selStart >= 0 && selEnd >= 0) {
            mIME.onExtractedSelectionChanged(selStart, selEnd);
        }
    }

    /** AOSP: give the normal click handling first refusal, then tell the IME. */
    @Override public boolean performClick() {
        if (!super.performClick() && mIME != null) {
            mIME.onExtractedTextClicked();
            return true;
        }
        return false;
    }

    /** AOSP hands most context-menu actions to the IME (which forwards them to
     *  the real editor over the InputConnection) and keeps only selectAll /
     *  replaceText locally. android.R.id.selectAll and android.R.id.replaceText
     *  are the two ids AOSP tests against. */
    @Override public boolean onTextContextMenuItem(int id) {
        if (id == ID_SELECT_ALL || id == ID_REPLACE_TEXT) {
            return super.onTextContextMenuItem(id);
        }
        if (mIME != null && mIME.onExtractTextContextMenuItem(id)) {
            return true;
        }
        return super.onTextContextMenuItem(id);
    }

    /** AOSP: `return true;` -- the extract editor is always the IME's target. */
    @Override public boolean isInputMethodTarget() {
        return true;
    }

    /** AOSP: the extract editor is scrollable when its content is taller than
     *  its viewport. InputMethodService uses this to decide whether the
     *  candidates strip has to give up its space. */
    public boolean hasVerticalScrollBar() {
        return computeVerticalScrollRange() > computeVerticalScrollExtent();
    }

    /** AOSP overrides the three focus predicates to track ENABLED instead: the
     *  extract editor lives in the IME's window, which never holds real focus,
     *  yet it must behave as the focused editor so the cursor draws and text
     *  selection works. */
    @Override public boolean hasWindowFocus() { return isEnabled(); }

    @Override public boolean isFocused() { return isEnabled(); }

    @Override public boolean hasFocus() { return isEnabled(); }
}
