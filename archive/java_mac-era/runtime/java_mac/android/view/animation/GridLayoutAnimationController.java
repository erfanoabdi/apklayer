// android.view.animation.GridLayoutAnimationController -- the GridView flavour
// of LayoutAnimationController: the per-child delay comes from the child's ROW
// and COLUMN rather than a flat index.
// Source: frameworks/base core/java/android/view/animation/GridLayoutAnimationController.java
// (android16-release). getDelayForView is AOSP's own arithmetic, verbatim, so a
// caller driving the controller itself gets a device-identical start offset.
package android.view.animation;

import android.view.View;
import android.view.ViewGroup;

public class GridLayoutAnimationController extends LayoutAnimationController {
    public static final int DIRECTION_LEFT_TO_RIGHT = 0x0;
    public static final int DIRECTION_RIGHT_TO_LEFT = 0x1;
    public static final int DIRECTION_TOP_TO_BOTTOM = 0x0;
    public static final int DIRECTION_BOTTOM_TO_TOP = 0x2;
    public static final int DIRECTION_HORIZONTAL_MASK = 0x1;
    public static final int DIRECTION_VERTICAL_MASK = 0x2;

    public static final int PRIORITY_NONE = 0;
    public static final int PRIORITY_COLUMN = 1;
    public static final int PRIORITY_ROW = 2;

    private float mColumnDelay;
    private float mRowDelay;
    private int mDirection;
    private int mDirectionPriority;

    public GridLayoutAnimationController(android.content.Context context,
            android.util.AttributeSet attrs) {
        super(context, attrs);
    }
    public GridLayoutAnimationController(Animation animation) {
        this(animation, 0.5f, 0.5f);
    }
    public GridLayoutAnimationController(Animation animation, float columnDelay, float rowDelay) {
        super(animation);
        mColumnDelay = columnDelay;
        mRowDelay = rowDelay;
    }

    public float getColumnDelay() { return mColumnDelay; }
    public void setColumnDelay(float columnDelay) { mColumnDelay = columnDelay; }
    public float getRowDelay() { return mRowDelay; }
    public void setRowDelay(float rowDelay) { mRowDelay = rowDelay; }
    public int getDirection() { return mDirection; }
    public void setDirection(int direction) { mDirection = direction; }
    public int getDirectionPriority() { return mDirectionPriority; }
    public void setDirectionPriority(int directionPriority) {
        mDirectionPriority = directionPriority;
    }

    /** AOSP GridLayoutAnimationController.willOverlap(): overlapping if either
     *  axis delay is below 1. */
    @Override
    public boolean willOverlap() { return mColumnDelay < 1.0f || mRowDelay < 1.0f; }

    /** AOSP GridLayoutAnimationController.getDelayForView(View), verbatim. */
    @Override
    protected long getDelayForView(View view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null || getAnimation() == null) return 0;
        LayoutAnimationController.AnimationParameters base = lp.layoutAnimationParameters;
        if (!(base instanceof AnimationParameters)) return 0;
        AnimationParameters params = (AnimationParameters) base;

        final int rowsCount = params.rowsCount;
        final int columnsCount = params.columnsCount;

        final long duration = getAnimation().getDuration();
        final float columnDelay = mColumnDelay * duration;
        final float rowDelay = mRowDelay * duration;

        int column = getTransformedColumnIndex(params);
        int row = getTransformedRowIndex(params);

        float viewDelay;
        float totalDelay;
        switch (mDirectionPriority) {
            case PRIORITY_COLUMN:
                viewDelay = column * rowsCount * rowDelay + row * rowDelay;
                totalDelay = rowsCount * columnsCount * rowDelay;
                break;
            case PRIORITY_ROW:
                viewDelay = row * columnsCount * columnDelay + column * columnDelay;
                totalDelay = rowsCount * columnsCount * columnDelay;
                break;
            case PRIORITY_NONE:
            default:
                viewDelay = column * columnDelay + row * rowDelay;
                totalDelay = Math.max(columnsCount * columnDelay, rowsCount * rowDelay);
                break;
        }

        Interpolator interpolator = getInterpolator();
        if (interpolator == null) {
            interpolator = new LinearInterpolator();
            setInterpolator(interpolator);
        }
        float normalizedDelay = totalDelay == 0f ? 0f : viewDelay / totalDelay;
        normalizedDelay = interpolator.getInterpolation(normalizedDelay);
        return (long) (normalizedDelay * totalDelay);
    }

    private int getTransformedColumnIndex(AnimationParameters params) {
        int index;
        switch (getOrder()) {
            case ORDER_REVERSE: index = params.columnsCount - 1 - params.column; break;
            case ORDER_RANDOM:
                if (mRandomizer == null) mRandomizer = new java.util.Random();
                index = (int) (params.columnsCount * mRandomizer.nextFloat());
                break;
            case ORDER_NORMAL:
            default: index = params.column; break;
        }
        int direction = mDirection & DIRECTION_HORIZONTAL_MASK;
        if (direction == DIRECTION_RIGHT_TO_LEFT) {
            index = params.columnsCount - 1 - index;
        }
        return index;
    }

    private int getTransformedRowIndex(AnimationParameters params) {
        int index;
        switch (getOrder()) {
            case ORDER_REVERSE: index = params.rowsCount - 1 - params.row; break;
            case ORDER_RANDOM:
                if (mRandomizer == null) mRandomizer = new java.util.Random();
                index = (int) (params.rowsCount * mRandomizer.nextFloat());
                break;
            case ORDER_NORMAL:
            default: index = params.row; break;
        }
        int direction = mDirection & DIRECTION_VERTICAL_MASK;
        if (direction == DIRECTION_BOTTOM_TO_TOP) {
            index = params.rowsCount - 1 - index;
        }
        return index;
    }

    /** AOSP GridLayoutAnimationController.AnimationParameters: the grid
     *  position a GridView attaches to each child's LayoutParams. */
    public static class AnimationParameters
            extends LayoutAnimationController.AnimationParameters {
        public AnimationParameters() {}
        public int column;
        public int row;
        public int columnsCount;
        public int rowsCount;
    }
}
