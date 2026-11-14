package org.robolectric.shadows;

import org.robolectric.shadows.SystemUi.NavigationBarBehavior;
import org.robolectric.shadows.SystemUi.SystemBar.Side;

/**
 * apklayer patch: the shell's navigation bar (bridge/shell/navbar.c, mobile style) as the display's navigation bar: a bottom bar of the
 * height the shell covers, so the windows' WindowInsets carry it (navigationBars(), systemBars()) and an app's content stays above it, or
 * draws behind it and pads by the inset when it lays out edge to edge. The status bar stays Robolectric's default (none: the host's own
 * top bar). Takes effect at the windows' next relayout (AppHost.hostResize asks for one).
 * The host's on-screen keyboard (the phones' Maliit keyboard, bridge/shell/maliit.c) is not part of the bar: it is the IME's own insets
 * source (setIme, patch 0027), and each window's softInputMode decides what it does (ViewRootImpl: adjustResize, adjustPan).
 */
public final class NavigationBarInset {
  private NavigationBarInset() {}

  private static final class Bar implements NavigationBarBehavior {
    private final int px;
    Bar(int px) { this.px = px; }
    @Override public Side calculateSide(int displayId) { return Side.BOTTOM; }
    @Override public int calculateSize(int displayId) { return px; }
  }

  private static volatile java.util.function.Consumer<Boolean> listener;

  /** Who learns when the app hides or shows the navigation bar (immersive mode): the host tells the shell, which hides its bar. */
  public static void onVisibility(java.util.function.Consumer<Boolean> l) { listener = l; }

  /** ShadowWindowManagerGlobal.applyInsets (patch 0019): the app's requested visible types changed the navigation bar's visibility. The
   * inset follows by itself (an invisible bar has no insets); the shell hides or shows the bar. */
  public static void visibilityChanged(boolean visible) {
    java.util.function.Consumer<Boolean> l = listener;
    if (l != null) l.accept(visible);
  }

  private static volatile int imePx;

  /** The host's keyboard covers the bottom px pixels of the display (0: hidden): the windows get the IME's insets at once. */
  public static void setIme(int px) {
    if (px == imePx) return;
    imePx = px;
    ShadowWindowManagerGlobal.getWindowSessionDelegate().notifyInsetsChanges(SystemUi.systemUiForDefaultDisplay(), null);
  }

  /** ShadowWindowManagerGlobal (patch 0027): the IME's source in a window's InsetsState, as InsetsStateController gives it. */
  static void putIme(android.graphics.Rect display, android.view.InsetsState state) {
    if (state == null) return;
    android.view.InsetsSource ime = state.getOrCreateSource(android.view.InsetsSource.ID_IME, android.view.WindowInsets.Type.ime());
    int px = imePx;
    ime.setFrame(display.left, display.bottom - px, display.right, display.bottom);
    ime.setVisible(px > 0);
  }

  private static volatile java.util.function.Consumer<Boolean> fullscreenListener;

  /** Who learns when the app goes fullscreen or leaves it (status bar hidden or shown): the host tells the shell. */
  public static void onFullscreen(java.util.function.Consumer<Boolean> l) { fullscreenListener = l; }

  /** ShadowWindowManagerGlobal.applyInsets (patch 0019): the app hid (fullscreen) or showed the status bar, as Android's fullscreen
   * (hide(systemBars()), SYSTEM_UI_FLAG_FULLSCREEN, FLAG_FULLSCREEN) does. */
  public static void statusBarChanged(boolean visible) {
    java.util.function.Consumer<Boolean> l = fullscreenListener;
    if (l != null) l.accept(!visible);
  }

  /**
   * IWindowManager.getWindowInsets (a window context's WindowMetrics): the insets state the app's newest window has (its bars, the IME), as
   * WindowManagerService answers from the display's state. False without a window yet: the state stays empty (no insets).
   */
  public static boolean windowInsets(android.view.InsetsState out) {
    try {
      java.util.Map<?, ?> windows = org.robolectric.util.ReflectionHelpers.getField(ShadowWindowManagerGlobal.getWindowSessionDelegate(), "windows");
      ShadowWindowManagerGlobal.WindowInfo last = null;
      synchronized (windows) { for (Object window : windows.values()) last = (ShadowWindowManagerGlobal.WindowInfo) window; }
      if (last == null || last.insetsState == null || out == null) return false;
      out.set(last.insetsState, true);
      return true;
    } catch (RuntimeException noWindows) { return false; }
  }

  /** The navigation bar is px pixels high (0: none, the desktop style). */
  public static void set(int px) {
    SystemUi.systemUiForDefaultDisplay().setNavigationBarBehavior(px > 0 ? new Bar(px) : SystemUi.NO_NAVIGATION_BAR);
  }
}
