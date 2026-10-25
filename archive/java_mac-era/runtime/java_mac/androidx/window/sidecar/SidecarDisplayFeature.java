package androidx.window.sidecar;
/** A fold/hinge feature. Never produced on a flat display; the type resolves. */
public final class SidecarDisplayFeature {
    public static final int TYPE_FOLD = 1;
    public static final int TYPE_HINGE = 2;
    private android.graphics.Rect mRect = new android.graphics.Rect();
    private int mType = TYPE_FOLD;
    public SidecarDisplayFeature() {}
    public android.graphics.Rect getRect() { return mRect; }
    public void setRect(android.graphics.Rect rect) { mRect = rect != null ? rect : new android.graphics.Rect(); }
    public int getType() { return mType; }
    public void setType(int type) { mType = type; }
}
