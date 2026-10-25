// android.transition.PathMotion -- abstracts how a transition moves a view
// between two points (straight line by default; ArcMotion curves it). Transition
// holds one and exposes set/getPathMotion. Was absent; referenced by Transition's
// signature so it must exist on the bootclasspath.
// Mirrors frameworks/base/core/java/android/transition/PathMotion.java.
package android.transition;

import android.util.AttributeSet;
import android.content.Context;
import android.graphics.Path;

public abstract class PathMotion {

    // A shared straight-line default so Transition can initialize its field
    // without each subclass constructing one. (AOSP keeps the default inline; the
    // observable behavior -- a straight Path between the points -- is identical.)
    static final PathMotion STRAIGHT_PATH_MOTION = new PathMotion() {
        @Override public Path getPath(float startX, float startY, float endX, float endY) {
            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);
            return path;
        }
    };

    public PathMotion() {}

    /**
     * AOSP PathMotion.java (android16-release) -- the XML-inflation
     * constructor. PathMotion itself declares no attributes; it exists so a
     * subclass inflated from XML can chain up.
     */
    public PathMotion(Context context, AttributeSet attrs) {}

    // Return a Path describing how to move from (startX,startY) to (endX,endY).
    public abstract Path getPath(float startX, float startY, float endX, float endY);
}
