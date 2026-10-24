package com.google.mlkit.vision.face;

// com.google.mlkit.vision.face.FaceDetection -- entry-point factory for a
// FaceDetector. Both overloads return our Default detector whose process() fails
// cleanly. Mirrors the published static factory.
// Ref: developers.google.com/android/reference/.../face/FaceDetection
public final class FaceDetection {
    private FaceDetection() {}

    public static FaceDetector getClient() {
        return new FaceDetector.Default();
    }

    public static FaceDetector getClient(FaceDetectorOptions options) {
        return new FaceDetector.Default();
    }
}
