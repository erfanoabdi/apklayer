// android.hardware.camera2.MultiResolutionImageReader -- a bundle of one
// ImageReader per physical camera of a logical multi-camera, so an app can
// receive whichever resolution the device actually produced. API 31.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/
// MultiResolutionImageReader.java (aosp-mirror, android16-release). It is a
// pure composition over android.media.ImageReader -- AOSP's class has no native
// state of its own, it just fans calls out to the readers it created -- so this
// reproduces the real thing rather than standing in for it. Whether an image
// ever ARRIVES is a property of the capture session, not of this class.
package android.hardware.camera2;

import android.graphics.ImageFormat;
import android.hardware.camera2.params.MultiResolutionStreamInfo;
import android.media.Image;
import android.media.ImageReader;
import android.util.Size;
import android.view.Surface;
import java.util.Collection;
import java.util.concurrent.Executor;

public class MultiResolutionImageReader implements AutoCloseable {

    private final ImageReader[] mReaders;
    private final MultiResolutionStreamInfo[] mStreamInfo;
    private final int mFormat;
    private final int mMaxImages;

    /** AOSP :86 -- at least two streams, at least one outstanding image, and
     *  NV21 is rejected outright. */
    public MultiResolutionImageReader(Collection<MultiResolutionStreamInfo> streams,
            int format, int maxImages) {
        this(streams, format, maxImages, false, 0L);
    }

    /** AOSP :140. API 36 (FLAG_MULTIRESOLUTION_IMAGEREADER_USAGE_PUBLIC). */
    public MultiResolutionImageReader(Collection<MultiResolutionStreamInfo> streams,
            int format, int maxImages, long usage) {
        this(streams, format, maxImages, true, usage);
    }

    private MultiResolutionImageReader(Collection<MultiResolutionStreamInfo> streams,
            int format, int maxImages, boolean withUsage, long usage) {
        mFormat = format;
        mMaxImages = maxImages;
        if (streams == null || streams.size() <= 1) {
            throw new IllegalArgumentException(
                    "The streams info collection must contain at least 2 entries");
        }
        if (mMaxImages < 1) {
            throw new IllegalArgumentException(
                    "Maximum outstanding image count must be at least 1");
        }
        if (format == ImageFormat.NV21) {
            throw new IllegalArgumentException("NV21 format is not supported");
        }
        int numImageReaders = streams.size();
        mReaders = new ImageReader[numImageReaders];
        mStreamInfo = new MultiResolutionStreamInfo[numImageReaders];
        int index = 0;
        for (MultiResolutionStreamInfo streamInfo : streams) {
            mReaders[index] = withUsage
                    ? ImageReader.newInstance(streamInfo.getWidth(), streamInfo.getHeight(),
                            format, maxImages, usage)
                    : ImageReader.newInstance(streamInfo.getWidth(), streamInfo.getHeight(),
                            format, maxImages);
            mStreamInfo[index] = streamInfo;
            index++;
        }
    }

    /**
     * AOSP :208 -- registered on EVERY underlying reader.
     *
     * AOSP calls ImageReader.setOnImageAvailableListenerWithExecutor, which is
     * @hide and does not exist in this tree; the public overload here takes a
     * Handler instead. So the listener is wrapped: the underlying reader
     * delivers on a Handler, and the wrapper immediately re-posts to the app's
     * Executor, which is the thread the public contract actually promises.
     *
     * The trampoline Handler is bound to the MAIN Looper rather than the
     * calling thread's, because this method's signature makes no promise about
     * the calling thread having one -- ImageReader's Handler overload throws
     * IllegalArgumentException when it does not, and turning an Executor-based
     * registration into a throw would be a failure AOSP never produces here.
     * A null executor means "run it wherever it arrives", so the wrapper is
     * skipped and the listener runs on the trampoline thread directly.
     */
    public void setOnImageAvailableListener(ImageReader.OnImageAvailableListener listener,
            Executor executor) {
        final ImageReader.OnImageAvailableListener target;
        if (listener == null || executor == null) {
            target = listener;
        } else {
            final ImageReader.OnImageAvailableListener inner = listener;
            final Executor exec = executor;
            target = new ImageReader.OnImageAvailableListener() {
                @Override public void onImageAvailable(ImageReader reader) {
                    exec.execute(new Runnable() {
                        @Override public void run() { inner.onImageAvailable(reader); }
                    });
                }
            };
        }
        android.os.Handler handler = null;
        if (target != null) {
            android.os.Looper looper = android.os.Looper.myLooper();
            if (looper == null) looper = android.os.Looper.getMainLooper();
            handler = looper != null ? new android.os.Handler(looper) : null;
        }
        for (int i = 0; i < mReaders.length; i++) {
            mReaders[i].setOnImageAvailableListener(target, handler);
        }
    }

    @Override public void close() {
        flush();
        for (int i = 0; i < mReaders.length; i++) {
            mReaders[i].close();
        }
    }

    @Override protected void finalize() { close(); }

    /** AOSP :232 -- drains and closes every queued image on every reader. */
    public void flush() { flushOther(null); }

    /** AOSP :245. @hide in AOSP; kept because flush() is defined in terms of it. */
    public void flushOther(ImageReader reader) {
        for (int i = 0; i < mReaders.length; i++) {
            if (reader != null && reader == mReaders[i]) continue;
            while (true) {
                Image image = mReaders[i].acquireNextImage();
                if (image == null) break;
                image.close();
            }
        }
    }

    /** AOSP :271. @hide in AOSP. */
    public ImageReader[] getReaders() { return mReaders; }

    /** AOSP :294 -- the surface of the reader matching this size + physical id. */
    public Surface getSurface(Size configuredSize, String physicalCameraId) {
        if (configuredSize == null) {
            throw new NullPointerException("configuredSize must not be null");
        }
        if (physicalCameraId == null) {
            throw new NullPointerException("physicalCameraId must not be null");
        }
        for (int i = 0; i < mStreamInfo.length; i++) {
            if (mStreamInfo[i].getWidth() == configuredSize.getWidth()
                    && mStreamInfo[i].getHeight() == configuredSize.getHeight()
                    && physicalCameraId.equals(mStreamInfo[i].getPhysicalCameraId())) {
                return mReaders[i].getSurface();
            }
        }
        throw new IllegalArgumentException("configuredSize and physicalCameraId don't match with "
                + "this MultiResolutionImageReader");
    }

    /** AOSP :330 -- the SMALLEST reader's surface, which is the one the camera
     *  binds when it does not know which physical camera will serve the frame. */
    public Surface getSurface() {
        int minReaderSize = mReaders[0].getWidth() * mReaders[0].getHeight();
        Surface candidateSurface = mReaders[0].getSurface();
        for (int i = 1; i < mReaders.length; i++) {
            int readerSize = mReaders[i].getWidth() * mReaders[i].getHeight();
            if (readerSize < minReaderSize) {
                minReaderSize = readerSize;
                candidateSurface = mReaders[i].getSurface();
            }
        }
        return candidateSurface;
    }

    /** AOSP :356 */
    public MultiResolutionStreamInfo getStreamInfoForImageReader(ImageReader reader) {
        for (int i = 0; i < mReaders.length; i++) {
            if (reader == mReaders[i]) return mStreamInfo[i];
        }
        throw new IllegalArgumentException("ImageReader doesn't belong to this multi-resolution "
                + "imagereader");
    }

    /** @hide -- the format every underlying reader was created with. */
    public int getFormat() { return mFormat; }

    /** @hide -- the maxImages every underlying reader was created with. */
    public int getMaxImages() { return mMaxImages; }
}
