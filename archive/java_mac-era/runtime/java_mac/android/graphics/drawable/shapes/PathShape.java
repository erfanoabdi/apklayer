// android.graphics.drawable.shapes.PathShape -- AOSP Shape that draws a
// user-supplied Path scaled from a standard (stdWidth x stdHeight)
// coordinate space into the shape's current bounds. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/shapes/PathShape.java
package android.graphics.drawable.shapes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

public class PathShape extends Shape {
    private Path mPath;
    private final float mStdWidth;
    private final float mStdHeight;
    private float mScaleX = 1f;
    private float mScaleY = 1f;

    public PathShape(Path path, float stdWidth, float stdHeight) {
        mPath = path;
        mStdWidth = stdWidth;
        mStdHeight = stdHeight;
    }

    @Override public void draw(Canvas canvas, Paint paint) {
        if (mPath == null) return;
        final int saved = canvas.save();
        canvas.scale(mScaleX, mScaleY);
        canvas.drawPath(mPath, paint);
        canvas.restoreToCount(saved);
    }

    @Override protected void onResize(float width, float height) {
        mScaleX = mStdWidth > 0 ? width / mStdWidth : 1f;
        mScaleY = mStdHeight > 0 ? height / mStdHeight : 1f;
    }

    @Override public PathShape clone() throws CloneNotSupportedException {
        PathShape s = (PathShape) super.clone();
        s.mPath = mPath != null ? new Path(mPath) : null;
        return s;
    }
}
