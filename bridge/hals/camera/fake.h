/* Between the fake camera's API (fake.c) and its file source (fake-file.c). */
#ifndef APKLAYER_CAMERA_FAKE_H
#define APKLAYER_CAMERA_FAKE_H

#include <stdint.h>

struct apklayer_camera;

const char *fake_source(void);
uint32_t fake_width(const struct apklayer_camera *c);
uint32_t fake_height(const struct apklayer_camera *c);
uint32_t fake_fps(const struct apklayer_camera *c);
int fake_running(const struct apklayer_camera *c);
/* the capture thread's start result for apklayer_camera_start: 1 streaming, -errno failed */
void fake_report_ready(struct apklayer_camera *c, int result);
void fake_deliver(struct apklayer_camera *c, const uint8_t *const planes[3], const uint32_t strides[3], uint32_t w, uint32_t h,
                  int64_t timestamp_ns, uint32_t sequence);
/* the capture thread of a file source (fake_source() is a path) */
void *fake_file_thread(void *camera);

#endif
