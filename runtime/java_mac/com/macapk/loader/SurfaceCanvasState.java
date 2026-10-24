// Shared state for the SurfaceView Canvas-blit path. Holds one flag
// so the "native method missing" warning logs exactly once. Pulled
// into its own class (rather than a private static on SurfaceView) so
// the framework jar's SurfaceView stays portable -- a fresh runtime
// build can replace the loader class without recompiling framework.
package com.macapk.loader;

public final class SurfaceCanvasState {
    private SurfaceCanvasState() {}
    public static volatile boolean warnedMissingNative = false;
}
