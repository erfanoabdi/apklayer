package android.window;
/** Controls the system splash screen for an activity. Mac.apk shows no separate
 *  splash window, so exit-animation listeners never fire and the theme override
 *  is inert. Source: frameworks/base/core/java/android/window/SplashScreen.java */
public interface SplashScreen {
    interface OnExitAnimationListener {
        void onSplashScreenExit(android.window.SplashScreenView view);
    }
    default void setOnExitAnimationListener(OnExitAnimationListener listener) { }
    default void clearOnExitAnimationListener() { }
    default void setSplashScreenTheme(int themeId) { }
}
