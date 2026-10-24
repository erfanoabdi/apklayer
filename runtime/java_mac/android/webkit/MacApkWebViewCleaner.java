package android.webkit;

/** v1.0.1642: GC-driven teardown of a WebView's native WKWebView peer. One
 *  shared Cleaner thread; each action holds only the view id, so registering it
 *  never keeps the WebView reachable. WebView.destroy() runs the same action
 *  through Cleanable.clean(), so explicit and GC teardown happen exactly once. */
final class MacApkWebViewCleaner {
    private static final java.lang.ref.Cleaner CLEANER = java.lang.ref.Cleaner.create();

    private MacApkWebViewCleaner() {}

    static java.lang.ref.Cleaner.Cleanable register(WebView wv, int viewId) {
        return CLEANER.register(wv, new Release(viewId));
    }

    private static final class Release implements Runnable {
        private final int viewId;
        Release(int viewId) { this.viewId = viewId; }
        @Override public void run() {
            try {
                MacApkWebViewBridge.unregister(viewId);
                MacApkWebViewBridge.nativeDestroy(viewId);
            } catch (Throwable ignored) { }
        }
    }
}
