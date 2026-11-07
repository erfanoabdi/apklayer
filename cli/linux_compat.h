// Linux interfaces newer than an old build root's headers (Sailfish OS 5.0: glibc 2.30, kernel headers 3.18): the system call numbers (one
// table for every architecture since Linux 5.1) and the new mount API's flags and wrappers as glibc 2.36 has them. On a kernel without the
// call (the Volla Quintus' 4.19) it fails with ENOSYS, as it does when built against new headers.
#pragma once
#include <cstddef>
#include <cstdint>
#include <fcntl.h>
#include <sys/mount.h>
#include <sys/syscall.h>
#include <unistd.h>

#ifndef SYS_pidfd_open
#define SYS_pidfd_open 434
#endif
#ifndef AT_RECURSIVE
#define AT_RECURSIVE 0x8000
#endif
#ifndef OPEN_TREE_CLONE  // glibc before 2.36
#define OPEN_TREE_CLONE 1
#define OPEN_TREE_CLOEXEC O_CLOEXEC
#define MOVE_MOUNT_F_EMPTY_PATH 0x4
#define MOUNT_ATTR_RDONLY 0x1
#define MOUNT_ATTR_NOSUID 0x2
#define MOUNT_ATTR_NODEV 0x4
struct mount_attr { uint64_t attr_set, attr_clr, propagation, userns_fd; };
inline int open_tree(int dfd, const char* path, unsigned flags) { return static_cast<int>(syscall(428, dfd, path, flags)); }
inline int move_mount(int from, const char* fromPath, int to, const char* toPath, unsigned flags) {
    return static_cast<int>(syscall(429, from, fromPath, to, toPath, flags));
}
inline int mount_setattr(int dfd, const char* path, unsigned flags, mount_attr* attr, size_t size) {
    return static_cast<int>(syscall(442, dfd, path, flags, attr, size));
}
#endif
