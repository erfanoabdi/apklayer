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

  /** The navigation bar is px pixels high (0: none, the desktop style). */
  public static void set(int px) {
    SystemUi.systemUiForDefaultDisplay().setNavigationBarBehavior(px > 0 ? new Bar(px) : SystemUi.NO_NAVIGATION_BAR);
  }
}
