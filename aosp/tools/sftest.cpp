// sftest: shows colour bars on the display through SurfaceFlinger, the whole stack's smoke test (PLAN P7.1). It creates a layer,
// allocates GraphicBuffers (our gralloc), fills them through the mapper's CPU lock, and hands them to SurfaceFlinger with
// transactions; SurfaceFlinger composites on the GPU and our HWC presents the result to the shell. Usage: sftest [frames]
#include <gui/SurfaceComposerClient.h>
#include <gui/SurfaceControl.h>
#include <ui/GraphicBuffer.h>
#include <utils/String8.h>

#include <stdio.h>
#include <stdlib.h>

using namespace android;

extern "C" int main(int argc, char** argv) {
    const int frames = argc > 1 ? atoi(argv[1]) : 120;
    const uint32_t w = 480, h = 320;

    sp<SurfaceComposerClient> client = sp<SurfaceComposerClient>::make();
    if (client->initCheck() != NO_ERROR) { fprintf(stderr, "sftest: no SurfaceFlinger\n"); return 1; }
    sp<SurfaceControl> sc = client->createSurface(String8("apklayer-sftest"), w, h, PIXEL_FORMAT_RGBA_8888,
                                                  ISurfaceComposerClient::eFXSurfaceBufferState);
    if (sc == nullptr || !sc->isValid()) { fprintf(stderr, "sftest: createSurface failed\n"); return 1; }

    sp<GraphicBuffer> buffers[3];
    for (auto& b : buffers) {
        b = sp<GraphicBuffer>::make(w, h, PIXEL_FORMAT_RGBA_8888, 1,
                                    GraphicBuffer::USAGE_SW_WRITE_OFTEN | GraphicBuffer::USAGE_HW_TEXTURE | GraphicBuffer::USAGE_HW_COMPOSER,
                                    "apklayer-sftest");
        if (b->initCheck() != NO_ERROR) { fprintf(stderr, "sftest: GraphicBuffer allocation failed (%d)\n", b->initCheck()); return 1; }
    }
    printf("sftest: layer and buffers created (%ux%u, stride %u)\n", w, h, buffers[0]->getStride());

    for (int f = 0; f < frames; f++) {
        sp<GraphicBuffer>& b = buffers[f % 3];
        uint32_t* px = nullptr;
        if (b->lock(GraphicBuffer::USAGE_SW_WRITE_OFTEN, reinterpret_cast<void**>(&px)) != NO_ERROR) { fprintf(stderr, "sftest: lock failed\n"); return 1; }
        for (uint32_t y = 0; y < h; y++)
            for (uint32_t x = 0; x < w; x++) {
                uint32_t bar = ((x + f * 4) / 60) % 8;  // moving colour bars
                static const uint32_t colors[8] = {0xff0000ff, 0xff00ff00, 0xffff0000, 0xff00ffff, 0xffff00ff, 0xffffff00, 0xffffffff, 0xff808080};
                px[y * b->getStride() + x] = colors[bar];  // RGBA in memory order: 0xAABBGGRR
            }
        b->unlock();
        SurfaceComposerClient::Transaction t;
        t.setLayer(sc, 0x7fffffff).setBuffer(sc, b).show(sc);
        t.apply(/*synchronous=*/true);
    }
    printf("sftest: %d frames submitted\n", frames);
    return 0;
}
