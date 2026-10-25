package androidx.window.extensions.embedding;

public final class SplitInfo {
    public static final class Token {
        private Token() {}
        public static Token createFromBinder(android.os.IBinder binder) { return new Token(); }
    }
    SplitInfo() {}
    public ActivityStack getPrimaryActivityStack() { return null; }
    public ActivityStack getSecondaryActivityStack() { return null; }
    public SplitAttributes getSplitAttributes() { return new SplitAttributes.Builder().build(); }
    public float getSplitRatio() { return 0.0f; }
    public android.os.IBinder getToken() { return null; }
    public Token getSplitInfoToken() { return null; }
}
