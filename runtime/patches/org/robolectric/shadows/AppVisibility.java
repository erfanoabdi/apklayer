package org.robolectric.shadows;

import android.os.IBinder;
import android.view.IWindow;
import android.view.View;
import android.view.ViewRootImpl;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.robolectric.util.ReflectionHelpers;

/**
 * apklayer patch: WindowManagerService's app visibility (ActivityRecord's visibility, sent to each of its windows): a stopped activity's
 * windows, its dialogs and the popups over them included, are hidden through ViewRootImpl.dispatchAppVisibility without their views
 * changing, and a window added while its activity is hidden comes up hidden (addWindow leaves out ADD_FLAG_APP_VISIBLE). A hidden window
 * relayouts GONE (ViewRootImpl.getHostVisibility: off the screen) and takes no input.
 */
public final class AppVisibility {
  private AppVisibility() {}

  private static final Set<IBinder> hidden = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

  /** The host's ActivityTask: the activity (its token) stopped or started again. */
  public static void setActivityVisible(IBinder activityToken, boolean visible) {
    if (visible) hidden.remove(activityToken);
    else hidden.add(activityToken);
  }

  /** Whether a window with this token belongs to a hidden activity: the token is the activity's, or a parent window's (a sub-window). */
  static boolean hides(IBinder token, Map<IWindow, ShadowWindowManagerGlobal.WindowInfo> windows) {
    for (int depth = 0; token != null && depth < 8; depth++) {
      if (hidden.contains(token)) return true;
      IBinder parent = null;
      for (Map.Entry<IWindow, ShadowWindowManagerGlobal.WindowInfo> e : windows.entrySet()) {
        if (e.getKey().asBinder() == token) parent = e.getValue().attrs.token;
      }
      token = parent;
    }
    return false;
  }

  /** A window shown on the screen and taking input: its root view VISIBLE and its app visible (ViewRootImpl.mAppVisible). */
  public static boolean shown(View root) {
    if (root.getVisibility() != View.VISIBLE) return false;
    ViewRootImpl impl = root.getViewRootImpl();
    return impl == null || (boolean) ReflectionHelpers.getField(impl, "mAppVisible");
  }
}
