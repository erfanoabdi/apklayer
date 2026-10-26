/* Host display HAL test: presents real GPU buffers to apklayer-shell and checks the pacing is the compositor's: the output's
 * refresh rate arrives, vsync callbacks follow it, buffers come back released. Needs a running apklayer-shell on a Wayland
 * session with zwp_linux_dmabuf_v1. */
#include "apklayer/display.h"

#include <drm_fourcc.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

static pthread_mutex_t mu = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t cv = PTHREAD_COND_INITIALIZER;
static int vsyncs, released[8];
static int64_t last_ts, first_ts;
static uint32_t refresh;

static void on_vsync(void *u, int64_t ts) { (void)u; pthread_mutex_lock(&mu); if (!vsyncs) first_ts = ts; last_ts = ts; vsyncs++; pthread_cond_broadcast(&cv); pthread_mutex_unlock(&mu); }
static void on_refresh(void *u, uint32_t mhz) { (void)u; pthread_mutex_lock(&mu); refresh = mhz; pthread_cond_broadcast(&cv); pthread_mutex_unlock(&mu); }
static void on_released(void *u, int slot) { (void)u; pthread_mutex_lock(&mu); released[slot & 7]++; pthread_mutex_unlock(&mu); }

static int failures;
#define CHECK(cond, what) do { if (cond) printf("PASS  %s\n", what); else { printf("FAIL  %s\n", what); failures++; } } while (0)

int main(void)
{
    struct apklayer_display_callbacks cb = { NULL, on_vsync, on_refresh, on_released };
    struct apklayer_display *d = apklayer_display_open("apklayer-display-test", 480, 320, &cb);
    CHECK(d != NULL, "connects to the shell and opens a window");
    if (!d) return 1;
    struct apklayer_gfx *gfx = apklayer_gfx_open(NULL);
    struct apklayer_gfx_buffer buf[3];
    for (int i = 0; i < 3; i++)
        if (apklayer_gfx_alloc(gfx, 480, 320, DRM_FORMAT_ARGB8888, APKLAYER_GFX_SAMPLE | APKLAYER_GFX_RENDER | APKLAYER_GFX_CPU_WRITE | APKLAYER_GFX_SCANOUT, &buf[i]) != 0) { printf("FAIL  alloc\n"); return 1; }

    pthread_mutex_lock(&mu);
    while (!refresh) pthread_cond_wait(&cv, &mu); /* the shell sends the output's refresh rate right after HELLO */
    pthread_mutex_unlock(&mu);
    printf("  output refresh: %u mHz\n", refresh);
    CHECK(refresh >= 24000 && refresh <= 400000, "the output's refresh rate arrives");

    enum { FRAMES = 90 };
    for (int f = 0; f < FRAMES; f++) {
        int slot = f % 3;
        uint32_t stride; struct apklayer_gfx_mapping *m;
        uint32_t *px = apklayer_gfx_map(gfx, &buf[slot], 0, 1, &stride, &m);
        if (px) {
            for (uint32_t y = 0; y < 320; y++) for (uint32_t x = 0; x < 480; x++)
                px[y * (stride / 4) + x] = 0xff000000u | (((x + f * 6) % 480) * 255 / 480) << 16 | (y * 255 / 320) << 8 | (f * 3 & 255);
            apklayer_gfx_unmap(m);
        }
        if (apklayer_display_present(d, slot, &buf[slot], NULL) != 0) { printf("FAIL  present\n"); failures++; break; }
        pthread_mutex_lock(&mu);
        int before = vsyncs;
        apklayer_display_request_vsync(d);
        while (vsyncs == before) pthread_cond_wait(&cv, &mu); /* the compositor's frame callback paces the loop */
        pthread_mutex_unlock(&mu);
    }
    double secs = (double)(last_ts - first_ts) / 1e9;
    double fps = vsyncs > 1 ? (vsyncs - 1) / secs : 0;
    printf("  %d vsyncs over %.2f s = %.1f fps (output %.1f Hz)\n", vsyncs, secs, fps, refresh / 1000.0);
    CHECK(vsyncs >= FRAMES, "one real vsync per presented frame");
    /* The reported mode can be wrong (a virtual display may say 60 Hz while the host runs 120 Hz): the compositor's frame
     * callbacks are the truth, so only sanity is asserted and both numbers are shown. */
    CHECK(fps > 20 && fps < 500, "pacing comes from the compositor's frame callbacks (a sane rate)");
    int rel = released[0] + released[1] + released[2];
    printf("  buffers released by the compositor: %d\n", rel);
    CHECK(rel > 0, "the compositor releases presented buffers back");
    for (int i = 0; i < 3; i++) apklayer_gfx_buffer_close(&buf[i]);
    apklayer_gfx_close(gfx);
    apklayer_display_close(d);
    printf(failures ? "%d FAILED\n" : "all passed\n", failures);
    return failures != 0;
}
