// android.text.method.KeyListener -- AOSP interface that converts hardware /
// soft key events into edits on an Editable. AOSP:
//   frameworks/base/core/java/android/text/method/KeyListener.java
//   public interface KeyListener {
//       int getInputType();
//       boolean onKeyDown(View, Editable, int, KeyEvent);
//       boolean onKeyUp(View, Editable, int, KeyEvent);
//       boolean onKeyOther(View, Editable, KeyEvent);
//       void clearMetaKeyState(View, Editable, int);
//   }
//
// Promoted from the v1.0.92 empty class stub to the real interface so the
// listener chain (MetaKeyKeyListener -> BaseKeyListener -> NumberKeyListener
// -> DigitsKeyListener) implements it. TextView holds it as a field type only,
// so the class->interface change is transparent there.
package android.text.method;

import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;

public interface KeyListener {
    int getInputType();
    boolean onKeyDown(View view, Editable text, int keyCode, KeyEvent event);
    boolean onKeyUp(View view, Editable text, int keyCode, KeyEvent event);
    boolean onKeyOther(View view, Editable text, KeyEvent event);
    void clearMetaKeyState(View view, Editable content, int states);
}
