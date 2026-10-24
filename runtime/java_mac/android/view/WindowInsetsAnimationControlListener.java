package android.view;

/**
 * Callbacks for an app-driven inset animation (drag the keyboard in/out).
 * AOSP declares it as an interface.
 * Source: frameworks/base/core/java/android/view/WindowInsetsAnimationControlListener.java
 */
public interface WindowInsetsAnimationControlListener {

    void onReady(WindowInsetsAnimationController controller, int types);

    void onFinished(WindowInsetsAnimationController controller);

    void onCancelled(WindowInsetsAnimationController controller);
}
