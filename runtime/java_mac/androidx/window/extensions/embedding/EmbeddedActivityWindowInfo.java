package androidx.window.extensions.embedding;

public final class EmbeddedActivityWindowInfo {
    private final android.app.Activity mActivity;
    EmbeddedActivityWindowInfo(android.app.Activity activity) { mActivity = activity; }
    public android.app.Activity getActivity() { return mActivity; }
    public boolean isEmbedded() { return false; }
    public android.graphics.Rect getTaskBounds() { return new android.graphics.Rect(); }
    public android.graphics.Rect getActivityStackBounds() { return new android.graphics.Rect(); }
}
