package androidx.window.extensions.embedding;

public final class WindowAttributes {
    public static final int DIM_AREA_ON_TASK = 0;
    public static final int DIM_AREA_ON_ACTIVITY_STACK = 1;
    private final int mDimAreaBehavior;
    public WindowAttributes(int dimAreaBehavior) { mDimAreaBehavior = dimAreaBehavior; }
    public int getDimAreaBehavior() { return mDimAreaBehavior; }
}
