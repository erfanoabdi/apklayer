// screenshot <display service> <out.png>: what SurfaceFlinger shows on one app's virtual display, through SurfaceFlinger's own capture path
// (ScreenshotClient::captureDisplay, as frameworks/base/cmds/screencap does). The display's token is the one the sink registered
// (aosp/hals/graphics/vdisplay: "apklayer.vdisplay.<layer stack>"). screencap itself is not used: it captures physical displays only and
// encodes through libhwui/Skia; the PNG here is written with zlib (8-bit RGB, filter 0), the only part that is new.
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/SyncScreenCaptureListener.h>
#include <ui/GraphicBuffer.h>
#include <zlib.h>

#include <stdio.h>
#include <string>
#include <vector>

using namespace android;

namespace {

void chunk(FILE* f, const char* type, const std::vector<uint8_t>& data) {
    uint8_t len[4] = {uint8_t(data.size() >> 24), uint8_t(data.size() >> 16), uint8_t(data.size() >> 8), uint8_t(data.size())};
    fwrite(len, 1, 4, f);
    fwrite(type, 1, 4, f);
    fwrite(data.data(), 1, data.size(), f);
    uLong crc = crc32(crc32(0, reinterpret_cast<const Bytef*>(type), 4), data.data(), data.size());
    uint8_t c[4] = {uint8_t(crc >> 24), uint8_t(crc >> 16), uint8_t(crc >> 8), uint8_t(crc)};
    fwrite(c, 1, 4, f);
}

bool writePng(const char* path, const uint8_t* rgba, uint32_t w, uint32_t h, uint32_t stridePx) {
    std::vector<uint8_t> raw;
    raw.reserve(size_t(w * 3 + 1) * h);
    for (uint32_t y = 0; y < h; y++) {
        raw.push_back(0);  // filter: none
        const uint8_t* row = rgba + size_t(y) * stridePx * 4;
        for (uint32_t x = 0; x < w; x++) raw.insert(raw.end(), row + x * 4, row + x * 4 + 3);
    }
    uLongf zlen = compressBound(raw.size());
    std::vector<uint8_t> z(zlen);
    if (compress2(z.data(), &zlen, raw.data(), raw.size(), 6) != Z_OK) return false;
    z.resize(zlen);
    FILE* f = fopen(path, "wb");
    if (f == nullptr) { perror(path); return false; }
    fwrite("\x89PNG\r\n\x1a\n", 1, 8, f);
    chunk(f, "IHDR", {uint8_t(w >> 24), uint8_t(w >> 16), uint8_t(w >> 8), uint8_t(w), uint8_t(h >> 24), uint8_t(h >> 16), uint8_t(h >> 8), uint8_t(h), 8, 2, 0, 0, 0});
    chunk(f, "IDAT", z);
    chunk(f, "IEND", {});
    return fclose(f) == 0;
}

}  // namespace

extern "C" int main(int argc, char** argv) {
    if (argc < 3) { fprintf(stderr, "usage: screenshot <display service> <out.png>\n"); return 2; }
    ProcessState::self()->startThreadPool();
    sp<IBinder> token = defaultServiceManager()->checkService(String16(argv[1]));
    if (token == nullptr) { fprintf(stderr, "screenshot: no display %s\n", argv[1]); return 1; }
    DisplayCaptureArgs args;
    args.displayToken = token;
    sp<SyncScreenCaptureListener> listener = sp<SyncScreenCaptureListener>::make();
    if (ScreenshotClient::captureDisplay(args, listener) != NO_ERROR) { fprintf(stderr, "screenshot: captureDisplay failed\n"); return 1; }
    gui::ScreenCaptureResults r = listener->waitForResults();
    if (!r.fenceResult.ok() || r.buffer == nullptr) { fprintf(stderr, "screenshot: capture failed\n"); return 1; }
    const sp<GraphicBuffer>& b = r.buffer;
    if (b->getPixelFormat() != PIXEL_FORMAT_RGBA_8888 && b->getPixelFormat() != PIXEL_FORMAT_RGBX_8888) {
        fprintf(stderr, "screenshot: unexpected pixel format %d\n", b->getPixelFormat());
        return 1;
    }
    void* base = nullptr;
    if (b->lock(GraphicBuffer::USAGE_SW_READ_OFTEN, &base) != NO_ERROR || base == nullptr) { fprintf(stderr, "screenshot: cannot map the capture\n"); return 1; }
    bool ok = writePng(argv[2], static_cast<const uint8_t*>(base), b->getWidth(), b->getHeight(), b->getStride());
    b->unlock();
    if (ok) printf("screenshot: %ux%u %s\n", b->getWidth(), b->getHeight(), argv[2]);
    return ok ? 0 : 1;
}
