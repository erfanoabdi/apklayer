package org.robolectric.shadows;

import android.graphics.Rect;
import android.view.SurfaceControl;

/** apklayer patch: WMS's container transform, separate from BLAST's buffer geometry. */
final class WindowSurface {
  private SurfaceControl parent;

  void place(SurfaceControl child, ShadowWindowManagerGlobal.WindowInfo window) {
    if (child == null || !child.isValid()) return;
    if (parent == null) {
      parent = new SurfaceControl.Builder()
          .setName("apklayer-window:" + window.attrs.getTitle())
          .setContainerLayer()
          .build();
    }
    // WindowState.transformFrameToSurfacePosition: shadow space lies outside
    // the window frame. BLAST resets the child's destination frame on each
    // buffer, so this translation belongs to the window's parent container.
    Rect insets = window.attrs.surfaceInsets;
    try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
      t.reparent(child, parent)
          .setPosition(parent, window.frame.left - insets.left, window.frame.top - insets.top)
          .setLayerStack(parent, Integer.getInteger("apklayer.layerstack", 0))
          .setLayer(parent, window.attrs.type * 1000 + window.zOrder)
          .setLayer(child, 0)
          .show(parent)
          .show(child)
          .apply();
    }
  }

  void close() {
    if (parent == null) return;
    try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
      t.remove(parent).apply();
    }
    parent.release();
    parent = null;
  }
}
