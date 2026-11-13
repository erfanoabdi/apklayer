/* Preserve an explicit Android child's environment separately from btload's private loader controls. */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

int apklayer_pack_guest_environment(char *const envp[])
{
    int fd = memfd_create("android-environment", MFD_ALLOW_SEALING);
    if (fd < 0) return -1;
    if (fd < 3) {
        int moved = fcntl(fd, F_DUPFD, 3), error = errno;
        close(fd);
        if (moved < 0) { errno = error; return -1; }
        fd = moved;
    }
    for (size_t i = 0; envp && envp[i]; i++) {
        const char *p = envp[i];
        size_t remaining = strlen(p) + 1;
        while (remaining) {
            ssize_t n = write(fd, p, remaining);
            if (n < 0 && errno == EINTR) continue;
            if (n <= 0) { int e = n ? errno : EIO; close(fd); errno = e; return -1; }
            p += n;
            remaining -= n;
        }
    }
    if (fcntl(fd, F_ADD_SEALS, F_SEAL_WRITE | F_SEAL_SHRINK | F_SEAL_GROW | F_SEAL_SEAL) < 0) {
        int e = errno; close(fd); errno = e; return -1;
    }
    return fd;
}

static pthread_once_t once = PTHREAD_ONCE_INIT;
static char **child_environment;

static void unpack(void)
{
    const char *value = getenv("APKLAYER_GUEST_ENV_FD");
    if (!value) return;
    char *end;
    errno = 0;
    long number = strtol(value, &end, 10);
    struct stat st;
    if (errno || !*value || *end || number < 3 || number > INT_MAX || fstat((int)number, &st)
            || st.st_size < 0 || (uintmax_t)st.st_size >= SIZE_MAX) goto invalid;
    int fd = (int)number;
    int seals = fcntl(fd, F_GET_SEALS);
    if (seals < 0 || (seals & (F_SEAL_WRITE | F_SEAL_SHRINK | F_SEAL_GROW))
            != (F_SEAL_WRITE | F_SEAL_SHRINK | F_SEAL_GROW)) goto invalid;
    size_t size = (size_t)st.st_size;
    char *bytes = malloc(size + 1);
    if (!bytes) goto invalid;
    for (size_t offset = 0; offset < size;) {
        ssize_t n = pread(fd, bytes + offset, size - offset, (off_t)offset);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) goto invalid;
        offset += n;
    }
    close(fd);
    if (size && bytes[size - 1]) goto invalid;
    size_t count = 0;
    for (size_t i = 0; i < size; i++) count += bytes[i] == 0;
    if (count >= SIZE_MAX / sizeof(char *)) goto invalid;
    child_environment = calloc(count + 1, sizeof(char *));
    if (!child_environment) goto invalid;
    size_t index = 0;
    for (size_t i = 0; i < size; i += strlen(bytes + i) + 1) child_environment[index++] = bytes + i;
    return;
invalid:
    fputs("apklayer: invalid explicit guest environment transport\n", stderr);
    _exit(127);
}

char **apklayer_guest_exec_environment(void)
{
    pthread_once(&once, unpack);
    return child_environment;
}
