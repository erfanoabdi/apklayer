// android.text.method.ScrollingMovementMethod -- AOSP movement method
// that scrolls a TextView without selecting. Calculator's
// CalculatorResult installs it from its constructor; the class only
// needs to exist with the singleton getInstance() for instantiation
// to succeed (we don't drive text scrolling).
// AOSP: frameworks/base/core/java/android/text/method/ScrollingMovementMethod.java
//   public class ScrollingMovementMethod extends BaseMovementMethod
//       implements MovementMethod
package android.text.method;

public class ScrollingMovementMethod extends BaseMovementMethod
        implements MovementMethod {
    private static ScrollingMovementMethod sInstance;

    public static MovementMethod getInstance() {
        if (sInstance == null) sInstance = new ScrollingMovementMethod();
        return sInstance;
    }
}
