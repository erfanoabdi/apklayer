/* apklayer camera HAL, host side: the host's cameras through PipeWire (the desktop's own arbiter: shared with other apps, permission
 * aware, V4L2/libcamera/portal behind it), plain C for the Android-side camera provider HAL (aosp/hals/camera). Frames are handed over
 * as the negotiated raw format with the real capture timestamp; nothing is invented (no synthetic frames, no fake clock). */
#ifndef APKLAYER_CAMERA_H
#define APKLAYER_CAMERA_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define APKLAYER_CAMERA_MAX_PLANES 4

/* DRM fourcc codes of the raw formats a camera can deliver here (what Android's YUV_420_888 / YCbCr_422_I / RGBX need). */
enum {
    APKLAYER_CAM_NV12 = 0x3231564e,  /* 'NV12' */
    APKLAYER_CAM_YUYV = 0x56595559,  /* 'YUYV' */
    APKLAYER_CAM_I420 = 0x32315559,  /* 'YU12' */
    APKLAYER_CAM_XRGB = 0x34325258,  /* 'XR24' (B,G,R,X in memory) */
};

struct apklayer_camera_info {
    char id[64];            /* stable id of the camera for this host session (the PipeWire node name) */
    char name[128];         /* human name ("MacBook Pro Camera") */
    int facing;             /* 0 front, 1 back, 2 external: from the device's location property, external when unknown */
};

/* The cameras of the host, at most `max`; returns how many (0 if none or PipeWire is not running), or -errno. */
int apklayer_camera_list(struct apklayer_camera_info *out, int max);

struct apklayer_camera;

struct apklayer_camera_frame {
    uint32_t fourcc, width, height;
    uint32_t num_planes;
    const void *data[APKLAYER_CAMERA_MAX_PLANES];   /* mapped, valid only during the callback */
    uint32_t stride[APKLAYER_CAMERA_MAX_PLANES];    /* bytes */
    uint32_t size[APKLAYER_CAMERA_MAX_PLANES];      /* bytes of the plane */
    int dmabuf_fd[APKLAYER_CAMERA_MAX_PLANES];      /* >= 0 when the buffer is a dmabuf (valid during the callback), else -1 */
    int64_t timestamp_ns;                           /* capture time on the monotonic clock */
    uint32_t sequence;
};

struct apklayer_camera_callbacks {
    void *user;
    void (*frame)(void *user, const struct apklayer_camera_frame *frame);   /* on the capture thread */
    void (*error)(void *user, int error);                                   /* stream failed or the camera went away */
};

struct apklayer_camera *apklayer_camera_open(const char *id, const struct apklayer_camera_callbacks *callbacks);
void apklayer_camera_close(struct apklayer_camera *camera);

/* Streams `width`x`height` at about `fps` in the first of `fourccs` (0-terminated, preference order) the camera can give, converting
 * where PipeWire's adapter can. Blocks until the format is negotiated; returns 0 or -errno. Actual values: apklayer_camera_format(). */
int apklayer_camera_start(struct apklayer_camera *camera, uint32_t width, uint32_t height, uint32_t fps, const uint32_t *fourccs);
int apklayer_camera_stop(struct apklayer_camera *camera);
void apklayer_camera_format(const struct apklayer_camera *camera, uint32_t *fourcc, uint32_t *width, uint32_t *height, uint32_t *fps);

#ifdef __cplusplus
}
#endif
#endif
