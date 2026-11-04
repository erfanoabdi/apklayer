/* Between the PipeWire camera backend's listing (pipewire-list.c) and its streams (pipewire.c). */
#ifndef APKLAYER_CAMERA_PIPEWIRE_H
#define APKLAYER_CAMERA_PIPEWIRE_H

#include <stdint.h>

/* the APKLAYER_CAM_* fourcc of a SPA raw video format the provider takes, 0 for another */
uint32_t pw_camera_fourcc(uint32_t spa_video_format);

#endif
