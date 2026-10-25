// android.gesture.OrientedBoundingBox -- the minimum-area oriented bounding box
// of a point set, produced by GestureUtils.computeOrientedBoundingBox and read
// by GestureOverlayView to decide whether a partial stroke is "a gesture" yet.
// Mirrors AOSP frameworks/base/core/java/android/gesture/OrientedBoundingBox.java
// (aosp-mirror android16-release, fetched 2026-08-24). All six fields are public
// final and part of the ABI.
package android.gesture;

import android.graphics.Matrix;
import android.graphics.Path;

public class OrientedBoundingBox {
    public final float squareness;

    public final float width;
    public final float height;

    public final float orientation;

    public final float centerX;
    public final float centerY;

    OrientedBoundingBox(float angle, float cx, float cy, float w, float h) {
        orientation = angle;
        width = w;
        height = h;
        centerX = cx;
        centerY = cy;
        float ratio = w / h;
        if (ratio > 1) {
            squareness = 1 / ratio;
        } else {
            squareness = ratio;
        }
    }

    /** AOSP @hide, "currently used for debugging purpose only". Kept because it
     *  is reachable by reflection and costs nothing. */
    public Path toPath() {
        Path path = new Path();
        float[] point = new float[2];
        point[0] = -width / 2;
        point[1] = height / 2;
        Matrix matrix = new Matrix();
        matrix.setRotate(orientation);
        matrix.postTranslate(centerX, centerY);
        matrix.mapPoints(point);
        path.moveTo(point[0], point[1]);

        point[0] = -width / 2;
        point[1] = -height / 2;
        matrix.mapPoints(point);
        path.lineTo(point[0], point[1]);

        point[0] = width / 2;
        point[1] = -height / 2;
        matrix.mapPoints(point);
        path.lineTo(point[0], point[1]);

        point[0] = width / 2;
        point[1] = height / 2;
        matrix.mapPoints(point);
        path.lineTo(point[0], point[1]);

        path.close();

        return path;
    }
}
