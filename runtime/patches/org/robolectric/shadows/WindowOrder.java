package org.robolectric.shadows;

import android.view.View;
import android.view.WindowManager.LayoutParams;
import java.util.List;

/**
 * apklayer patch: the window manager's stacking, one rule for the screen (WindowSurface) and for input (ShadowUiAutomation, the host's
 * HostWindows). Top-level windows stack by type, then in the order they were added; a sub-window (a popup, a panel, an attached dialog:
 * FIRST_SUB_WINDOW..LAST_SUB_WINDOW, its token is its parent's window) stacks with its parent at its sub-layer, as WindowState does:
 * a popup of the activity stays below a dialog shown over the activity, whatever the order they came in.
 */
public final class WindowOrder {
  private WindowOrder() {}

  /** WindowManagerPolicy#getSubWindowLayerFromTypeLw: where a sub-window sits relative to its parent (0: the parent itself). */
  public static int subLayer(int type) {
    switch (type) {
      case LayoutParams.TYPE_APPLICATION_MEDIA: return -2;
      case LayoutParams.TYPE_APPLICATION_MEDIA_OVERLAY: return -1;
      case LayoutParams.TYPE_APPLICATION_PANEL:
      case LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG: return 1;
      case LayoutParams.TYPE_APPLICATION_SUB_PANEL: return 2;
      case LayoutParams.TYPE_APPLICATION_ABOVE_SUB_PANEL: return 3;
      default: return 0;
    }
  }

  static boolean isSubWindow(int type) {
    return type >= LayoutParams.FIRST_SUB_WINDOW && type <= LayoutParams.LAST_SUB_WINDOW;
  }

  /**
   * The stacking key of each window of WindowManagerGlobal's parallel lists (mViews, mParams: in the order the windows were added),
   * higher is above.
   */
  public static long[] keys(List<View> views, List<LayoutParams> params) {
    long[] keys = new long[views.size()];
    for (int i = 0; i < keys.length; i++) {
      LayoutParams p = params.get(i);
      int parent = -1;
      if (isSubWindow(p.type) && p.token != null) {
        for (int j = 0; j < keys.length && parent < 0; j++) if (j != i && views.get(j).getWindowToken() == p.token) parent = j;
      }
      keys[i] = parent < 0 ? key(p.type, i, 0, 0) : key(params.get(parent).type, parent, subLayer(p.type), i + 1);
    }
    return keys;
  }

  /** Top-level type and order, then the sub-layer and the sub-window's own order (0 for the top-level window itself). */
  private static long key(int type, int order, int subLayer, int own) {
    return ((long) type << 42) | ((long) order << 21) | ((long) (subLayer + 4) << 18) | own;
  }
}
