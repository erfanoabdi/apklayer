#ifndef APPINFO_H
#define APPINFO_H
#include <stddef.h>

/* The Android package of a window: the last field of an "apklayer:<pid>:<instance>:<package>" name, else the name itself. */
const char *appinfo_package(const char *name);
/* The app's label from its install record (info.properties); out is the package when there is none. */
void appinfo_label(const char *package, char *out, size_t n);
#endif
