/* splash.c: the picture a window shows until its app presents its first frame. */
#ifndef APKLAYER_SPLASH_H
#define APKLAYER_SPLASH_H
struct window;
struct wl_buffer *splash_buffer(const struct window *w, int width, int height);  /* XRGB8888, physical pixels; NULL on failure */
#endif
