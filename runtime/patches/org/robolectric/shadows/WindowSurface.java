package org.robolectric.shadows;

import android.graphics.Rect;
import android.view.IWindow;
import android.view.SurfaceControl;
import java.util.Map;

/** apklayer patch: WMS's container transform, separate from BLAST's buffer geometry. */
final class WindowSurface {
  private SurfaceControl parent;
  private SurfaceControl dim; // FLAG_DIM_BEHIND: Dimmer's color layer, relative below the window

  void place(SurfaceControl child, ShadowWindowManagerGlobal.WindowInfo window, Map<IWindow, ShadowWindowManagerGlobal.WindowInfo> windows,
      boolean visible) {
    if (child == null || !child.isValid()) {
      showDim(false, window);
      return;
    }
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
            .setLayer(parent, WindowOrder.screenLayer(window.attrs.type, window.zOrder, activityOrder(window, windows), activityZOrder(window, windows)));
      }
      t.setLayer(child, 0)
          .show(parent)
          .show(child)
          .apply();
    }
    showDim(visible && (window.attrs.flags & android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0, window);
  }

  /**
   * Dimmer.updateDims: black over the display at dimAmount, just below the window, while the window is shown and asks for it. A child of
   * the window's container under the window's own layer (Dimmer's relative layer -1 to the window, without a relative parent to follow).
   */
  private void showDim(boolean shown, ShadowWindowManagerGlobal.WindowInfo window) {
    if (dim == null && (!shown || parent == null)) return;
    if (dim == null) {
      dim = new SurfaceControl.Builder().setName("Dim Layer for - " + window.attrs.getTitle()).setColorLayer().build();
    }
    try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
      if (shown) {
        Rect insets = window.attrs.surfaceInsets; // the container's origin on the screen: the dim covers the display from there
        t.reparent(dim, parent)
            .setLayer(dim, -1)
            .setPosition(dim, insets.left - window.frame.left, insets.top - window.frame.top)
            .setColor(dim, new float[] {0, 0, 0})
            .setAlpha(dim, window.attrs.dimAmount)
            .setCrop(dim, window.displayFrame)
            .show(dim);
      } else {
        t.hide(dim);
      }
      t.apply();
    }
  }

  /** The window's activity's place (WindowOrder.activityOrder): its token, or its own window's binder without one. */
  private static int activityOrder(ShadowWindowManagerGlobal.WindowInfo window, Map<IWindow, ShadowWindowManagerGlobal.WindowInfo> windows) {
    if (window.attrs.token != null) return WindowOrder.activityOrder(window.attrs.token);
    for (Map.Entry<IWindow, ShadowWindowManagerGlobal.WindowInfo> e : windows.entrySet()) if (e.getValue() == window) return WindowOrder.activityOrder(e.getKey().asBinder());
    return 0;
  }

  /** The zOrder of the window's activity's base window, its own without one: the add order within the activity. */
  private static int activityZOrder(ShadowWindowManagerGlobal.WindowInfo window, Map<IWindow, ShadowWindowManagerGlobal.WindowInfo> windows) {
    if (window.attrs.type >= android.view.WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW || window.attrs.token == null) return window.zOrder;
    for (ShadowWindowManagerGlobal.WindowInfo w : windows.values()) {
      if (w.attrs.type == android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION && w.attrs.token == window.attrs.token) return w.zOrder;
    }
    return window.zOrder;
  }

  void close() {
    if (dim != null) {
      try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
        t.remove(dim).apply();
      }
      dim.release();
      dim = null;
    }
    if (parent == null) return;
    try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
      t.remove(parent).apply();
    }
    parent.release();
    parent = null;
  }
}
