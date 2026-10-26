/* The ANativeWindow shared by the libandroid and libEGL shims: one per Surface, filled from the
 * host's HostSurface (identity hash, size). Frames go to $APKLAYER_GLDIR/<id>.rgba, see HostSurfaces.java. */
#ifndef APKLAYER_ANWINDOW_H
#define APKLAYER_ANWINDOW_H
struct ANativeWindow { int id, width, height; };
#endif
