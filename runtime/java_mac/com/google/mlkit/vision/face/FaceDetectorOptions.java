package com.google.mlkit.vision.face;

// com.google.mlkit.vision.face.FaceDetectorOptions -- detector config passed to
// FaceDetection.getClient(options). Mirrors the published Builder + mode/landmark/
// contour/classification constants exactly (apps pass these to setX). Values are
// inert because no detector runs.
// Ref: developers.google.com/android/reference/.../face/FaceDetectorOptions
public class FaceDetectorOptions {

    public static final int PERFORMANCE_MODE_FAST = 1;
    public static final int PERFORMANCE_MODE_ACCURATE = 2;

    public static final int LANDMARK_MODE_NONE = 1;
    public static final int LANDMARK_MODE_ALL = 2;

    public static final int CONTOUR_MODE_NONE = 1;
    public static final int CONTOUR_MODE_ALL = 2;

    public static final int CLASSIFICATION_MODE_NONE = 1;
    public static final int CLASSIFICATION_MODE_ALL = 2;

    private final int performanceMode;
    private final int landmarkMode;
    private final int contourMode;
    private final int classificationMode;
    private final float minFaceSize;
    private final boolean trackingEnabled;

    private FaceDetectorOptions(int performanceMode, int landmarkMode, int contourMode,
            int classificationMode, float minFaceSize, boolean trackingEnabled) {
        this.performanceMode = performanceMode;
        this.landmarkMode = landmarkMode;
        this.contourMode = contourMode;
        this.classificationMode = classificationMode;
        this.minFaceSize = minFaceSize;
        this.trackingEnabled = trackingEnabled;
    }

    public int getPerformanceMode() { return performanceMode; }
    public int getLandmarkMode() { return landmarkMode; }
    public int getContourMode() { return contourMode; }
    public int getClassificationMode() { return classificationMode; }
    public float getMinFaceSize() { return minFaceSize; }
    public boolean isTrackingEnabled() { return trackingEnabled; }

    public static class Builder {
        private int performanceMode = PERFORMANCE_MODE_FAST;
        private int landmarkMode = LANDMARK_MODE_NONE;
        private int contourMode = CONTOUR_MODE_NONE;
        private int classificationMode = CLASSIFICATION_MODE_NONE;
        private float minFaceSize = 0.1f;
        private boolean trackingEnabled = false;

        public Builder setPerformanceMode(int performanceMode) { this.performanceMode = performanceMode; return this; }
        public Builder setLandmarkMode(int landmarkMode) { this.landmarkMode = landmarkMode; return this; }
        public Builder setContourMode(int contourMode) { this.contourMode = contourMode; return this; }
        public Builder setClassificationMode(int classificationMode) { this.classificationMode = classificationMode; return this; }
        public Builder setMinFaceSize(float minFaceSize) { this.minFaceSize = minFaceSize; return this; }
        public Builder enableTracking() { this.trackingEnabled = true; return this; }
        public FaceDetectorOptions build() {
            return new FaceDetectorOptions(performanceMode, landmarkMode, contourMode,
                    classificationMode, minFaceSize, trackingEnabled);
        }
    }
}
