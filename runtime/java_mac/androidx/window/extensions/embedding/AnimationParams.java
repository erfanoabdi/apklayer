package androidx.window.extensions.embedding;

public final class AnimationParams {
    private final AnimationBackground mBackground;
    private final int mOpen, mClose, mChange;
    private AnimationParams(Builder b) {
        mBackground = b.mBackground; mOpen = b.mOpen; mClose = b.mClose; mChange = b.mChange;
    }
    public AnimationBackground getAnimationBackground() { return mBackground; }
    public int getOpenAnimationResId() { return mOpen; }
    public int getCloseAnimationResId() { return mClose; }
    public int getChangeAnimationResId() { return mChange; }
    public static final class Builder {
        private AnimationBackground mBackground = AnimationBackground.ANIMATION_BACKGROUND_DEFAULT;
        private int mOpen, mClose, mChange;
        public Builder() {}
        public Builder setAnimationBackground(AnimationBackground b) { mBackground = b; return this; }
        public Builder setOpenAnimationResId(int r) { mOpen = r; return this; }
        public Builder setCloseAnimationResId(int r) { mClose = r; return this; }
        public Builder setChangeAnimationResId(int r) { mChange = r; return this; }
        public AnimationParams build() { return new AnimationParams(this); }
    }
}
