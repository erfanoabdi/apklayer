// android.widget.TextSwitcher -- a ViewSwitcher whose two children are
// TextViews; setText/setCurrentText push text to the next/current child and
// flip. Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/TextSwitcher.java
//   public class TextSwitcher extends ViewSwitcher
package android.widget;

import android.content.Context;
import android.view.View;

public class TextSwitcher extends ViewSwitcher {
    public TextSwitcher(Context context) { super(context); }
    public TextSwitcher(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public void addView(View child, int index,
            android.view.ViewGroup.LayoutParams params) {
        if (!(child instanceof TextView)) {
            throw new IllegalArgumentException(
                    "TextSwitcher children must be instances of TextView");
        }
        super.addView(child, index, params);
    }

    public void setText(CharSequence text) {
        final TextView t = (TextView) getNextView();
        if (t != null) t.setText(text);
        showNext();
    }

    public void setCurrentText(CharSequence text) {
        final View v = getCurrentView();
        if (v instanceof TextView) ((TextView) v).setText(text);
    }
}
