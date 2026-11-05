/* glbench: one GLES 2 render loop built for native Linux (linux.c) and for Android through apklayer (tests/apps/hello-glbench), so
 * their numbers compare. The platform makes a context current (window or offscreen) and passes a present callback; bench.c draws
 * the scenes, times each frame and its present, and prints one result line per scene. */
#pragma once

#include <EGL/egl.h>

struct bench_target {
    const char *side;   /* "linux" or "android" */
    const char *mode;   /* "window", "window-novsync", "offscreen" */
    int width, height;
    EGLDisplay dpy;
    EGLSurface surface; /* the window's surface; offscreen: EGL_NO_SURFACE or a pbuffer, drawing goes to an FBO */
    int offscreen;
    void (*print)(const char *line);
};

/* Runs every scene named in `scenes` (comma-separated: clear,fill,draws; NULL: all) for `seconds` each. Returns 0 on success. */
int bench_run(const struct bench_target *t, const char *scenes, double seconds);
