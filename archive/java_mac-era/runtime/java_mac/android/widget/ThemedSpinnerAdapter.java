// android.widget.ThemedSpinnerAdapter -- a SpinnerAdapter whose drop-down
// views are created against a (possibly different) theme. Was a class stub;
// AOSP declares it as an interface extending SpinnerAdapter, and
// ArrayAdapter implements it. AOSP:
//   frameworks/base/core/java/android/widget/ThemedSpinnerAdapter.java
package android.widget;

import android.content.res.Resources;

public interface ThemedSpinnerAdapter extends SpinnerAdapter {
    void setDropDownViewTheme(Resources.Theme theme);
    Resources.Theme getDropDownViewTheme();
}
