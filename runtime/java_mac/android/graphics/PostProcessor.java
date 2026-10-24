package android.graphics;

/**
 * AOSP-faithful android.graphics.PostProcessor. A callback the app supplies to
 * ImageDecoder to draw over the decoded image before it is finalized. The
 * return value is a PixelFormat opacity constant. Mirrors
 * frameworks/base/graphics/java/android/graphics/PostProcessor.java.
 */
public interface PostProcessor {
    int onPostProcess(Canvas canvas);
}
