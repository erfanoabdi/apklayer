package androidx.window.extensions;
/** The reflective entry point androidx.window loads. Returns a singleton
 *  flat-device WindowExtensions. */
public final class WindowExtensionsProvider {
    private static final WindowExtensions INSTANCE = new WindowExtensions() {
        private final androidx.window.extensions.layout.WindowLayoutComponent mLayout =
                new androidx.window.extensions.layout.WindowLayoutComponent() { };
        private final androidx.window.extensions.embedding.ActivityEmbeddingComponent mEmbedding =
                new androidx.window.extensions.embedding.ActivityEmbeddingComponent() { };
        private final androidx.window.extensions.area.WindowAreaComponent mArea =
                new androidx.window.extensions.area.WindowAreaComponent() { };
        @Override public androidx.window.extensions.layout.WindowLayoutComponent getWindowLayoutComponent() { return mLayout; }
        @Override public androidx.window.extensions.embedding.ActivityEmbeddingComponent getActivityEmbeddingComponent() { return mEmbedding; }
        @Override public androidx.window.extensions.area.WindowAreaComponent getWindowAreaComponent() { return mArea; }
    };
    private WindowExtensionsProvider() {}
    public static WindowExtensions getWindowExtensions() { return INSTANCE; }
}
