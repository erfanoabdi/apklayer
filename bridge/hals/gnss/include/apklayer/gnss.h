/* apklayer GNSS HAL, host side: what the Android-side front (aosp/hals/gnss, AOSP's IGnss example with a backend's fixes) calls.
 * A backend is a library libapklayer-gnss-<backend>.so exporting these (one per front process, chosen in providers.toml). Plain C. */
#ifndef APKLAYER_GNSS_H
#define APKLAYER_GNSS_H

#ifdef __cplusplus
extern "C" {
#endif

struct apklayer_gnss_fix {
    double latitude, longitude, altitude;  /* degrees (WGS84), meters above the ellipsoid */
    float speed, bearing, accuracy;        /* m/s, degrees, horizontal meters */
};

/* <0: not usable on this host; 0: usable but never chosen by default (fake); >0: quality, the highest is the default */
int apklayer_gnss_probe(void);
/* the domain's options from providers.toml, "key=value" each; 0 or -errno */
int apklayer_gnss_init(int count, const char *const *options);
/* the next fix (the front asks once per reporting interval): 0, or -EAGAIN while there is none */
int apklayer_gnss_fix(struct apklayer_gnss_fix *fix);

#ifdef __cplusplus
}
#endif
#endif
