/* The fake GNSS receiver: a fixed point (location=lat,lon[,alt], accuracy=meters) or the points of a GPX file replayed one per fix
 * (gpx=path: its trkpt/rtept/wpt elements in file order, staying at the last one). Default: AOSP's own mock location. */
#include "apklayer/gnss.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static struct apklayer_gnss_fix fixed = {37.4219999, -122.0840575, 1.60062531, 0, 0, 5.0f};
static struct apklayer_gnss_fix *track;
static int track_len, track_next;

/* the number of attribute `name` ("lat=") inside the tag [tag, end); *ok cleared when it has none */
static double attribute(const char *tag, const char *end, const char *name, int *ok)
{
    const char *a = strstr(tag, name);
    if (!a || a > end) { *ok = 0; return 0; }
    a += strlen(name);
    if (*a == '"' || *a == '\'') a++;
    return strtod(a, NULL);
}

static int load_gpx(const char *path)
{
    FILE *f = fopen(path, "r");
    if (!f) { fprintf(stderr, "gnss fake: %s: %s\n", path, strerror(errno)); return -errno; }
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    rewind(f);
    char *text = malloc(size + 1);
    if (!text || fread(text, 1, size, f) != (size_t)size) { fclose(f); free(text); return -EIO; }
    text[size] = 0;
    fclose(f);
    for (const char *p = text; (p = strchr(p, '<')); p++) {
        if (strncmp(p, "<trkpt", 6) && strncmp(p, "<rtept", 6) && strncmp(p, "<wpt", 4)) continue;
        const char *end = strchr(p, '>');
        if (!end) break;
        int ok = 1;
        struct apklayer_gnss_fix fix = fixed;
        fix.latitude = attribute(p, end, "lat=", &ok);
        fix.longitude = attribute(p, end, "lon=", &ok);
        const char *ele = strstr(end, "<ele>"), *close = strstr(end, "</trkpt>");
        if (ele && (!close || ele < close)) fix.altitude = strtod(ele + 5, NULL);
        if (!ok) continue;
        struct apklayer_gnss_fix *grown = realloc(track, (track_len + 1) * sizeof *track);
        if (!grown) break;
        track = grown;
        track[track_len++] = fix;
    }
    free(text);
    if (!track_len) { fprintf(stderr, "gnss fake: no points in %s\n", path); return -EINVAL; }
    return 0;
}

int apklayer_gnss_probe(void) { return 0; }

int apklayer_gnss_init(int count, const char *const *options)
{
    const char *gpx = NULL;
    for (int i = 0; i < count; i++) {
        const char *o = options[i];
        if (!strncmp(o, "location=", 9)) {
            int n = sscanf(o + 9, "%lf,%lf,%lf", &fixed.latitude, &fixed.longitude, &fixed.altitude);
            if (n < 2) { fprintf(stderr, "gnss fake: location=lat,lon[,alt]\n"); return -EINVAL; }
        } else if (!strncmp(o, "accuracy=", 9)) fixed.accuracy = strtof(o + 9, NULL);
        else if (!strncmp(o, "gpx=", 4)) gpx = o + 4;
        else { fprintf(stderr, "gnss fake: unknown option %s\n", o); return -EINVAL; }
    }
    return gpx ? load_gpx(gpx) : 0;
}

int apklayer_gnss_fix(struct apklayer_gnss_fix *fix)
{
    if (!track_len) { *fix = fixed; return 0; }
    *fix = track[track_next];
    if (track_next < track_len - 1) track_next++;
    return 0;
}
