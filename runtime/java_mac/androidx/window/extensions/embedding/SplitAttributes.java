package androidx.window.extensions.embedding;

public final class SplitAttributes {
    public static final int LAYOUT_DIRECTION_LOCALE = 0;
    public static final int LAYOUT_DIRECTION_LEFT_TO_RIGHT = 1;
    public static final int LAYOUT_DIRECTION_RIGHT_TO_LEFT = 2;
    public static final int LAYOUT_DIRECTION_TOP_TO_BOTTOM = 3;
    public static final int LAYOUT_DIRECTION_BOTTOM_TO_TOP = 4;
    /** The way a split divides its container. */
    public abstract static class SplitType {
        private final float mValue;
        SplitType(float value) { mValue = value; }
        public float getValue() { return mValue; }
        public static final class RatioSplitType extends SplitType {
            private final float mRatio;
            public RatioSplitType(float ratio) { super(ratio); mRatio = ratio; }
            public float getRatio() { return mRatio; }
        }
        public static final class ExpandContainersSplitType extends SplitType {
            public ExpandContainersSplitType() { super(0.0f); }
        }
        public static final class HingeSplitType extends SplitType {
            private final SplitType mFallback;
            public HingeSplitType(SplitType fallbackSplitType) { super(-1.0f); mFallback = fallbackSplitType; }
            public SplitType getFallbackSplitType() { return mFallback; }
        }
    }
    private final SplitType mSplitType;
    private final int mLayoutDirection;
    private final AnimationBackground mAnimationBackground;
    private final AnimationParams mAnimationParams;
    private final DividerAttributes mDividerAttributes;
    private final WindowAttributes mWindowAttributes;
    private SplitAttributes(Builder b) {
        mSplitType = b.mSplitType; mLayoutDirection = b.mLayoutDirection;
        mAnimationBackground = b.mAnimationBackground; mAnimationParams = b.mAnimationParams;
        mDividerAttributes = b.mDividerAttributes; mWindowAttributes = b.mWindowAttributes;
    }
    public SplitType getSplitType() { return mSplitType; }
    public int getLayoutDirection() { return mLayoutDirection; }
    public AnimationBackground getAnimationBackground() { return mAnimationBackground; }
    public AnimationParams getAnimationParams() { return mAnimationParams; }
    public DividerAttributes getDividerAttributes() { return mDividerAttributes; }
    public WindowAttributes getWindowAttributes() { return mWindowAttributes; }
    public static final class Builder {
        private SplitType mSplitType = new SplitType.RatioSplitType(0.5f);
        private int mLayoutDirection = LAYOUT_DIRECTION_LOCALE;
        private AnimationBackground mAnimationBackground = AnimationBackground.ANIMATION_BACKGROUND_DEFAULT;
        private AnimationParams mAnimationParams;
        private DividerAttributes mDividerAttributes;
        private WindowAttributes mWindowAttributes = new WindowAttributes(WindowAttributes.DIM_AREA_ON_TASK);
        public Builder() {}
        public Builder setSplitType(SplitType splitType) { mSplitType = splitType; return this; }
        public Builder setLayoutDirection(int layoutDirection) { mLayoutDirection = layoutDirection; return this; }
        public Builder setAnimationBackground(AnimationBackground b) { mAnimationBackground = b; return this; }
        public Builder setAnimationParams(AnimationParams p) { mAnimationParams = p; return this; }
        public Builder setDividerAttributes(DividerAttributes d) { mDividerAttributes = d; return this; }
        public Builder setWindowAttributes(WindowAttributes wa) { mWindowAttributes = wa; return this; }
        public SplitAttributes build() { return new SplitAttributes(this); }
    }
}
