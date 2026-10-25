// android.text.method.MovementMethod -- AOSP interface providing
// cursor positioning, scrolling and text selection in a TextView.
// AOSP: frameworks/base/core/java/android/text/method/MovementMethod.java
// (was a v1.0.92 empty class stub; converted to the real interface
// shape so BaseMovementMethod/ScrollingMovementMethod can implement
// it -- Calculator's CalculatorResult constructor instantiates
// ScrollingMovementMethod via getInstance()).
package android.text.method;

import android.text.Spannable;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

public interface MovementMethod {
    void initialize(TextView widget, Spannable text);
    boolean onKeyDown(TextView widget, Spannable text, int keyCode, KeyEvent event);
    boolean onKeyUp(TextView widget, Spannable text, int keyCode, KeyEvent event);
    boolean onKeyOther(TextView view, Spannable text, KeyEvent event);
    void onTakeFocus(TextView widget, Spannable text, int direction);
    boolean onTrackballEvent(TextView widget, Spannable text, MotionEvent event);
    boolean onTouchEvent(TextView widget, Spannable text, MotionEvent event);
    boolean onGenericMotionEvent(TextView widget, Spannable text, MotionEvent event);
    boolean canSelectArbitrarily();
}
