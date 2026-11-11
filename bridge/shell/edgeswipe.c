/* Edge swipes on a fullscreen window: Android's transient system bars, for the compositor's edges. A compositor blocks its screen edges
 * while the active window is fullscreen (KWin's Edge::checkBlocking: touch and pointer, unless kwinrc [ScreenEdges] RemainActiveOnFullscreen),
 * so on a touchscreen KDE's edge swipes (overview, panels, the virtual keyboard) never start over an Android game or video. As Android does
 * in immersive mode (a swipe from an edge shows the bars for a moment), an inward swipe from an edge of a fullscreen window takes that
 * touch away from the app (cancel, as Android's pilferPointers) and leaves the compositor's fullscreen state for a while: its edges and
 * panels work again. The window goes back to fullscreen once it is active and no finger has touched it for HOLD_MS; the app's own
 * fullscreen request (want_fullscreen) stays as it was all the time. */
#include "shell.h"

#define EDGE 24      /* logical px from the window edge where a swipe may start (KWin's touch edge zone is about this wide) */
#define TRAVEL 40    /* logical px inward before it counts as an edge swipe */
#define HOLD_MS 3000

static struct window *start_window;  /* a finger went down in an edge zone of this fullscreen window */
static int start_finger, start_x, start_y, start_edge;  /* edge: 1 left, 2 right, 3 top, 4 bottom */
static struct window *held;          /* out of fullscreen since an edge swipe */
static long long hold_until;
static int held_out;                 /* the compositor's configure without fullscreen came */

int edgeswipe_down(struct window *w, int finger, int x, int y)
{
    if (held == w) hold_until = now_ms() + HOLD_MS;  /* the user works with the window: it stays out of fullscreen meanwhile */
    if (!w->fullscreen || !w->toplevel || start_window) return 0;
    int edge = x < EDGE ? 1 : x >= w->cfg_w - EDGE ? 2 : y < EDGE ? 3 : y >= w->cfg_h - EDGE ? 4 : 0;
    if (!edge) return 0;
    start_window = w; start_finger = finger; start_x = x; start_y = y; start_edge = edge;
    return 0;  /* the app gets the touch until it turns out to be an edge swipe */
}

/* 1: the finger's sequence just became an edge swipe (the caller cancels it for the app) */
int edgeswipe_motion(int finger, int x, int y)
{
    if (!start_window || finger != start_finger) return 0;
    struct window *w = start_window;
    int dx = x - start_x, dy = y - start_y;
    int in = start_edge == 1 ? dx : start_edge == 2 ? -dx : start_edge == 3 ? dy : -dy;
    int across = start_edge <= 2 ? abs(dy) : abs(dx);
    if (in < TRAVEL || across > in) return 0;
    start_window = NULL;
    if (!w->client || !w->fullscreen) return 0;
    fprintf(stderr, "[shell] %s: edge swipe, out of fullscreen for the desktop's edges\n", w->title);
    held = w;
    held_out = 0;
    hold_until = now_ms() + HOLD_MS;
    xdg_toplevel_unset_fullscreen(w->toplevel);
    if (w->mobile) xdg_toplevel_set_maximized(w->toplevel);
    return 1;
}

void edgeswipe_up(int finger) { if (start_window && finger == start_finger) start_window = NULL; }
void edgeswipe_cancel(void) { start_window = NULL; }

/* main loop: ms until the next check (-1: none); puts the window back into fullscreen when its time is up */
int edgeswipe_tick(void)
{
    if (!held) return -1;
    struct window *w = held;
    if (!w->fullscreen) held_out = 1;
    /* gone, the app left fullscreen, or fullscreen again by itself (F11, the app's request) */
    if (!w->client || !w->toplevel || !w->want_fullscreen || (held_out && w->fullscreen)) { held = NULL; return -1; }
    long long wait = hold_until - now_ms();
    if (wait > 0) return (int)wait;
    if (!held_out) { held = NULL; return -1; }  /* the compositor kept it fullscreen: nothing to undo */
    if (!w->activated) { hold_until = now_ms() + HOLD_MS; return HOLD_MS; }  /* another window is in front: not over it */
    held = NULL;
    fprintf(stderr, "[shell] %s: fullscreen again after the edge swipe\n", w->title);
    xdg_toplevel_set_fullscreen(w->toplevel, NULL);
    return -1;
}

/* a window going away */
void edgeswipe_forget(struct window *w)
{
    if (start_window == w) start_window = NULL;
    if (held == w) held = NULL;
}
