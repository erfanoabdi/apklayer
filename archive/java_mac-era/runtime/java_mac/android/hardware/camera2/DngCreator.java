// android.hardware.camera2.DngCreator -- writes a RAW_SENSOR capture out as a
// DNG (TIFF/EP) file, using the camera's characteristics + the capture's result
// metadata to fill the DNG header. API 21.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/DngCreator.java
// (aosp-mirror/platform_frameworks_base, android16-release) and the validation
// its native half performs
// (core/jni/android_hardware_camera2_DngCreator.cpp).
//
// WHAT IS REAL HERE, AND WHY THE FAILURES ARE AOSP'S OWN
//
// AOSP splits this class in two: a Java half that validates arguments and
// formats the capture timestamp, and a native half (libandroid_runtime's
// TiffWriter) that encodes the file. The Java half is reproduced faithfully
// below, including the constructor's timebase arithmetic and every
// IllegalArgumentException AOSP's javadoc names.
//
// The encoder is NOT reproduced, and that costs nothing today, because no call
// can reach it:
//
//   * writeImage rejects any Image whose format is not ImageFormat.RAW_SENSOR
//     -- AOSP's own first check. Nothing in this runtime produces a RAW_SENSOR
//     Image: the AVFoundation capture path delivers RGBA/YUV frames, and
//     StreamConfigurationMap advertises no RAW output.
//   * writeByteBuffer and writeInputStream reach AOSP's native
//     validateDngHeader / NativeContext::setup, which fail with
//     IllegalArgumentException("Missing metadata fields for tag ...") when the
//     characteristics lack the RAW tags a DNG header needs
//     (SENSOR_INFO_WHITE_LEVEL, SENSOR_INFO_COLOR_FILTER_ARRANGEMENT,
//     SENSOR_COLOR_TRANSFORM1, SENSOR_CALIBRATION_TRANSFORM1,
//     SENSOR_FORWARD_MATRIX1, SENSOR_REFERENCE_ILLUMINANT1,
//     SENSOR_INFO_PIXEL_ARRAY_SIZE). This backend reports none of them, so that
//     is the exception an app gets -- the same one it would get from a real
//     device whose HAL is not RAW-capable.
//
// The unreachable tail after those checks throws UnsupportedOperationException
// naming the missing encoder, so that IF a future backend ever supplies both
// RAW images and full RAW characteristics, the gap announces itself at the
// exact call rather than writing a malformed .dng.
package android.hardware.camera2;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.location.Location;
import android.media.ExifInterface;
import android.media.Image;
import android.util.Size;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public final class DngCreator implements AutoCloseable {

    /** AOSP DngCreator.java:457 -- thumbnails larger than this are rejected. */
    public static final int MAX_THUMBNAIL_DIMENSION = 256;

    // AOSP DngCreator.java:474
    private static final String TIFF_DATETIME_FORMAT = "yyyy:MM:dd HH:mm:ss";
    // AOSP DngCreator.java:484-485
    private static final int DEFAULT_PIXEL_STRIDE = 2;
    // TIFF/EP "Unknown" orientation; ExifInterface and the TIFF spec disagree
    // on the value, which is why AOSP remaps it (DngCreator.java setOrientation).
    private static final int TAG_ORIENTATION_UNKNOWN = 9;

    private final CameraCharacteristics mCharacteristics;
    private final CaptureResult mMetadata;
    private final String mFormattedCaptureTime;

    private String mDescription;
    private int mOrientation = TAG_ORIENTATION_UNKNOWN;
    private Location mLocation;
    private boolean mClosed;

    /**
     * AOSP DngCreator.java:110. Rejects a null argument, then converts the
     * capture's SENSOR_TIMESTAMP from the sensor timebase into wall-clock time
     * and formats it for the DNG's DateTime tag.
     *
     * One documented deviation. AOSP unboxes
     * characteristics.get(SENSOR_INFO_TIMESTAMP_SOURCE) straight into an int,
     * which NPEs if the tag is absent -- impossible on a real camera, where the
     * tag has been mandatory since API 21, but the normal case for a
     * CameraCharacteristics this backend builds. A null therefore takes AOSP's
     * own "Unexpected time source - treat as CLOCK_MONOTONIC" branch rather
     * than throwing a NullPointerException that no device could produce.
     */
    public DngCreator(CameraCharacteristics characteristics, CaptureResult metadata) {
        if (characteristics == null || metadata == null) {
            throw new IllegalArgumentException("Null argument to DngCreator constructor");
        }
        mCharacteristics = characteristics;
        mMetadata = metadata;

        long currentTime = System.currentTimeMillis();
        long timeOffset;
        Integer timestampSource = characteristics.get(
                CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
        if (timestampSource != null && timestampSource
                == CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) {
            // Same timebase as SystemClock.elapsedRealtime(), i.e. CLOCK_BOOTTIME.
            timeOffset = currentTime - android.os.SystemClock.elapsedRealtime();
        } else {
            // SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN, an unrecognised source, or
            // an absent tag: AOSP treats all of these as CLOCK_MONOTONIC.
            timeOffset = currentTime - android.os.SystemClock.uptimeMillis();
        }

        Long timestamp = metadata.get(CaptureResult.SENSOR_TIMESTAMP);
        long captureTime = currentTime;
        if (timestamp != null) {
            captureTime = timestamp / 1000000 + timeOffset;
        }

        // Built fresh each time because the zone can change while a long-lived
        // app is running -- AOSP's own comment.
        final DateFormat dateTimeStampFormat =
                new SimpleDateFormat(TIFF_DATETIME_FORMAT, Locale.US);
        dateTimeStampFormat.setTimeZone(TimeZone.getDefault());
        mFormattedCaptureTime = dateTimeStampFormat.format(captureTime);
    }

    /** AOSP :161. The DNG's ImageDescription tag. */
    public DngCreator setDescription(String description) {
        if (description == null) {
            throw new IllegalArgumentException("Null description passed to setDescription.");
        }
        mDescription = description;
        return this;
    }

    /**
     * AOSP :247. The DNG's Orientation tag, given as an ExifInterface
     * ORIENTATION_* value. AOSP rejects anything outside
     * ORIENTATION_UNDEFINED..ORIENTATION_ROTATE_270 and remaps UNDEFINED,
     * because ExifInterface and TIFF/EP disagree on the "unknown" value.
     */
    public DngCreator setOrientation(int orientation) {
        if (orientation < ExifInterface.ORIENTATION_UNDEFINED
                || orientation > ExifInterface.ORIENTATION_ROTATE_270) {
            throw new IllegalArgumentException("Orientation " + orientation
                    + " is not a valid EXIF orientation value");
        }
        mOrientation = (orientation == ExifInterface.ORIENTATION_UNDEFINED)
                ? TAG_ORIENTATION_UNKNOWN : orientation;
        return this;
    }

    /** AOSP :186. The thumbnail, at most MAX_THUMBNAIL_DIMENSION on a side. */
    public DngCreator setThumbnail(Bitmap pixels) {
        if (pixels == null) {
            throw new IllegalArgumentException("Null argument to setThumbnail");
        }
        checkThumbnailSize(pixels.getWidth(), pixels.getHeight());
        // The RGB conversion AOSP performs here feeds the native writer, which
        // is not reachable (see the class header). Storing the Bitmap instead of
        // converting it keeps the observable behaviour -- the size check -- and
        // avoids a conversion whose only consumer does not exist.
        mThumbnailBitmap = pixels;
        return this;
    }

    /** AOSP :227. The YUV_420_888 form; any other format is rejected. */
    public DngCreator setThumbnail(Image pixels) {
        if (pixels == null) {
            throw new IllegalArgumentException("Null argument to setThumbnail");
        }
        int format = pixels.getFormat();
        if (format != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Unsupported Image format " + format);
        }
        checkThumbnailSize(pixels.getWidth(), pixels.getHeight());
        mThumbnailImage = pixels;
        return this;
    }

    private Bitmap mThumbnailBitmap;
    private Image mThumbnailImage;

    private static void checkThumbnailSize(int width, int height) {
        if (width > MAX_THUMBNAIL_DIMENSION || height > MAX_THUMBNAIL_DIMENSION) {
            throw new IllegalArgumentException("Thumbnail dimensions width,height (" + width
                    + "," + height + ") too large, dimensions must be smaller than "
                    + MAX_THUMBNAIL_DIMENSION);
        }
    }

    /**
     * AOSP :274. The GPS tags. AOSP converts latitude/longitude into the EXIF
     * rational triples and the timestamp into a GPSDateStamp/GPSTimeStamp pair;
     * the conversion's only consumer is the native writer, so the Location is
     * stored here and the observable check -- a null argument -- is kept.
     */
    public DngCreator setLocation(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Null location passed to setLocation");
        }
        mLocation = location;
        return this;
    }

    /**
     * AOSP :313. Writes the DNG for a RAW_SENSOR Image.
     *
     * The format check is AOSP's first statement and it is where every call on
     * this runtime ends: no path here produces a RAW_SENSOR Image.
     */
    public void writeImage(OutputStream dngOutput, Image pixels) throws IOException {
        checkNotClosed();
        if (dngOutput == null) {
            throw new IllegalArgumentException("Null dngOutput to writeImage");
        } else if (pixels == null) {
            throw new IllegalArgumentException("Null pixels to writeImage");
        }
        int format = pixels.getFormat();
        if (format != ImageFormat.RAW_SENSOR) {
            throw new IllegalArgumentException("Unsupported image format " + format);
        }
        Image.Plane[] planes = pixels.getPlanes();
        if (planes == null || planes.length <= 0) {
            throw new IllegalArgumentException("Image with no planes passed to writeImage");
        }
        ByteBuffer buf = planes[0].getBuffer();
        writeByteBuffer(pixels.getWidth(), pixels.getHeight(), buf, dngOutput,
                planes[0].getPixelStride(), planes[0].getRowStride(), 0);
    }

    /** AOSP :372. */
    public void writeByteBuffer(OutputStream dngOutput, Size size, ByteBuffer pixels, long offset)
            throws IOException {
        checkNotClosed();
        if (dngOutput == null) {
            throw new IllegalArgumentException("Null dngOutput passed to writeByteBuffer");
        } else if (size == null) {
            throw new IllegalArgumentException("Null size passed to writeByteBuffer");
        } else if (pixels == null) {
            throw new IllegalArgumentException("Null pixels passed to writeByteBuffer");
        } else if (offset < 0) {
            throw new IllegalArgumentException("Negative offset passed to writeByteBuffer");
        }
        int width = size.getWidth();
        int height = size.getHeight();
        writeByteBuffer(width, height, pixels, dngOutput, DEFAULT_PIXEL_STRIDE,
                width * DEFAULT_PIXEL_STRIDE, offset);
    }

    /** AOSP :428. */
    public void writeInputStream(OutputStream dngOutput, Size size, InputStream pixels, long offset)
            throws IOException {
        checkNotClosed();
        if (dngOutput == null) {
            throw new IllegalArgumentException("Null dngOutput passed to writeInputStream");
        } else if (size == null) {
            throw new IllegalArgumentException("Null size passed to writeInputStream");
        } else if (pixels == null) {
            throw new IllegalArgumentException("Null pixels passed to writeInputStream");
        } else if (offset < 0) {
            throw new IllegalArgumentException("Negative offset passed to writeInputStream");
        }
        int width = size.getWidth();
        int height = size.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Size with invalid width, height: (" + width + ","
                    + height + ") passed to writeInputStream");
        }
        requireDngMetadata();
        throw new UnsupportedOperationException(unreachableEncoderMessage());
    }

    // AOSP's private writeByteBuffer(int,int,ByteBuffer,OutputStream,int,int,long).
    private void writeByteBuffer(int width, int height, ByteBuffer pixels, OutputStream dngOutput,
            int pixelStride, int rowStride, long offset) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image with invalid width, height: (" + width + ","
                    + height + ") passed to write");
        }
        long capacity = pixels.capacity();
        long totalSize = ((long) rowStride) * height + offset;
        if (capacity < totalSize) {
            throw new IllegalArgumentException("Image size " + capacity
                    + " is too small (must be larger than " + totalSize + ")");
        }
        int minRowStride = pixelStride * width;
        if (minRowStride > rowStride) {
            throw new IllegalArgumentException("Invalid image pixel stride, row byte width "
                    + minRowStride + " is too large, expecting " + rowStride);
        }
        requireDngMetadata();
        throw new UnsupportedOperationException(unreachableEncoderMessage());
    }

    /**
     * The Java-side stand-in for AOSP's native NativeContext::setup /
     * validateDngHeader, which fail with IllegalArgumentException("Missing
     * metadata fields for tag ...") when a tag the DNG header needs is absent.
     *
     * These seven are the ones AOSP's writer reads unconditionally
     * (core/jni/android_hardware_camera2_DngCreator.cpp): without any one of
     * them there is no legal DNG to write, on this runtime or on a device.
     */
    private void requireDngMetadata() {
        requireTag(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE,
                "SENSOR_INFO_PIXEL_ARRAY_SIZE");
        requireTag(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL, "SENSOR_INFO_WHITE_LEVEL");
        requireTag(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT,
                "SENSOR_INFO_COLOR_FILTER_ARRANGEMENT");
        requireTag(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1, "SENSOR_COLOR_TRANSFORM1");
        requireTag(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1,
                "SENSOR_CALIBRATION_TRANSFORM1");
        requireTag(CameraCharacteristics.SENSOR_FORWARD_MATRIX1, "SENSOR_FORWARD_MATRIX1");
        requireTag(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1,
                "SENSOR_REFERENCE_ILLUMINANT1");
    }

    private void requireTag(CameraCharacteristics.Key<?> key, String name) {
        if (mCharacteristics.get(key) == null) {
            throw new IllegalArgumentException("Missing metadata fields for tag " + name
                    + " (" + key.getName() + ")");
        }
    }

    private String unreachableEncoderMessage() {
        return "DngCreator: this camera reports every DNG metadata tag, but the TIFF/EP encoder"
                + " is not implemented in Mac.apk. Refusing rather than writing a malformed .dng.";
    }

    private void checkNotClosed() {
        if (mClosed) {
            throw new IllegalStateException("Attempted to use a closed DngCreator");
        }
    }

    /** AOSP :453 frees the native context. There is none here; the flag makes
     *  a use-after-close fail the same way it does on a device. */
    @Override public void close() { mClosed = true; }

    /** The capture time, formatted for the DNG DateTime tag. @hide-equivalent:
     *  AOSP keeps it inside the native context. */
    String getFormattedCaptureTime() { return mFormattedCaptureTime; }

    /** @hide-equivalent accessors for the tags the encoder would consume. */
    String getDescription() { return mDescription; }
    int getOrientation() { return mOrientation; }
    Location getLocation() { return mLocation; }
    Bitmap getThumbnailBitmap() { return mThumbnailBitmap; }
    Image getThumbnailImage() { return mThumbnailImage; }
    CaptureResult getMetadata() { return mMetadata; }
}
