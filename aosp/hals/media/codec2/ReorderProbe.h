/* Real AIDL B-frame transport probe; coded input order differs from display PTS. */
#pragma once
#include "DecodeProbe.h"
#include <android/binder_manager.h>
#include <algorithm>
#include <set>
namespace apklayer {
inline int reorderProbe(const char *path) {
    if (__builtin_available(android 31, *)) {
        const char *service = "android.hardware.media.c2.IComponentStore/apklayer";
        ndk::SpAIBinder binder(AServiceManager_waitForService(service));
        if (!binder.get()) return 1;
    } else return 1;
    std::unique_ptr<FILE, decltype(&fclose)> file(fopen(path, "rb"), fclose);
    uint8_t header[12];
    if (!file || fread(header, 1, 12, file.get()) != 12 || memcmp(header, "A264", 4)) return 1;
    unsigned width = readLE(header + 4, 2), height = readLE(header + 6, 2), count = readLE(header + 8, 4);
    if (!width || !height || count != 12) return 1;
    auto client = android::Codec2Client::CreateFromService("apklayer");
    auto listener = std::make_shared<ProbeListener>();
    std::shared_ptr<android::Codec2Client::Component> codec;
    if (!client || client->createComponent("c2.apklayer.avc.decoder", listener, &codec) != C2_OK) return 1;
    C2StreamPictureSizeInfo::output size(0u, width, height);
    std::vector<std::unique_ptr<C2SettingResult>> failures;
    if (codec->config({&size}, C2_MAY_BLOCK, &failures) != C2_OK || codec->start() != C2_OK) return 1;
    struct Stop { std::shared_ptr<android::Codec2Client::Component> codec; ~Stop() { codec->stop(); codec->release(); } } stop{codec};
    auto allocator = std::make_shared<android::C2AllocatorBlob>(C2AllocatorStore::PLATFORM_START + 10);
    C2BasicLinearBlockPool pool(allocator);
    std::vector<int64_t> pts;
    std::list<std::unique_ptr<C2Work>> queue;
    for (unsigned index = 0; index < count; index++) {
        uint8_t packet[12];
        if (fread(packet, 1, 12, file.get()) != 12) return 1;
        unsigned bytes = readLE(packet, 4);
        if (!bytes || bytes > 8 * 1024 * 1024) return 1;
        pts.push_back(readLE(packet + 4, 8));
        std::shared_ptr<C2LinearBlock> block;
        if (pool.fetchLinearBlock(bytes, {C2MemoryUsage::CPU_READ, C2MemoryUsage::CPU_WRITE}, &block) != C2_OK) return 1;
        {
            C2WriteView view = block->map().get();
            if (view.error() || fread(view.data(), 1, bytes, file.get()) != bytes) return 1;
        }
        auto work = std::make_unique<C2Work>();
        work->input.ordinal.frameIndex = index; work->input.ordinal.timestamp = pts.back();
        work->input.flags = index + 1 == count ? C2FrameData::FLAG_END_OF_STREAM : C2FrameData::flags_t(0);
        work->input.buffers.push_back(C2Buffer::CreateLinearBuffer(block->share(0, bytes, C2Fence())));
        work->worklets.emplace_back(new C2Worklet);
        queue.push_back(std::move(work));
    }
    if (std::is_sorted(pts.begin(), pts.end()) || codec->queue(&queue) != C2_OK) return 1;
    std::vector<int64_t> expected = pts;
    std::sort(expected.begin(), expected.end());
    unsigned pictures = 0;
    bool eos = false;
    std::set<uint64_t> seen;
    while (!eos) {
        std::unique_lock lock(listener->mutex);
        if (!listener->ready.wait_for(lock, std::chrono::seconds(2), [&] { return listener->error || !listener->done.empty(); }) || listener->error) return 1;
        auto work = std::move(listener->done.front()); listener->done.pop_front(); lock.unlock();
        if (work->result != C2_OK || work->workletsProcessed != 1) {
            fprintf(stderr, "codec2: reordered result=%d processed=%u\n", work->result, work->workletsProcessed); return 1;
        }
        const auto& out = work->worklets.front()->output;
        uint64_t index = out.ordinal.frameIndex.peekull();
        if (index >= count || out.ordinal.timestamp.peekll() != pts[index]) return 1;
        eos = out.flags & C2FrameData::FLAG_END_OF_STREAM;
        if (eos) {
            if (pictures != count || index + 1 != count || !out.buffers.empty()) return 1;
            fprintf(stderr, "codec2: PASS AIDL reordered EOS after %u pictures\n", pictures);
            continue;
        }
        if (pictures >= count || out.ordinal.timestamp.peekll() != expected[pictures] || !seen.insert(index).second || out.buffers.size() != 1) return 1;
        if (bool(out.flags & C2FrameData::FLAG_INCOMPLETE) != (index + 1 == count)) return 1;
        if (out.buffers[0]->data().graphicBlocks().size() != 1) return 1;
        C2GraphicView view = out.buffers[0]->data().graphicBlocks()[0].map().get();
        if (view.error()) return 1;
        unsigned y = view.data()[C2PlanarLayout::PLANE_Y][0], u = view.data()[C2PlanarLayout::PLANE_U][0], v = view.data()[C2PlanarLayout::PLANE_V][0];
        if (y < 50 || y > 110 || u > 130 || v < 180) return 1;
        fprintf(stderr, "codec2: PASS AIDL reordered picture %u input=%llu pts=%lld YUV=%u,%u,%u\n",
            pictures++, (unsigned long long)index, (long long)out.ordinal.timestamp.peekll(), y, u, v);
    }
    return 0;
}
}
