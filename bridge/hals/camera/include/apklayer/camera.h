/* apklayer camera plugin API: what the Android-side camera provider (aosp/hals/camera, Goldfish's AIDL ICameraProvider with cameras fed by a
 * plugin) calls. A plugin is a library libapklayer-camera-<backend>.so exporting these (one per provider process, chosen per instance in
 * providers.toml; common rules, probe, options, init and the ABI version: apklayer/plugin.h; docs/PLUGINS.md). In tree: fake (test pattern,
 * image, video file), pipewire (the desktop's cameras). Plain C, no Android types; frames are handed over in a raw format with their capture
 * time and converted by the provider to what the app's streams need.
 * Threads: list is called once, on the provider's main thread, right after init. open, start, stop, format and close come from the
 * provider's binder threads, one call at a time per camera (different cameras may be called at the same time). The frame and error callbacks
 * run on the plugin's own capture thread. */
#ifndef APKLAYER_CAMERA_H
#define APKLAYER_CAMERA_H

#include <stddef.h>
#include <stdint.h>

#include <apklayer/plugin.h>

#ifdef __cplusplus
extern "C" {
#endif
#pragma GCC visibility push(default)

#define APKLAYER_CAMERA_MAX_PLANES 4
#define APKLAYER_CAMERA_MAX_SIZES 8

/* DRM fourcc codes of the raw formats a backend can deliver (the provider converts them to what the app's streams need). */
enum {
    APKLAYER_CAM_I420 = 0x32315559,  /* 'YU12': Y, U, V planes */
    APKLAYER_CAM_NV12 = 0x3231564e,  /* 'NV12': Y plane, interleaved UV */
    APKLAYER_CAM_YUYV = 0x56595559,  /* 'YUYV' */
    APKLAYER_CAM_XRGB = 0x34325258,  /* 'XR24' (B,G,R,X in memory) */
    APKLAYER_CAM_MJPEG = 0x47504a4d, /* 'MJPG': one JPEG per frame, data[0] and size[0] (stride 0); given only when the provider asks for it */
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

int apklayer_camera_probe(void);
extern const char apklayer_camera_options[];  /* optional */
int apklayer_camera_init(int count, const char *const *options);
/* writes the cameras into out (the caller's array of max entries); returns how many (0..max), or -errno */
int apklayer_camera_list(struct apklayer_camera_info *out, int max);

struct apklayer_camera;  /* the plugin's own, opaque to the provider */

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
    void (*error)(void *user, int error);   /* -errno: the stream failed or the camera went away (the provider then stops or closes it) */
};

/* opens camera `id` (an id of the list) without streaming; *callbacks is copied. Returns the plugin's handle, or NULL (id unknown, busy). */
struct apklayer_camera *apklayer_camera_open(const char *id, const struct apklayer_camera_callbacks *callbacks);
/* stops a running stream (as apklayer_camera_stop) and frees the handle; NULL is allowed. No callback runs once it returned. */
void apklayer_camera_close(struct apklayer_camera *camera);

/* Streams about `width`x`height` at about `fps` in the first of `fourccs` (0-terminated, preference order) the camera can give; a running
 * stream is restarted. Blocks until the format is negotiated (frames may already arrive before it returns); returns 0 or -errno. Actual
 * values: apklayer_camera_format(). */
int apklayer_camera_start(struct apklayer_camera *camera, uint32_t width, uint32_t height, uint32_t fps, const uint32_t *fourccs);
/* stops the stream (no-op when stopped); no frame callback runs once it returned. 0 or -errno */
int apklayer_camera_stop(struct apklayer_camera *camera);
/* the negotiated format of the last successful start, into the caller's variables */
void apklayer_camera_format(const struct apklayer_camera *camera, uint32_t *fourcc, uint32_t *width, uint32_t *height, uint32_t *fps);

#pragma GCC visibility pop
#ifdef __cplusplus
}
#endif
#endif
