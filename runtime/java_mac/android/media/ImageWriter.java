// android.media.ImageWriter -- a producer that pushes Images into a consumer
// Surface (the inverse of ImageReader). AOSP frameworks/base
// media/java/android/media/ImageWriter.java.
//
// Mac.apk has no Surface-backed consumer pipeline, so dequeueInputImage returns
// null (no buffer slot) and queueInputImage is a no-op. The class links for
// CameraX/codec reprocessing setup; it produces no frames.
package android.media;

public class ImageWriter implements AutoCloseable {
    private final int mMaxImages;
    private final int mFormat;
    private OnImageReleasedListener mListener;

    protected ImageWriter(int maxImages, int format) {
        mMaxImages = maxImages;
        mFormat = format;
    }

    // Published descriptors take android.view.Surface; the prior Object params
    // compiled but would not resolve a guest's newInstance(surface, n) call
    // (descriptor mismatch -> NoSuchMethodError).
    public static ImageWriter newInstance(android.view.Surface surface, int maxImages) {
        return new ImageWriter(maxImages, android.graphics.ImageFormat.UNKNOWN);
    }
    public static ImageWriter newInstance(android.view.Surface surface, int maxImages, int format) {
        return new ImageWriter(maxImages, format);
    }

    public int getMaxImages() { return mMaxImages; }
    public int getFormat() { return mFormat; }
    public int getWidth() { return -1; }
    public int getHeight() { return -1; }
    public int getHardwareBufferFormat() { return mFormat; }
    public long getUsage() { return 0; }
    public int getDataSpace() { return 0; }

    public Image dequeueInputImage() { return null; }
    public void queueInputImage(Image image) {}
    public void setOnImageReleasedListener(OnImageReleasedListener listener, android.os.Handler handler) {
        mListener = listener;
    }
    public void close() {}

    public interface OnImageReleasedListener {
        void onImageReleased(ImageWriter writer);
    }

    public static final class Builder {
        private int maxImages = 1;
        private int format = android.graphics.ImageFormat.UNKNOWN;
        public Builder(android.view.Surface surface) {}
        public Builder setMaxImages(int maxImages) { this.maxImages = maxImages; return this; }
        public Builder setImageFormat(int imageFormat) { this.format = imageFormat; return this; }
        public Builder setWidthAndHeight(int width, int height) { return this; }
        public Builder setUsage(long usage) { return this; }
        public Builder setDataSpace(int dataSpace) { return this; }
        /** API 33. The HardwareBuffer format the writer's images use, as
         *  distinct from setImageFormat's ImageFormat value. Recorded on the
         *  builder; this tree's ImageWriter has no HardwareBuffer producer
         *  behind it, so the two describe the same (empty) queue. AOSP rejects
         *  combining it with setImageFormat, but accepting a call AOSP would
         *  have rejected is the safe direction. */
        public Builder setHardwareBufferFormat(int hardwareBufferFormat) {
            this.format = hardwareBufferFormat;
            return this;
        }
        public ImageWriter build() { return new ImageWriter(maxImages, format); }
    }
}
