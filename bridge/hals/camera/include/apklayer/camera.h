/* apklayer camera HAL, host side: what the Android-side camera provider (aosp/hals/camera, Goldfish's AIDL ICameraProvider with a camera
 * fed by a backend) calls. A backend is a library libapklayer-camera-<backend>.so exporting these (one per provider process, chosen per
 * instance in providers.toml, docs/PLAN.md "Plan: hardware providers"): fake (test pattern, image, video file), pipewire (the desktop's
 * cameras through PipeWire). Plain C, no Android types; frames are handed over in a raw format with their capture time. */
#ifndef APKLAYER_CAMERA_H
#define APKLAYER_CAMERA_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define APKLAYER_CAMERA_MAX_PLANES 4
#define APKLAYER_CAMERA_MAX_SIZES 8

/* DRM fourcc codes of the raw formats a backend can deliver (the provider converts them to what the app's streams need). */
enum {
    APKLAYER_CAM_I420 = 0x32315559,  /* 'YU12': Y, U, V planes */
    APKLAYER_CAM_NV12 = 0x3231564e,  /* 'NV12': Y plane, interleaved UV */
    APKLAYER_CAM_YUYV = 0x56595559,  /* 'YUYV' */
    APKLAYER_CAM_XRGB = 0x34325258,  /* 'XR24' (B,G,R,X in memory) */
};

/* Android's LENS_FACING values */
enum { APKLAYER_CAM_FRONT = 0, APKLAYER_CAM_BACK = 1, APKLAYER_CAM_EXTERNAL = 2 };

struct apklayer_camera_info {
    char id[64];            /* stable id of the camera for this host session */
    char name[128];         /* human name ("MacBook Pro Camera") */
    int facing;             /* APKLAYER_CAM_FRONT/BACK/EXTERNAL */
    int orientation;        /* Android's sensor orientation (0, 90, 180, 270): relative to the instance's portrait-natural display, so a camera whose
                               picture is upright on a landscape desktop (a webcam, a phone's sensor) is 90, a front one 270 */
    int max_fps;
    int num_sizes;          /* 1..APKLAYER_CAMERA_MAX_SIZES */
    uint32_t sizes[APKLAYER_CAMERA_MAX_SIZES][2];   /* width, height the camera can stream */
};

/* <0: not usable on this host; 0: usable but never chosen by default (fake); >0: quality, the highest is the default */
int apklayer_camera_probe(void);
/* optional (the manager's fields, `apklayer providers`): the options init takes, a line "key\tdefault\tkind" each, kind text or file */
extern const char apklayer_camera_options[];
/* the backend's options ("key=value" each); 0 or -errno */
int apklayer_camera_init(int count, const char *const *options);
/* the cameras, at most `max`; returns how many, or -errno */
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

/* Streams about `width`x`height` at about `fps` in the first of `fourccs` (0-terminated, preference order) the camera can give.
 * Blocks until the format is negotiated; returns 0 or -errno. Actual values: apklayer_camera_format(). */
int apklayer_camera_start(struct apklayer_camera *camera, uint32_t width, uint32_t height, uint32_t fps, const uint32_t *fourccs);
int apklayer_camera_stop(struct apklayer_camera *camera);
void apklayer_camera_format(const struct apklayer_camera *camera, uint32_t *fourcc, uint32_t *width, uint32_t *height, uint32_t *fps);

#ifdef __cplusplus
}
#endif
#endif
