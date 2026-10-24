// android.text.method.ArrowKeyMovementMethod -- AOSP default movement method
// for editable text (caret navigation by arrows, selection). AOSP:
//   frameworks/base/core/java/android/text/method/ArrowKeyMovementMethod.java
//   public class ArrowKeyMovementMethod extends BaseMovementMethod
//       implements MovementMethod
// with a process-wide singleton via getInstance(). EditText installs it by
// default. We don't drive on-canvas caret movement, so inheriting
// BaseMovementMethod's handlers is the whole behavior; canSelectArbitrarily()
// returns true to match AOSP (the field is selectable).
package android.text.method;

public class ArrowKeyMovementMethod extends BaseMovementMethod
        implements MovementMethod {
    private static ArrowKeyMovementMethod sInstance;

    public ArrowKeyMovementMethod() {}

    public static MovementMethod getInstance() {
        if (sInstance == null) sInstance = new ArrowKeyMovementMethod();
        return sInstance;
    }

    @Override
    public boolean canSelectArbitrarily() { return true; }

    /** AOSP ArrowKeyMovementMethod.initialize (android14 :362-364):
     *  `Selection.setSelection(text, 0);`. TextView calls it from its
     *  constructor and from every setText, which is why an EditText's
     *  selection is never -1 on a device. */
    @Override
    public void initialize(android.widget.TextView widget, android.text.Spannable text) {
        android.text.Selection.setSelection(text, 0);
    }
}
