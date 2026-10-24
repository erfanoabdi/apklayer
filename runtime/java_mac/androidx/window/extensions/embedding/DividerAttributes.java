package androidx.window.extensions.embedding;

public final class DividerAttributes {
    public static final int DIVIDER_TYPE_FIXED = 1;
    public static final int DIVIDER_TYPE_DRAGGABLE = 2;
    public static final int WIDTH_SYSTEM_DEFAULT = -1;
    public static final float RATIO_SYSTEM_DEFAULT = -1.0f;
    private final int mType, mWidthDp, mColor;
    private final float mMinRatio, mMaxRatio;
    private final boolean mDragToFullscreen;
    private DividerAttributes(Builder b) {
        mType = b.mType; mWidthDp = b.mWidthDp; mColor = b.mColor;
        mMinRatio = b.mMinRatio; mMaxRatio = b.mMaxRatio; mDragToFullscreen = b.mDragToFullscreen;
    }
    public int getDividerType() { return mType; }
    public int getWidthDp() { return mWidthDp; }
    public int getDividerColor() { return mColor; }
    public float getPrimaryMinRatio() { return mMinRatio; }
    public float getPrimaryMaxRatio() { return mMaxRatio; }
    public boolean isDraggingToFullscreenAllowed() { return mDragToFullscreen; }
    public static final class Builder {
        private final int mType;
        private int mWidthDp = WIDTH_SYSTEM_DEFAULT, mColor;
        private float mMinRatio = RATIO_SYSTEM_DEFAULT, mMaxRatio = RATIO_SYSTEM_DEFAULT;
        private boolean mDragToFullscreen;
        public Builder(int dividerType) { mType = dividerType; }
        public Builder setWidthDp(int w) { mWidthDp = w; return this; }
        public Builder setDividerColor(int c) { mColor = c; return this; }
        public Builder setPrimaryMinRatio(float r) { mMinRatio = r; return this; }
        public Builder setPrimaryMaxRatio(float r) { mMaxRatio = r; return this; }
        public Builder setDraggingToFullscreenAllowed(boolean a) { mDragToFullscreen = a; return this; }
        public DividerAttributes build() { return new DividerAttributes(this); }
    }
}
