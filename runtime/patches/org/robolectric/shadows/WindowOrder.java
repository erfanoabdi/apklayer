package org.robolectric.shadows;

import android.os.IBinder;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * apklayer patch: the window manager's stacking, one rule for the screen (WindowSurface) and for input (ShadowUiAutomation, the host's
 * HostWindows). An application window (a type below FIRST_SYSTEM_WINDOW: an activity's window and its dialogs, their token is the
 * activity's) stacks with its activity, as ActivityRecord does: activities in their task's order, the order they were started (a newer
 * activity covers an older one's dialogs: Meet's link sheet under the green room), a relaunched one keeps its place (Android keeps its
 * ActivityRecord; Robolectric's recreation makes a new token), within one activity by type, then by add order; system
 * windows (toasts, overlays) stack above every activity, by type then add order. A sub-window (a popup, a panel, an attached dialog:
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

  private static final Map<IBinder, Integer> activities = new WeakHashMap<>();
  private static int started;

  /**
   * The place of an activity (its token) in the task: the order it was first seen in (its start, AppHost's launch, or its first window),
   * kept by a relaunch. An application window without a token counts as an activity of its own (its window's binder).
   */
  public static synchronized int activityOrder(IBinder token) {
    return token == null ? 0 : activities.computeIfAbsent(token, t -> ++started);
  }

  /** A relaunched activity (ActivityThread keeps its ActivityRecord, Robolectric's recreation gives it a new token) keeps its place. */
  public static synchronized void relaunched(IBinder old, IBinder now) {
    activities.put(now, activityOrder(old));
  }

  /**
   * The stacking key of each window of WindowManagerGlobal's parallel lists (mViews, mParams: in the order the windows were added),
   * higher is above; -1 for a sub-window whose parent window is gone.
   */
  public static long[] keys(List<View> views, List<LayoutParams> params) {
    long[] keys = new long[views.size()];
    for (int i = 0; i < keys.length; i++) {
      LayoutParams p = params.get(i);
      int parent = -1;
      if (isSubWindow(p.type) && p.token != null) {
        for (int j = 0; j < keys.length && parent < 0; j++) if (j != i && views.get(j).getWindowToken() == p.token) parent = j;
      }
      if (isSubWindow(p.type) && p.token != null && parent < 0) {
        keys[i] = -1; // its parent window was removed, and the window manager removed it with it: no window, no input
        continue;
      }
      int top = parent < 0 ? i : parent;
      LayoutParams t = params.get(top);
      IBinder activity = t.type >= LayoutParams.FIRST_SYSTEM_WINDOW ? null : t.token != null ? t.token : views.get(top).getWindowToken();
      keys[i] = key(t.type, activityOrder(activity), top, parent < 0 ? 0 : subLayer(p.type), parent < 0 ? 0 : i + 1);
    }
    return keys;
  }

  /** Tier (0: an application window, else the system type), activity, type and order, then the sub-layer and the sub-window's own order. */
  private static long key(int type, int activity, int order, int subLayer, int own) {
    long tier = type >= LayoutParams.FIRST_SYSTEM_WINDOW ? type : 0;
    return (tier << 51) | ((long) Math.min(activity, 0x1fff) << 38) | ((long) (type & 0x7f) << 31) | ((long) order << 18) | ((long) (subLayer + 4) << 15) | own;
  }

  /**
   * The same order as a SurfaceFlinger layer of a top-level window (its zOrder: the add counter; activity: activityOrder; baseZOrder: its
   * activity's base window's zOrder). shortcut: ties past 7 windows of one type within one activity, and past 2^20 activities in one run; a
   * container per activity would remove both if that ever matters.
   */
  static int screenLayer(int type, int zOrder, int activity, int baseZOrder) {
    if (type >= LayoutParams.FIRST_SYSTEM_WINDOW) return 0x40000000 + (type - LayoutParams.FIRST_SYSTEM_WINDOW) * 100000 + Math.min(zOrder, 99999);
    return Math.min(activity, 0xfffff) * 1024 + type * 8 + Math.min(Math.max(zOrder - baseZOrder, 0), 7);
  }
}
