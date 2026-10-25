// android.view.animation.RotateAnimation -- rotates a view between two angles
// around a pivot. Was absent. Mirrors
// frameworks/base/core/java/android/view/animation/RotateAnimation.java.
package android.view.animation;

public class RotateAnimation extends Animation {
    // Not final: the (Context, AttributeSet) ctor must call super() first and
    // therefore cannot delegate. AOSP does not mark them final either.
    private float mFromDegrees;
    private float mToDegrees;

    private int mPivotXType = ABSOLUTE;
    private int mPivotYType = ABSOLUTE;
    private float mPivotXValue = 0.0f;
    private float mPivotYValue = 0.0f;
    private float mPivotX;
    private float mPivotY;

    public RotateAnimation(float fromDegrees, float toDegrees) {
        mFromDegrees = fromDegrees; mToDegrees = toDegrees;
        mPivotX = 0.0f; mPivotY = 0.0f;
    }

    public RotateAnimation(float fromDegrees, float toDegrees, float pivotX, float pivotY) {
        mFromDegrees = fromDegrees; mToDegrees = toDegrees;
        mPivotXType = ABSOLUTE; mPivotYType = ABSOLUTE;
        mPivotXValue = pivotX; mPivotYValue = pivotY;
        mPivotX = pivotX; mPivotY = pivotY;
    }

    public RotateAnimation(float fromDegrees, float toDegrees,
            int pivotXType, float pivotXValue, int pivotYType, float pivotYValue) {
        mFromDegrees = fromDegrees; mToDegrees = toDegrees;
        mPivotXValue = pivotXValue; mPivotXType = pivotXType;
        mPivotYValue = pivotYValue; mPivotYType = pivotYType;
        initializePivotPoint();
    }

    /** AOSP RotateAnimation(Context, AttributeSet) (android16-release
     *  core/java/android/view/animation/RotateAnimation.java): fromDegrees /
     *  toDegrees are plain floats, pivotX / pivotY are Descriptions (so
     *  `pivotX="50%"` means the view's own centre). */
    public RotateAnimation(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        mFromDegrees = AnimationUtils.animFloat(context, attrs, "fromDegrees", 0.0f);
        mToDegrees = AnimationUtils.animFloat(context, attrs, "toDegrees", 0.0f);

        Description d = describe(AnimationUtils.parseVal(context,
                AnimationUtils.rawAttr(attrs, "pivotX")));
        mPivotXType = d.type; mPivotXValue = d.value;

        d = describe(AnimationUtils.parseVal(context,
                AnimationUtils.rawAttr(attrs, "pivotY")));
        mPivotYType = d.type; mPivotYValue = d.value;

        initializePivotPoint();
    }

    /** AOSP RotateAnimation.initializePivotPoint(): an ABSOLUTE pivot is usable
     *  before initialize() runs, so publish it now; a relative one has to wait
     *  for the sizes. Without this an XML-inflated rotate with an absolute
     *  pivot spins about (0,0) on any frame drawn before initialize(). */
    private void initializePivotPoint() {
        if (mPivotXType == ABSOLUTE) mPivotX = mPivotXValue;
        if (mPivotYType == ABSOLUTE) mPivotY = mPivotYValue;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        float degrees = mFromDegrees + ((mToDegrees - mFromDegrees) * interpolatedTime);
        if (mPivotX == 0.0f && mPivotY == 0.0f) {
            t.getMatrix().setRotate(degrees);
        } else {
            t.getMatrix().setRotate(degrees, mPivotX, mPivotY);
        }
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        mPivotX = resolveSize(mPivotXType, mPivotXValue, width, parentWidth);
        mPivotY = resolveSize(mPivotYType, mPivotYValue, height, parentHeight);
    }
}
