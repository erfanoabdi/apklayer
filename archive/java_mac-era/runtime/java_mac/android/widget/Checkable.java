// android.widget.Checkable -- AOSP API-1 interface for widgets that
// have a "checked" state (CheckBox, RadioButton, Switch,
// MaterialCardView with checkable=true, etc.). Real Android source:
//   frameworks/base/core/java/android/widget/Checkable.java
//
// Hit by F-Droid's Latest tab: MaterialCardView declares
// `implements Checkable` so its class verifier fails (NoClassDefFoundError
// android/widget/Checkable) before any instance can be constructed.
// Once the interface exists, the card inflates and the
// LatestAdapter binds the per-app row.
package android.widget;

public interface Checkable {
    void setChecked(boolean checked);
    boolean isChecked();
    void toggle();
}
