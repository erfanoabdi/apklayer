package hello.system;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

/**
 * An input method of this app: listed and enabled (ImeCheck); once bound to a focused field of another app (tests/vm/ime-bind.py) it reports the
 * binding's callbacks and commits one text into the field.
 */
public final class Ime extends InputMethodService {
    @Override public void onBindInput() { System.out.println("[ime] onBindInput uid=" + getCurrentInputBinding().getUid()); }

    @Override public void onStartInput(EditorInfo editor, boolean restarting) {
        System.out.println("[ime] onStartInput " + editor.packageName + " restarting=" + restarting);
        if (getCurrentInputConnection() != null) System.out.println("[ime] commitText " + getCurrentInputConnection().commitText("hello from ime", 1));
    }

    @Override public View onCreateInputView() {
        System.out.println("[ime] onCreateInputView");
        TextView keys = new TextView(this);
        keys.setText("test keyboard");
        return keys;
    }

    @Override public void onStartInputView(EditorInfo editor, boolean restarting) { System.out.println("[ime] onStartInputView " + editor.packageName); }
}
