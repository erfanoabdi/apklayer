// android.widget.Advanceable -- interface for widgets (AdapterViewFlipper,
// StackView) that can be advanced one step by a host (e.g. a launcher).
// Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/Advanceable.java
package android.widget;

public interface Advanceable {
    void advance();
    void fyiWillBeAdvancedByHostKThx();
}
