/* liblog shim (PLAN P4.3): Android's logging API for native libraries. Built as a glibc library and
 * mapped from "liblog.so" by bionic_translation's cfg.d, so app libraries call it directly.
 * Messages go to stderr as "[native] <prio> <tag>: <text>" (logcat has no equivalent on the desktop). */
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum { ANDROID_LOG_UNKNOWN, ANDROID_LOG_DEFAULT, ANDROID_LOG_VERBOSE, ANDROID_LOG_DEBUG, ANDROID_LOG_INFO,
       ANDROID_LOG_WARN, ANDROID_LOG_ERROR, ANDROID_LOG_FATAL, ANDROID_LOG_SILENT };

static const char *prio_name(int prio)
{
    static const char *n[] = { "?", "?", "V", "D", "I", "W", "E", "F", "S" };
    return prio >= 0 && prio <= ANDROID_LOG_SILENT ? n[prio] : "?";
}

int __android_log_write(int prio, const char *tag, const char *text)
{
    if (getenv("APKLAYER_NATIVE_QUIET") && prio < ANDROID_LOG_WARN) return 0;
    fprintf(stderr, "[native] %s %s: %s\n", prio_name(prio), tag ? tag : "?", text ? text : "");
    return 1;
}

int __android_log_vprint(int prio, const char *tag, const char *fmt, va_list ap)
{
    char buf[2048];
    vsnprintf(buf, sizeof buf, fmt, ap);
    return __android_log_write(prio, tag, buf);
}

int __android_log_print(int prio, const char *tag, const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    int r = __android_log_vprint(prio, tag, fmt, ap);
    va_end(ap);
    return r;
}

int __android_log_buf_write(int bufID, int prio, const char *tag, const char *text)
{
    (void)bufID;
    return __android_log_write(prio, tag, text);
}

int __android_log_buf_print(int bufID, int prio, const char *tag, const char *fmt, ...)
{
    (void)bufID;
    va_list ap;
    va_start(ap, fmt);
    int r = __android_log_vprint(prio, tag, fmt, ap);
    va_end(ap);
    return r;
}

void __android_log_assert(const char *cond, const char *tag, const char *fmt, ...)
{
    char buf[2048];
    if (fmt) {
        va_list ap;
        va_start(ap, fmt);
        vsnprintf(buf, sizeof buf, fmt, ap);
        va_end(ap);
    } else {
        snprintf(buf, sizeof buf, "Assertion failed: %s", cond ? cond : "?");
    }
    __android_log_write(ANDROID_LOG_FATAL, tag, buf);
    abort();
}

int __android_log_is_loggable(int prio, const char *tag, int default_prio)
{
    (void)tag;
    return prio >= default_prio;
}

int __android_log_is_loggable_len(int prio, const char *tag, size_t len, int default_prio)
{
    (void)len;
    return __android_log_is_loggable(prio, tag, default_prio);
}

int __android_log_set_minimum_priority(int prio) { (void)prio; return ANDROID_LOG_VERBOSE; }
int __android_log_get_minimum_priority(void) { return ANDROID_LOG_VERBOSE; }
void __android_log_set_default_tag(const char *tag) { (void)tag; }
