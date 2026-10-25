// android.view.ViewManager -- AOSP declares this an INTERFACE, and that KIND is
// load-bearing.
//
// WHY (found 2026-07-29, same audit that caught ViewTranslationCallback): we
// shipped it as a `public class` with three empty methods (a v1.0.92
// auto-generated stub). Anything that correctly declares
// `implements ViewManager` / `extends ViewManager` then fails to link with
// IncompatibleClassChangeError -- the JVM refuses an interface slot filled by a
// class. It also made two AOSP type relationships unexpressible, so both were
// silently missing from our framework:
//   ViewGroup     implements ViewManager   (frameworks/base ViewGroup.java)
//   WindowManager extends    ViewManager   (frameworks/base WindowManager.java)
// so a guest doing `(ViewManager) someViewGroup`, or handing a WindowManager to
// a ViewManager-typed parameter, got a ClassCastException. Both relationships
// are wired now.
//
// Surface verified verbatim against
// frameworks/base/core/java/android/view/ViewManager.java:
//   public interface ViewManager {
//       public void addView(View view, ViewGroup.LayoutParams params);
//       public void updateViewLayout(View view, ViewGroup.LayoutParams params);
//       public void removeView(View view);
//   }
package android.view;

/** Interface to let you add and remove child views to an Activity. */
public interface ViewManager {
    void addView(View view, ViewGroup.LayoutParams params);
    void updateViewLayout(View view, ViewGroup.LayoutParams params);
    void removeView(View view);
}
