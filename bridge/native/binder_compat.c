/* Kernel binder on a host without SELinux (PLAN P7.1). Android's servicemanager registers with BINDER_SET_CONTEXT_MGR_EXT and
 * FLAT_BINDER_FLAG_TXN_SECURITY_CTX, so the kernel asks the active security module (AppArmor on Ubuntu) to turn each caller's
 * security id into a context string, which fails (-EINVAL) and every transaction to the service manager dies. libbinder falls
 * back to a plain context manager when that ioctl fails, so it is made to fail; every other ioctl goes straight to the kernel.
 * Linked into the launcher executable only (scripts/btload.c): the bionic linker resolves ioctl to the first definition in the
 * global scope, which is this one. The userspace binder (PLAN) needs none of this. */
#define _GNU_SOURCE
#include <errno.h>
#include <stdarg.h>
#include <sys/syscall.h>
#include <unistd.h>

#define BINDER_SET_CONTEXT_MGR_EXT 0x4018620dUL /* _IOW('b', 13, struct flat_binder_object) */

int ioctl(int fd, unsigned long request, ...)
{
    va_list ap;
    va_start(ap, request);
    void *arg = va_arg(ap, void *);
    va_end(ap);
    if (request == BINDER_SET_CONTEXT_MGR_EXT) {
        errno = EINVAL;
        return -1;
    }
    long r = syscall(SYS_ioctl, fd, request, arg);
    return (int)r;
}
