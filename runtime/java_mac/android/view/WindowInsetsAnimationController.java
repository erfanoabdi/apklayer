package android.view;

import android.graphics.Insets;

/**
 * Handle for an app-controlled inset animation: the app drives the fraction
 * itself (following a drag) instead of letting the system animate.
 *
 * Mac.apk has no system bars and no soft keyboard, so
 * WindowInsetsController.controlWindowInsetsAnimation never produces a
 * controller and the listener receives onCancelled -- which is exactly what
 * AOSP does when the requested inset type is not present on the window. Apps
 * handle that path already. The interface must exist for their callback code
 * to link.
 *
 * Source: frameworks/base/core/java/android/view/WindowInsetsAnimationController.java
 */
public interface WindowInsetsAnimationController {

    Insets getHiddenStateInsets();

    Insets getShownStateInsets();

    Insets getCurrentInsets();

    float getCurrentFraction();

    float getCurrentAlpha();

    int getTypes();

    void setInsetsAndAlpha(Insets insets, float alpha, float fraction);

    void finish(boolean shown);

    boolean isReady();

    boolean isFinished();

    boolean isCancelled();
}
