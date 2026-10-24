package androidx.window.extensions.layout;
/** A fold/hinge. Mac.apk is flat, so one is never produced; the type resolves.
 *  Source: androidx.window.extensions.layout.FoldingFeature */
public class FoldingFeature implements DisplayFeature {
    public static final int TYPE_FOLD = 1;
    public static final int TYPE_HINGE = 2;
    public static final int STATE_FLAT = 1;
    public static final int STATE_HALF_OPENED = 2;
    private final android.graphics.Rect mBounds;
    private final int mType;
    private final int mState;
    public FoldingFeature(android.graphics.Rect bounds, int type, int state) {
        mBounds = bounds; mType = type; mState = state;
    }
    @Override public android.graphics.Rect getBounds() { return mBounds; }
    public int getType() { return mType; }
    public int getState() { return mState; }
}
