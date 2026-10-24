package com.google.mlkit.vision.face;

import android.graphics.Rect;
import java.util.Collections;
import java.util.List;

// com.google.mlkit.vision.face.Face -- a single detected face. Mac.apk runs no
// face model, so a detect Task fails and apps never receive a Face via the
// success path; the type exists so success-listener generics and field reads
// link. Mirrors the published accessors (bounding box, tracking id, euler
// angles, smile/eye-open probabilities, landmarks, contours).
// Ref: developers.google.com/android/reference/.../face/Face
public class Face {

    private final Rect boundingBox;
    private final int trackingId;
    private final float eulerX, eulerY, eulerZ;
    private final Float smileProb, leftEyeOpenProb, rightEyeOpenProb;

    public Face() { this(null, -1, 0f, 0f, 0f, null, null, null); }
    public Face(Rect boundingBox, int trackingId, float eulerX, float eulerY, float eulerZ,
            Float smileProb, Float leftEyeOpenProb, Float rightEyeOpenProb) {
        this.boundingBox = boundingBox;
        this.trackingId = trackingId;
        this.eulerX = eulerX; this.eulerY = eulerY; this.eulerZ = eulerZ;
        this.smileProb = smileProb;
        this.leftEyeOpenProb = leftEyeOpenProb;
        this.rightEyeOpenProb = rightEyeOpenProb;
    }

    public Rect getBoundingBox() { return boundingBox; }
    public int getTrackingId() { return trackingId; }
    public float getHeadEulerAngleX() { return eulerX; }
    public float getHeadEulerAngleY() { return eulerY; }
    public float getHeadEulerAngleZ() { return eulerZ; }
    public Float getSmilingProbability() { return smileProb; }
    public Float getLeftEyeOpenProbability() { return leftEyeOpenProb; }
    public Float getRightEyeOpenProbability() { return rightEyeOpenProb; }

    public List<FaceLandmark> getAllLandmarks() { return Collections.emptyList(); }
    public FaceLandmark getLandmark(int landmarkType) { return null; }
    public List<FaceContour> getAllContours() { return Collections.emptyList(); }
    public FaceContour getContour(int contourType) { return null; }

    // Minimal landmark/contour holders so getLandmark/getContour return types link.
    public static class FaceLandmark {
        private final int type;
        public FaceLandmark(int type) { this.type = type; }
        public int getLandmarkType() { return type; }
        public android.graphics.PointF getPosition() { return null; }
    }
    public static class FaceContour {
        private final int type;
        public FaceContour(int type) { this.type = type; }
        public int getFaceContourType() { return type; }
        public List<android.graphics.PointF> getPoints() { return Collections.emptyList(); }
    }
}
