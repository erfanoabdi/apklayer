/*
 * apklayer-presentd: out-of-process Wayland presenter (SurfaceFlinger-style split).
 * The app process (any JVM arch, e.g. x86_64 under Rosetta for Robolectric's native
 * graphics) writes frames into a shared file; this native process maps it and presents
 * through present.c. Layout (little-endian int32):
 *   [0] magic 'APKL' (0 = producer gone)  [1] width  [2] height  [3] frame seq
 *   [4] close requested (set here)        [5] input events written (monotonic, set here)
 *   [6..7] reserved   [8 .. 8+4*EV_RING) input ring of {type,a,b,0} (see present.c)
 *   [FB_HDR..] ARGB pixels
 * Usage: apklayer-presentd /dev/shm/apklayer-fb
 */
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#define FB_MAGIC 0x4C4B5041
#define EV_RING 64
#define FB_HDR (8 + 4 * EV_RING)

int apklayer_wayland_init(int w, int h);
int apklayer_wayland_present(const int32_t *src, int w, int h);
void apklayer_wayland_pump(int timeout_ms);
int apklayer_wayland_should_close(void);
int apklayer_wayland_next_event(int32_t ev[4]);

int main(int argc, char **argv)
{
    const char *path = argc > 1 ? argv[1] : "/dev/shm/apklayer-fb";
    volatile int32_t *fb = NULL;
    struct stat st;

    /* Wait (up to 5 min) for the producer to publish a header. */
    for (int i = 0; i < 3000 && !fb; i++) {
        int fd = open(path, O_RDWR);
        if (fd >= 0 && fstat(fd, &st) == 0 && st.st_size > FB_HDR * 4) {
            void *m = mmap(NULL, st.st_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
            if (m != MAP_FAILED && ((int32_t *)m)[0] == FB_MAGIC) fb = m;
            else if (m != MAP_FAILED) munmap(m, st.st_size);
        }
        if (fd >= 0) close(fd);
        if (!fb) usleep(100000);
    }
    if (!fb) { fprintf(stderr, "[presentd] no framebuffer at %s\n", path); return 1; }

    int w = fb[1], h = fb[2];
    if (!apklayer_wayland_init(w, h)) return 1;
    fprintf(stderr, "[presentd] mapped %s %dx%d\n", path, w, h);

    int32_t last = -1;
    while (fb[0] == FB_MAGIC) {
        int32_t seq = fb[3];
        if (seq != last) {
            apklayer_wayland_present((const int32_t *)(fb + FB_HDR), w, h);
            last = seq;
        }
        apklayer_wayland_pump(8);
        if (apklayer_wayland_should_close()) fb[4] = 1;
        int32_t ev[4];
        while (apklayer_wayland_next_event(ev)) {
            int32_t n = fb[5];
            volatile int32_t *slot = fb + 8 + 4 * (n % EV_RING);
            for (int i = 0; i < 4; i++) slot[i] = ev[i];
            __atomic_store_n(&fb[5], n + 1, __ATOMIC_RELEASE);
        }
    }
    fprintf(stderr, "[presentd] producer gone, exiting\n");
    return 0;
}
