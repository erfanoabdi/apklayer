// Present fences that are real sync_files. SurfaceFlinger (and through it every app's frame timing) reads them with SYNC_IOC_FILE_INFO; an
// eventfd made it log "sync_file_info returned NULL" at every frame. A DRM syncobj created signaled, exported as a sync_file, is one (the
// fence is already signaled: the frame is shown as soon as the compositor takes it). Needs a render node; without, an eventfd (not a sync_file).
#pragma once

#include <dirent.h>
#include <fcntl.h>
#include <sys/eventfd.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <cstring>
#include <string>

#include <drm.h>

namespace apklayer::composer {

class SignaledFence {
  public:
    SignaledFence() {
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
    ~SignaledFence() { if (mDrm >= 0) close(mDrm); }

    // a new fd every call, readable (signaled)
    int get() const {
        if (mDrm >= 0) {
            int fd = exportFd(mDrm, mHandle);
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
};

}  // namespace apklayer::composer
