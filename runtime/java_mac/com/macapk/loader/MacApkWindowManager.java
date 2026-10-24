// MacApkWindowManager -- thin concrete impl of android.view.WindowManager.
//
// v1.0.691: addView/removeView/updateViewLayout are no longer no-ops. Mac.apk
// owns ONE NSWindow, so a floating window (Dialog / PopupWindow / PopupMenu /
// Spinner dropdown / AutoComplete suggestions) is registered in
// WindowOverlayStack and composited over the Activity content view by
// ViewCanvasDriver, with touch hit-tested by TouchDispatcher. Before this every
// such window drew ZERO pixels (the highest-blast-radius no-op the v677 audit
// found). getDefaultDisplay still returns the shared metrics.
package com.macapk.loader;

import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

public final class MacApkWindowManager implements WindowManager {
    private static final Display DEFAULT_DISPLAY = new Display();

    @Override public Display getDefaultDisplay() { return DEFAULT_DISPLAY; }

    @Override public void addView(View view, ViewGroup.LayoutParams params) {
        // AOSP WindowManagerGlobal.addView (android14 :397) builds a
        // ViewRootImpl, whose field `final ViewRootHandler mHandler = new
        // ViewRootHandler();` (ViewRootImpl.java:6073) is an implicit-Looper
        // Handler: a window cannot be added from a thread without a Looper (the
        // GLSurfaceView GLThread). Same demand here, before anything is added.
        // MACAPK_GL_THREAD_NO_LOOPER; docs/gl-thread-has-no-looper_2026-10-01.md
        android.os.Handler.macapkRequireLooperOnGlThread();
        WindowOverlayStack.add(view, params);
    }
    @Override public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
        WindowOverlayStack.update(view, params);
    }
    @Override public void removeView(View view) {
        WindowOverlayStack.remove(view);
    }
    @Override public void removeViewImmediate(View view) {
        WindowOverlayStack.remove(view);
    }
}
