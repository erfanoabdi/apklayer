// apklayer shim for gfxstream's logging: Android's log (the HAL) or stderr (the host test).
#pragma once
#include <cstdlib>
#ifdef __ANDROID__
#include <log/log.h>
#define APKLAYER_GFXSTREAM_LOG(prio, fmt, ...) __android_log_print(prio, "apklayer.vulkan", "texture emulation: " fmt, ##__VA_ARGS__)
#define APKLAYER_PRIO_ERROR ANDROID_LOG_ERROR
#define APKLAYER_PRIO_WARN ANDROID_LOG_WARN
#define APKLAYER_PRIO_INFO ANDROID_LOG_INFO
#else
#include <cstdio>
#define APKLAYER_GFXSTREAM_LOG(prio, fmt, ...) fprintf(stderr, "texture emulation: " fmt "\n", ##__VA_ARGS__)
#define APKLAYER_PRIO_ERROR 0
#define APKLAYER_PRIO_WARN 0
#define APKLAYER_PRIO_INFO 0
#endif
#define GFXSTREAM_FATAL(fmt, ...) (APKLAYER_GFXSTREAM_LOG(APKLAYER_PRIO_ERROR, fmt, ##__VA_ARGS__), abort())
#define GFXSTREAM_ERROR(fmt, ...) APKLAYER_GFXSTREAM_LOG(APKLAYER_PRIO_ERROR, fmt, ##__VA_ARGS__)
#define GFXSTREAM_WARNING(fmt, ...) APKLAYER_GFXSTREAM_LOG(APKLAYER_PRIO_WARN, fmt, ##__VA_ARGS__)
#define GFXSTREAM_INFO(fmt, ...) APKLAYER_GFXSTREAM_LOG(APKLAYER_PRIO_INFO, fmt, ##__VA_ARGS__)
#define GFXSTREAM_VERBOSE(fmt, ...) ((void)0)
