// android.view.animation.TranslateAnimation -- moves a view from one position to
// another. Was absent. Resolves ABSOLUTE / RELATIVE_TO_SELF / RELATIVE_TO_PARENT
// from/to deltas at initialize() and applies a translate matrix each frame.
// Mirrors frameworks/base/core/java/android/view/animation/TranslateAnimation.java.
package android.view.animation;

public class TranslateAnimation extends Animation {
    private int mFromXType = ABSOLUTE;
    private int mToXType = ABSOLUTE;
    private int mFromYType = ABSOLUTE;
    private int mToYType = ABSOLUTE;

    private float mFromXValue;
    private float mToXValue;
    private float mFromYValue;
    private float mToYValue;

    // Resolved absolute pixel deltas (filled in initialize()).
    private float mFromXDelta;
    private float mToXDelta;
    private float mFromYDelta;
    private float mToYDelta;

    public TranslateAnimation(float fromXDelta, float toXDelta, float fromYDelta, float toYDelta) {
        mFromXType = ABSOLUTE; mToXType = ABSOLUTE;
        mFromYType = ABSOLUTE; mToYType = ABSOLUTE;
        mFromXValue = fromXDelta; mToXValue = toXDelta;
        mFromYValue = fromYDelta; mToYValue = toYDelta;
        // ABSOLUTE deltas are usable immediately even before initialize().
        mFromXDelta = fromXDelta; mToXDelta = toXDelta;
        mFromYDelta = fromYDelta; mToYDelta = toYDelta;
    }

    public TranslateAnimation(int fromXType, float fromXValue, int toXType, float toXValue,
            int fromYType, float fromYValue, int toYType, float toYValue) {
        mFromXValue = fromXValue; mToXValue = toXValue;
        mFromYValue = fromYValue; mToYValue = toYValue;
        mFromXType = fromXType; mToXType = toXType;
        mFromYType = fromYType; mToYType = toYType;
    }

    /** AOSP TranslateAnimation(Context, AttributeSet) (android16-release
     *  core/java/android/view/animation/TranslateAnimation.java): each of
     *  fromXDelta / toXDelta / fromYDelta / toYDelta is a Description, i.e. an
     *  absolute pixel offset OR a "%" fraction of the view OR a "%p" fraction
     *  of the parent. The percentage forms are the whole reason Description
     *  exists -- a slide-in-from-the-right animation is written
     *  `fromXDelta="100%p"`, and reading that as the number 100 would move the
     *  view 100px instead of a full parent width.
     *
     *  Note the deltas stay UNRESOLVED here: resolveSize runs in initialize(),
     *  which is where the view and parent sizes are finally known. */
    public TranslateAnimation(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        Description d = describe(AnimationUtils.parseVal(context,
                AnimationUtils.rawAttr(attrs, "fromXDelta")));
        mFromXType = d.type; mFromXValue = d.value;

        d = describe(AnimationUtils.parseVal(context,
                AnimationUtils.rawAttr(attrs, "toXDelta")));
        mToXType = d.type; mToXValue = d.value;

        d = describe(AnimationUtils.parseVal(context,
                AnimationUtils.rawAttr(attrs, "fromYDelta")));
        mFromYType = d.type; mFromYValue = d.value;

        d = describe(AnimationUtils.parseVal(context,
                AnimationUtils.rawAttr(attrs, "toYDelta")));
        mToYType = d.type; mToYValue = d.value;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        float dx = mFromXDelta;
        float dy = mFromYDelta;
        if (mFromXDelta != mToXDelta) {
            dx = mFromXDelta + ((mToXDelta - mFromXDelta) * interpolatedTime);
        }
        if (mFromYDelta != mToYDelta) {
            dy = mFromYDelta + ((mToYDelta - mFromYDelta) * interpolatedTime);
        }
        t.getMatrix().setTranslate(dx, dy);
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        mFromXDelta = resolveSize(mFromXType, mFromXValue, width, parentWidth);
        mToXDelta = resolveSize(mToXType, mToXValue, width, parentWidth);
        mFromYDelta = resolveSize(mFromYType, mFromYValue, height, parentHeight);
        mToYDelta = resolveSize(mToYType, mToYValue, height, parentHeight);
    }
}
