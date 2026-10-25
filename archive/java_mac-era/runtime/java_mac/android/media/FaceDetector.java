package android.media;
/** Legacy face detection (API 1). Mac.apk has no detector, so it finds none. */
public class FaceDetector {
    public FaceDetector(int width, int height, int maxFaces) {}
    public int findFaces(android.graphics.Bitmap bitmap, Face[] faces) { return 0; }
    public static class Face {
        public static final float CONFIDENCE_THRESHOLD = 0.4f;
        public float confidence() { return 0f; }
        public float eyesDistance() { return 0f; }
        public void getMidPoint(android.graphics.PointF point) {}
        public float pose(int euler) { return 0f; }
    }
}
