// android.text.method.BaseKeyListener -- AOSP base implementing common edit
// operations (backspace / forward-delete) on top of the meta-state base.
// AOSP:
//   frameworks/base/core/java/android/text/method/BaseKeyListener.java
//   public abstract class BaseKeyListener extends MetaKeyKeyListener
//       implements KeyListener
// NumberKeyListener / TextKeyListener descend from this. Our input flows
// through the IME path, so the delete helpers default to false; the class
// exists so the listener chain compiles and `instanceof` checks round-trip.
package android.text.method;

import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;

public abstract class BaseKeyListener extends MetaKeyKeyListener {
    public BaseKeyListener() {}

    public boolean backspace(View view, Editable content, int keyCode, KeyEvent event) {
        return false;
    }
    public boolean forwardDelete(View view, Editable content, int keyCode, KeyEvent event) {
        return false;
    }

    /**
     * AOSP BaseKeyListener.makeTextContentType(Capitalize, boolean)
     * (BaseKeyListener.java:475-492) -- the shared getInputType() body of
     * TextKeyListener / QwertyKeyListener / MultiTapKeyListener. Package-private
     * on AOSP too.
     */
    static int makeTextContentType(TextKeyListener.Capitalize caps, boolean autoText) {
        int contentType = android.text.InputType.TYPE_CLASS_TEXT;
        switch (caps) {
            case CHARACTERS:
                contentType |= android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
                break;
            case WORDS:
                contentType |= android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS;
                break;
            case SENTENCES:
                contentType |= android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
                break;
            default:
                break;
        }
        if (autoText) {
            contentType |= android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
        }
        return contentType;
    }
}
