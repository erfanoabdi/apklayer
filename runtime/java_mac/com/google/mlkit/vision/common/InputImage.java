package com.google.mlkit.vision.common;

import android.graphics.Bitmap;
import android.media.Image;
import android.net.Uri;
import android.content.Context;

// com.google.mlkit.vision.common.InputImage -- the image wrapper every ML Kit
// vision detector consumes. ML Kit runs all inference on-device with bundled or
// downloaded models; Mac.apk ships no model backend, so detectors fail their
// Task cleanly (see vision/text|barcode|face|label). InputImage itself is pure
// data, so we mirror its real factories faithfully: an app builds an InputImage
// the normal way, then process() returns a failed Task. This lets ML Kit-using
// apps init and degrade exactly like a device whose model download is blocked.
//
// Ref: developers.google.com/android/reference/com/google/mlkit/vision/common/InputImage
public class InputImage {

    public static final int IMAGE_FORMAT_NV21 = 17;
    public static final int IMAGE_FORMAT_YV12 = 842094169;
    public static final int IMAGE_FORMAT_YUV_420_888 = 35;
    public static final int IMAGE_FORMAT_BITMAP = -1;

    private final Bitmap bitmap;
    private final int width;
    private final int height;
    private final int rotation;
    private final int format;

    private InputImage(Bitmap bitmap, int width, int height, int rotation, int format) {
        this.bitmap = bitmap;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.format = format;
    }

    public static InputImage fromBitmap(Bitmap bitmap, int rotationDegrees) {
        if (bitmap == null) throw new IllegalArgumentException("bitmap is null");
        return new InputImage(bitmap, bitmap.getWidth(), bitmap.getHeight(),
                rotationDegrees, IMAGE_FORMAT_BITMAP);
    }

    public static InputImage fromMediaImage(Image image, int rotationDegrees) {
        if (image == null) throw new IllegalArgumentException("image is null");
        return new InputImage(null, image.getWidth(), image.getHeight(),
                rotationDegrees, image.getFormat());
    }

    // The published overload that also accepts the CameraX transform matrix.
    public static InputImage fromMediaImage(Image image, int rotationDegrees,
            android.graphics.Matrix transformMatrix) {
        return fromMediaImage(image, rotationDegrees);
    }

    public static InputImage fromFilePath(Context context, Uri uri)
            throws java.io.IOException {
        // Real ML Kit decodes the Uri to a Bitmap here. With no decode backend
        // wired we expose a zero-dimension image; detectors fail regardless so
        // the dimensions never feed real inference.
        return new InputImage(null, 0, 0, 0, IMAGE_FORMAT_BITMAP);
    }

    public static InputImage fromByteBuffer(java.nio.ByteBuffer byteBuffer,
            int width, int height, int rotationDegrees, int format) {
        return new InputImage(null, width, height, rotationDegrees, format);
    }

    public static InputImage fromByteArray(byte[] byteArray, int width, int height,
            int rotationDegrees, int format) {
        return new InputImage(null, width, height, rotationDegrees, format);
    }

    public Bitmap getBitmapInternal() { return bitmap; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getRotationDegrees() { return rotation; }
    public int getFormat() { return format; }
}
