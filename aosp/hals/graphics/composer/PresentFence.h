// Present fences that are real sync_files. SurfaceFlinger (and through it every app's frame timing) reads them with SYNC_IOC_FILE_INFO; an
// eventfd made it log "sync_file_info returned NULL" at every frame. The frame counts as presented when the shell takes it (the composer cannot
// make a kernel fence that signals later, at the compositor's real present: sw_sync needs debugfs and root), so each fence is one that signals
// at that hand-over, in this order:
//  1. a DRM syncobj, signaled anew at each present (DRM_IOCTL_SYNCOBJ_SIGNAL gives it a fresh stub fence stamped with the current time) and
//     exported as a sync_file. Exporting the syncobj created signaled again and again gave every fence the composer's start time: SurfaceFlinger's
//     frame timeline found each present before its frame's start and dropped it ("Unknown jank", present time = end time).
//  2. no render node (the Halium phones): a GPU fence through EGL (GpuFence.h; Mali there), made at the hand-over.
//  3. neither: an eventfd (readable; not a sync_file, SurfaceFlinger logs it).
// Most presents of the primary display have nothing to show (the apps' windows are virtual displays, vdisplay), so the fence cannot come from the
// composition's own GPU fence.
#pragma once

#include "../../HostEnvironment.h"
#include <dirent.h>
#include <fcntl.h>
#include <sys/eventfd.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <cstdlib>
#include <cstring>
#include <string>

#include <drm.h>
#include <log/log.h>

#include "GpuFence.h"

namespace apklayer::composer {

class PresentFences {
  public:
    PresentFences() {
        const char* only = apklayer::hostGetenv("APKLAYER_PRESENT_FENCE");  // "gpu": no syncobj (tests the path of hosts without a render node)
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
        int fd = mGpu.get();
        if (fd >= 0) return fd;
        if (!mToldEventfd) {
            mToldEventfd = true;
            ALOGW("present fences: neither a render node nor EGL native fences: eventfds (SurfaceFlinger logs each)");
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
    GpuFence mGpu;  // used only without a render node
    bool mToldEventfd = false;
};

}  // namespace apklayer::composer
