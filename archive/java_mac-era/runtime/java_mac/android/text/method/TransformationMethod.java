// android.text.method.TransformationMethod -- AOSP interface for a TextView
// text transformation (password masking, single-line, all-caps). AOSP:
//   frameworks/base/core/java/android/text/method/TransformationMethod.java
//   public interface TransformationMethod {
//       CharSequence getTransformation(CharSequence source, View view);
//       void onFocusChanged(View view, CharSequence sourceText, boolean focused,
//                           int direction, Rect previouslyFocusedRect);
//   }
//
// Promoted from the v1.0.92 empty class stub to the real interface so the
// concrete transforms (Password / SingleLine / HideReturns) implement it and
// TextView.setTransformationMethod type-checks against the published contract.
// TextView uses it only as a parameter/return type, so the class->interface
// change is transparent there.
package android.text.method;

import android.graphics.Rect;
import android.view.View;

public interface TransformationMethod {
    CharSequence getTransformation(CharSequence source, View view);

    void onFocusChanged(View view, CharSequence sourceText, boolean focused,
                        int direction, Rect previouslyFocusedRect);
}
