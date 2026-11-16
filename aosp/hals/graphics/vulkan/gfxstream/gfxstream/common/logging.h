// apklayer shim for gfxstream's logging: Android's log (the HAL) or stderr (the host test). Like gfxstream's GfxstreamLog the
// function has no format attribute (upstream passes VkDeviceSize to %d in a fatal message).
#pragma once
#include <cstdarg>
#include <cstdlib>
#ifdef __ANDROID__
#include <log/log.h>
#else
#include <cstdio>
#endif

inline void apklayerGfxstreamLog(int level, const char* format, ...) {
    va_list args;
    va_start(args, format);
#ifdef __ANDROID__
    static const int priority[] = {ANDROID_LOG_ERROR, ANDROID_LOG_WARN, ANDROID_LOG_INFO};
    __android_log_vprint(priority[level], "apklayer.vulkan", format, args);
#else
    static const char* const name[] = {"E", "W", "I"};
    fprintf(stderr, "texture emulation %s: ", name[level]);
    vfprintf(stderr, format, args);
    fputc('\n', stderr);
#endif
    va_end(args);
}
#define GFXSTREAM_FATAL(...) (apklayerGfxstreamLog(0, __VA_ARGS__), abort())
#define GFXSTREAM_ERROR(...) apklayerGfxstreamLog(0, __VA_ARGS__)
#define GFXSTREAM_WARNING(...) apklayerGfxstreamLog(1, __VA_ARGS__)
#define GFXSTREAM_INFO(...) apklayerGfxstreamLog(2, __VA_ARGS__)
#define GFXSTREAM_VERBOSE(...) ((void)0)
