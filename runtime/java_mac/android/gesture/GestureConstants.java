// android.gesture.GestureConstants -- package-private shared constants for the
// gesture recognizer. Mirrors AOSP
// frameworks/base/core/java/android/gesture/GestureConstants.java verbatim
// (fetched from aosp-mirror android16-release, 2026-08-24).
package android.gesture;

interface GestureConstants {
    static final int STROKE_STRING_BUFFER_SIZE = 1024;
    static final int STROKE_POINT_BUFFER_SIZE = 100; // number of points

    static final int IO_BUFFER_SIZE = 32 * 1024; // 32K

    static final String LOG_TAG = "Gestures";
}
