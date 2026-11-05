// Present fences that are real sync_files. SurfaceFlinger (and through it every app's frame timing) reads them with SYNC_IOC_FILE_INFO; an
// eventfd made it log "sync_file_info returned NULL" at every frame. The frame counts as presented when the shell takes it (the composer cannot
// make a kernel fence that signals later, at the compositor's real present: sw_sync needs debugfs and root), so each fence is one that signals
// at that hand-over, in this order:
//  1. a DRM syncobj, signaled anew at each present (DRM_IOCTL_SYNCOBJ_SIGNAL gives it a fresh stub fence stamped with the current time) and
//     exported as a sync_file. Exporting the syncobj created signaled again and again gave every fence the composer's start time: SurfaceFlinger's
//     frame timeline found each present before its frame's start and dropped it ("Unknown jank", present time = end time).
//  2. no render node (the Halium phones): the GPU fence of SurfaceFlinger's composition (the client target's acquire fence, a real sync_file of
//     the device GPU), which the composer has waited for before the hand-over.
//  3. neither: an eventfd (readable; not a sync_file, SurfaceFlinger logs it).
#pragma once

#include <dirent.h>
#include <fcntl.h>
#include <linux/sync_file.h>
#include <sys/eventfd.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <cstdlib>
#include <cstring>
#include <string>

#include <drm.h>

namespace apklayer::composer {

// fd is a sync_file (what libsync's sync_file_info asks first)
inline bool isSyncFile(int fd) {
    sync_file_info info = {};
    return fd >= 0 && ioctl(fd, SYNC_IOC_FILE_INFO, &info) == 0;
}

class PresentFences {
  public:
    PresentFences() {
        const char* only = getenv("APKLAYER_PRESENT_FENCE");  // "gpu": no syncobj (tests the path of hosts without a render node)
        if (only && strcmp(only, "gpu") == 0) return;
        DIR* dir = opendir("/dev/dri");
        if (!dir) return;
        while (dirent* e = readdir(dir)) {
            if (strncmp(e->d_name, "renderD", 7) != 0) continue;
            const std::string path = std::string("/dev/dri/") + e->d_name;
            int fd = open(path.c_str(), O_RDWR | O_CLOEXEC);
            if (fd < 0) continue;
            drm_syncobj_create c = {};
            c.flags = DRM_SYNCOBJ_CREATE_SIGNALED;
            if (ioctl(fd, DRM_IOCTL_SYNCOBJ_CREATE, &c) == 0) {
                int probe = exportFd(fd, c.handle);
                if (probe >= 0) {
                    close(probe);
                    mDrm = fd;
                    mHandle = c.handle;
                    break;
                }
            }
            close(fd);
        }
        closedir(dir);
    }
    ~PresentFences() {
        if (mDrm >= 0) close(mDrm);
        if (mGpu >= 0) close(mGpu);
    }

    // The client target's GPU fence of this frame, once waited for (ours: closed here or kept for the fallback).
    void composed(int gpuFence) {
        if (mDrm >= 0 || !isSyncFile(gpuFence)) {
            if (gpuFence >= 0) close(gpuFence);
            return;
        }
        if (mGpu >= 0) close(mGpu);
        mGpu = gpuFence;
    }

    // A new fd, already signaled, at each present (the caller owns it).
    int presented() {
        if (mDrm >= 0) {
            drm_syncobj_array s = {};
            s.handles = reinterpret_cast<uintptr_t>(&mHandle);
            s.count_handles = 1;
            ioctl(mDrm, DRM_IOCTL_SYNCOBJ_SIGNAL, &s);  // a fresh fence: its timestamp is now
            int fd = exportFd(mDrm, mHandle);
            if (fd >= 0) return fd;
        }
        if (mGpu >= 0) {
            int fd = fcntl(mGpu, F_DUPFD_CLOEXEC, 0);
            if (fd >= 0) return fd;
        }
        return eventfd(1, EFD_CLOEXEC);
    }

  private:
    static int exportFd(int drm, uint32_t handle) {
        drm_syncobj_handle h = {};
        h.handle = handle;
        h.flags = DRM_SYNCOBJ_HANDLE_TO_FD_FLAGS_EXPORT_SYNC_FILE;
        return ioctl(drm, DRM_IOCTL_SYNCOBJ_HANDLE_TO_FD, &h) == 0 ? h.fd : -1;
    }
    int mDrm = -1;
    uint32_t mHandle = 0;
    int mGpu = -1;  // the last composition's GPU fence (only without a render node)
};

}  // namespace apklayer::composer
