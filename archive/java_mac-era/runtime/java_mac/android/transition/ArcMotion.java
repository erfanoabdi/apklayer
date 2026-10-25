// android.transition.ArcMotion -- a PathMotion that moves a view along a curved
// (arced) path between two points rather than a straight line. Was absent. We
// build a quadratic Bezier whose control point is offset perpendicular to the
// chord, which produces a faithful arc for the common case. Mirrors the public
// surface of frameworks/base/core/java/android/transition/ArcMotion.java (the
// exact min-angle threshold geometry AOSP uses is simplified to a single arced
// Bezier).
package android.transition;

import android.util.AttributeSet;
import android.content.Context;
import android.graphics.Path;

public class ArcMotion extends PathMotion {

    private float mMinimumHorizontalAngle = 0;
    private float mMinimumVerticalAngle = 0;
    private float mMaximumAngle = 70;

    public ArcMotion() {}

    /**
     * AOSP ArcMotion.java:63-76 -- the XML-inflation constructor. Reads
     * android:minimumVerticalAngle / android:minimumHorizontalAngle
     * (DEFAULT_MIN_ANGLE_DEGREES = 0) and android:maximumAngle
     * (DEFAULT_MAX_ANGLE_DEGREES = 70), and pushes each through the SETTER so
     * any precomputation the setters do still happens.
     */
    public ArcMotion(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            setMinimumVerticalAngle(attrs.getAttributeFloatValue(
                    Transition.ANDROID_NS, "minimumVerticalAngle", 0f));
            setMinimumHorizontalAngle(attrs.getAttributeFloatValue(
                    Transition.ANDROID_NS, "minimumHorizontalAngle", 0f));
            setMaximumAngle(attrs.getAttributeFloatValue(
                    Transition.ANDROID_NS, "maximumAngle", 70f));
        }
    }

    public void setMinimumHorizontalAngle(float angleInDegrees) { mMinimumHorizontalAngle = angleInDegrees; }
    public float getMinimumHorizontalAngle() { return mMinimumHorizontalAngle; }
    public void setMinimumVerticalAngle(float angleInDegrees) { mMinimumVerticalAngle = angleInDegrees; }
    public float getMinimumVerticalAngle() { return mMinimumVerticalAngle; }
    public void setMaximumAngle(float angleInDegrees) { mMaximumAngle = angleInDegrees; }
    public float getMaximumAngle() { return mMaximumAngle; }

    @Override
    public Path getPath(float startX, float startY, float endX, float endY) {
        Path path = new Path();
        path.moveTo(startX, startY);
        // Midpoint of the chord, pushed perpendicular by a fraction of the chord
        // length to bow the path into an arc.
        float midX = (startX + endX) / 2f;
        float midY = (startY + endY) / 2f;
        float dx = endX - startX;
        float dy = endY - startY;
        float len = (float) Math.hypot(dx, dy);
        float bow = len * 0.25f;
        // Perpendicular unit vector (-dy, dx)/len.
        float px = len == 0 ? 0 : -dy / len;
        float py = len == 0 ? 0 : dx / len;
        float ctrlX = midX + px * bow;
        float ctrlY = midY + py * bow;
        path.quadTo(ctrlX, ctrlY, endX, endY);
        return path;
    }
}
