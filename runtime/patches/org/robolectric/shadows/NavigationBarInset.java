package org.robolectric.shadows;

import org.robolectric.shadows.SystemUi.NavigationBarBehavior;
import org.robolectric.shadows.SystemUi.SystemBar.Side;

/**
 * apklayer patch: the shell's navigation bar (bridge/shell/navbar.c, mobile style) as the display's navigation bar: a bottom bar of the
 * height the shell covers, so the windows' WindowInsets carry it (navigationBars(), systemBars()) and an app's content stays above it, or
 * draws behind it and pads by the inset when it lays out edge to edge. The status bar stays Robolectric's default (none: the host's own
 * top bar). Takes effect at the windows' next relayout (AppHost.hostResize asks for one).
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

  private static volatile java.util.function.Consumer<Boolean> fullscreenListener;

  /** Who learns when the app goes fullscreen or leaves it (status bar hidden or shown): the host tells the shell. */
  public static void onFullscreen(java.util.function.Consumer<Boolean> l) { fullscreenListener = l; }

  /** ShadowWindowManagerGlobal.applyInsets (patch 0019): the app hid (fullscreen) or showed the status bar, as Android's fullscreen
   * (hide(systemBars()), SYSTEM_UI_FLAG_FULLSCREEN, FLAG_FULLSCREEN) does. */
  public static void statusBarChanged(boolean visible) {
    java.util.function.Consumer<Boolean> l = fullscreenListener;
    if (l != null) l.accept(!visible);
  }

  /** The navigation bar is px pixels high (0: none, the desktop style). */
  public static void set(int px) {
    SystemUi.systemUiForDefaultDisplay().setNavigationBarBehavior(px > 0 ? new Bar(px) : SystemUi.NO_NAVIGATION_BAR);
  }
}
