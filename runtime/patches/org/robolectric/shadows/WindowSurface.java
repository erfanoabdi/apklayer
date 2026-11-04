package org.robolectric.shadows;

import android.graphics.Rect;
import android.view.IWindow;
import android.view.SurfaceControl;
import java.util.Map;

/** apklayer patch: WMS's container transform, separate from BLAST's buffer geometry. */
final class WindowSurface {
  private SurfaceControl parent;

  void place(SurfaceControl child, ShadowWindowManagerGlobal.WindowInfo window, Map<IWindow, ShadowWindowManagerGlobal.WindowInfo> windows) {
    if (child == null || !child.isValid()) return;
    ShadowWindowManagerGlobal.WindowInfo host = null; // a sub-window's parent window (its token): WindowState stacks it there (WindowOrder)
    if (WindowOrder.isSubWindow(window.attrs.type) && window.attrs.token != null) {
      for (Map.Entry<IWindow, ShadowWindowManagerGlobal.WindowInfo> e : windows.entrySet()) {
        if (e.getValue() != window && e.getKey().asBinder() == window.attrs.token) host = e.getValue();
      }
    }
    SurfaceControl above = host == null ? null : host.surface.parent;
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
      t.reparent(child, parent);
      if (above != null && above.isValid()) { // inside the parent's container, in its coordinates: below the next window above the parent
        Rect hostInsets = host.attrs.surfaceInsets;
        t.reparent(parent, above)
            .setPosition(parent, window.frame.left - insets.left - (host.frame.left - hostInsets.left),
                window.frame.top - insets.top - (host.frame.top - hostInsets.top))
            .setLayer(parent, WindowOrder.subLayer(window.attrs.type) * 100000 + window.zOrder);
      } else {
        t.setPosition(parent, window.frame.left - insets.left, window.frame.top - insets.top)
            .setLayerStack(parent, Integer.getInteger("apklayer.layerstack", 0))
            .setLayer(parent, WindowOrder.screenLayer(window.attrs.type, window.zOrder, activityZOrder(window, windows)));
      }
      t.setLayer(child, 0)
          .show(parent)
          .show(child)
          .apply();
    }
  }

  /** The zOrder of the window's activity (its token's base window, WindowOrder.activityOrder), its own without one. */
  private static int activityZOrder(ShadowWindowManagerGlobal.WindowInfo window, Map<IWindow, ShadowWindowManagerGlobal.WindowInfo> windows) {
    if (window.attrs.type >= android.view.WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW || window.attrs.token == null) return window.zOrder;
    for (ShadowWindowManagerGlobal.WindowInfo w : windows.values()) {
      if (w.attrs.type == android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION && w.attrs.token == window.attrs.token) return w.zOrder;
    }
    return window.zOrder;
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
