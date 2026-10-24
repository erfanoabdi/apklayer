// android.view.animation.ScaleAnimation -- scales a view between two scale
// factors around a pivot. Was absent. Mirrors
// frameworks/base/core/java/android/view/animation/ScaleAnimation.java.
package android.view.animation;

public class ScaleAnimation extends Animation {
    // Not final: initialize() resolves a percentage-valued scale in place
    // (AOSP does exactly this), and the (Context, AttributeSet) ctor cannot
    // delegate because it must call super() first.
    private float mFromX;
    private float mToX;
    private float mFromY;
    private float mToY;

    // AOSP keeps the raw TypedValue type+data for a scale attribute that was
    // NOT a plain float, and resolves it in initialize() (ScaleAnimation.java:
    // mFromXType/mFromXData ... resolveScale). Our pipeline gives text, so we
    // keep AnimationUtils.Val's kind instead of a TypedValue type; Val.FLOAT
    // means "already a scale factor, leave it alone", which is the same
    // decision AOSP's `else return scale;` branch makes.
    private int mFromXKind = AnimationUtils.Val.FLOAT;
    private int mToXKind   = AnimationUtils.Val.FLOAT;
    private int mFromYKind = AnimationUtils.Val.FLOAT;
    private int mToYKind   = AnimationUtils.Val.FLOAT;

    private int mPivotXType = ABSOLUTE;
    private int mPivotYType = ABSOLUTE;
    private float mPivotXValue = 0.0f;
    private float mPivotYValue = 0.0f;
    private float mPivotX;
    private float mPivotY;

    public ScaleAnimation(float fromX, float toX, float fromY, float toY) {
        mFromX = fromX; mToX = toX; mFromY = fromY; mToY = toY;
        mPivotX = 0; mPivotY = 0;
    }

    public ScaleAnimation(float fromX, float toX, float fromY, float toY,
            float pivotX, float pivotY) {
        mFromX = fromX; mToX = toX; mFromY = fromY; mToY = toY;
        mPivotXType = ABSOLUTE; mPivotYType = ABSOLUTE;
        mPivotXValue = pivotX; mPivotYValue = pivotY;
        mPivotX = pivotX; mPivotY = pivotY;
    }

    public ScaleAnimation(float fromX, float toX, float fromY, float toY,
            int pivotXType, float pivotXValue, int pivotYType, float pivotYValue) {
        mFromX = fromX; mToX = toX; mFromY = fromY; mToY = toY;
        mPivotXValue = pivotXValue; mPivotXType = pivotXType;
        mPivotYValue = pivotYValue; mPivotYType = pivotYType;
        initializePivotPoint();
    }

    /** AOSP ScaleAnimation(Context, AttributeSet) (android16-release
     *  core/java/android/view/animation/ScaleAnimation.java): each of the four
     *  scale attributes is read with peekValue -- a TYPE_FLOAT is taken as a
     *  scale factor verbatim, anything else is stashed and resolved later by
     *  resolveScale. pivotX / pivotY are Descriptions.
     *
     *  AOSP's defaults for the scale factors are 0.0f, NOT 1.0f: an omitted
     *  fromXScale scales to nothing, which is what a "grow from a point"
     *  animation declaring only toXScale wants. */
    public ScaleAnimation(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        AnimationUtils.Val v = AnimationUtils.parseVal(context,
                AnimationUtils.rawAttr(attrs, "fromXScale"));
        mFromX = (v.kind == AnimationUtils.Val.NONE) ? 0.0f : v.value;
        mFromXKind = v.kind;

        v = AnimationUtils.parseVal(context, AnimationUtils.rawAttr(attrs, "toXScale"));
        mToX = (v.kind == AnimationUtils.Val.NONE) ? 0.0f : v.value;
        mToXKind = v.kind;

        v = AnimationUtils.parseVal(context, AnimationUtils.rawAttr(attrs, "fromYScale"));
        mFromY = (v.kind == AnimationUtils.Val.NONE) ? 0.0f : v.value;
        mFromYKind = v.kind;

        v = AnimationUtils.parseVal(context, AnimationUtils.rawAttr(attrs, "toYScale"));
        mToY = (v.kind == AnimationUtils.Val.NONE) ? 0.0f : v.value;
        mToYKind = v.kind;

        Description d = describe(AnimationUtils.parseVal(context,
                AnimationUtils.rawAttr(attrs, "pivotX")));
        mPivotXType = d.type; mPivotXValue = d.value;

        d = describe(AnimationUtils.parseVal(context,
                AnimationUtils.rawAttr(attrs, "pivotY")));
        mPivotYType = d.type; mPivotYValue = d.value;

        initializePivotPoint();
    }

    /** AOSP ScaleAnimation.initializePivotPoint(). */
    private void initializePivotPoint() {
        if (mPivotXType == ABSOLUTE) mPivotX = mPivotXValue;
        if (mPivotYType == ABSOLUTE) mPivotY = mPivotYValue;
    }

    /** AOSP ScaleAnimation.resolveScale (android16-release
     *  ScaleAnimation.java:260-275):
     *
     *      if (type == TYPE_FRACTION)  targetSize = complexToFraction(data, size, psize);
     *      else if (type == TYPE_DIMENSION) targetSize = complexToDimension(data, metrics);
     *      else return scale;
     *      if (size == 0) return 1;
     *      return targetSize / (float) size;
     *
     *  i.e. a scale written as a SIZE ("50%", "48dp") means "scale so the view
     *  ends up that big", not "multiply by that number". complexToFraction(d,
     *  size, psize) is d*size for a self fraction and d*psize for a parent one,
     *  which is exactly what our SELF/PARENT kinds carry. */
    private float resolveScale(float scale, int kind, int size, int psize) {
        float targetSize;
        if (kind == AnimationUtils.Val.SELF) {
            targetSize = scale * size;
        } else if (kind == AnimationUtils.Val.PARENT) {
            targetSize = scale * psize;
        } else if (kind == AnimationUtils.Val.DIMEN) {
            targetSize = scale; // parseVal already resolved the dimension to px
        } else {
            return scale;
        }
        if (size == 0) return 1;
        return targetSize / (float) size;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        float sx = 1.0f;
        float sy = 1.0f;
        if (mFromX != 1.0f || mToX != 1.0f) {
            sx = mFromX + ((mToX - mFromX) * interpolatedTime);
        } else {
            sx = mFromX;
        }
        if (mFromY != 1.0f || mToY != 1.0f) {
            sy = mFromY + ((mToY - mFromY) * interpolatedTime);
        } else {
            sy = mFromY;
        }
        // AOSP applies the animation's scale factor to the PIVOT here:
        //     t.getMatrix().setScale(sx, sy, scale * mPivotX, scale * mPivotY);
        // scale is 1 unless getTransformation(long, Transformation, float) was
        // driven with something else, so this is a no-op on the common path.
        final float scale = getScaleFactor();
        if (mPivotX == 0 && mPivotY == 0) {
            t.getMatrix().setScale(sx, sy);
        } else {
            t.getMatrix().setScale(sx, sy, scale * mPivotX, scale * mPivotY);
        }
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        // AOSP resolves the scale factors here too, and does so IN PLACE (the
        // second call is a no-op because resolveScale's `else return scale`
        // branch is reached once the kind is FLOAT). We keep the kind, so a
        // re-initialize would re-resolve; reset it to FLOAT after resolving to
        // reproduce AOSP's idempotence exactly.
        mFromX = resolveScale(mFromX, mFromXKind, width, parentWidth);
        mFromXKind = AnimationUtils.Val.FLOAT;
        mToX = resolveScale(mToX, mToXKind, width, parentWidth);
        mToXKind = AnimationUtils.Val.FLOAT;
        mFromY = resolveScale(mFromY, mFromYKind, height, parentHeight);
        mFromYKind = AnimationUtils.Val.FLOAT;
        mToY = resolveScale(mToY, mToYKind, height, parentHeight);
        mToYKind = AnimationUtils.Val.FLOAT;

        mPivotX = resolveSize(mPivotXType, mPivotXValue, width, parentWidth);
        mPivotY = resolveSize(mPivotYType, mPivotYValue, height, parentHeight);
    }
}
