package com.macapk.loader;

/**
 * Mouse-wheel delivery switches (2026-10-19, v1.0.2273). A classic wheel notch
 * reaches an app as Android delivers it: one MotionEvent ACTION_SCROLL from
 * SOURCE_MOUSE with AXIS_VSCROLL / AXIS_HSCROLL (InputReader CursorInputMapper),
 * sent to the window and the view UNDER THE POINTER, and the framework's own
 * scrollers answer it from onGenericMotionEvent (AOSP ScrollView,
 * HorizontalScrollView, AbsListView). App-shipped RecyclerView,
 * NestedScrollView and Compose read it themselves. Before, every notch was a
 * synthetic one-frame touch drag (v1.0.2254: at least 24pt), which a
 * RecyclerView whose DOWN lands on a row never scrolled (it intercepts on the
 * single MOVE and the gesture ends before any scroll).
 */
public final class WheelScroll {
    private WheelScroll() {}

    /** MACAPK_WHEEL_SCROLL_VIEWS (default ON; =0 = the framework scrollers ignore
     *  ACTION_SCROLL and ViewGroup offers a pointer generic event to every child
     *  in turn, the pre-v1.0.2273 state). */
    public static final boolean VIEWS = !"0".equals(System.getenv("MACAPK_WHEEL_SCROLL_VIEWS"));

    /** MACAPK_WHEEL_SCROLL_ALL (default ON; =0 = only a window whose input queue
     *  was taken gets ACTION_SCROLL, every other window gets the v1.0.2254 touch
     *  drag; the v1.0.2267 state). */
    public static final boolean ALL = !"0".equals(System.getenv("MACAPK_WHEEL_SCROLL_ALL"));
}
