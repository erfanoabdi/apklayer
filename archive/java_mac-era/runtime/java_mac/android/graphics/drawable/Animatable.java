// android.graphics.drawable.Animatable -- AOSP INTERFACE (NOT class)
// for drawables that have animations. Must be an interface for
// androidx CircularProgressDrawable and many other animated
// drawables to implement it. The v1.0.92 stub was a class which
// caused IncompatibleClassChangeError at class load time. v1.0.98.
package android.graphics.drawable;

public interface Animatable {
    void start();
    void stop();
    boolean isRunning();
}
