/* Direct Codec2 integration probe, fed a real VP8 IVF fixture. No simulated
 * decoder or allocator: BLOB input and flexible-YUV output use our gralloc. */
#pragma once
#include "HostDecoder.h"
#include <C2AllocatorBlob.h>
#include <C2BufferPriv.h>
#include <C2Config.h>
#include <codec2/hidl/client.h>
#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <cstring>
#include <mutex>

namespace apklayer {
class ProbeListener : public C2Component::Listener, public android::Codec2Client::Listener {
public:
    std::mutex mutex;
    std::condition_variable ready;
    std::list<std::unique_ptr<C2Work>> done;
    uint32_t error = 0;
    void onWorkDone_nb(std::weak_ptr<C2Component>, std::list<std::unique_ptr<C2Work>> work) override {
        std::lock_guard lock(mutex); done.splice(done.end(), work); ready.notify_one();
    }
    void onWorkDone(const std::weak_ptr<android::Codec2Client::Component>&,
                    std::list<std::unique_ptr<C2Work>>& work) override {
        std::lock_guard lock(mutex); done.splice(done.end(), work); ready.notify_one();
    }
    void onTripped(const std::weak_ptr<android::Codec2Client::Component>&,
                   const std::vector<std::shared_ptr<C2SettingResult>>&) override {}
    void onError(const std::weak_ptr<android::Codec2Client::Component>&, uint32_t code) override {
        std::lock_guard lock(mutex); error = code; ready.notify_one();
    }
    void onDeath(const std::weak_ptr<android::Codec2Client::Component>& comp) override {
        onError(comp, C2_CORRUPTED);
    }
    void onInputBufferDone(uint64_t, size_t) override {}
    void onFrameRendered(uint64_t, int32_t, int64_t) override {}
    void onTripped_nb(std::weak_ptr<C2Component>, std::vector<std::shared_ptr<C2SettingResult>>) override {}
    void onError_nb(std::weak_ptr<C2Component>, uint32_t code) override {
        std::lock_guard lock(mutex); error = code; ready.notify_one();
    }
};
inline uint64_t readLE(const uint8_t *p, unsigned n) {
    uint64_t value = 0;
    for (unsigned i = 0; i < n; i++) value |= uint64_t(p[i]) << (8 * i);
    return value;
}
inline int decodeProbe(const char *path, bool remote = false, bool hideFirst = false) {
    std::unique_ptr<FILE, decltype(&fclose)> file(fopen(path, "rb"), fclose);
    uint8_t header[32];
    if (!file || fread(header, 1, sizeof header, file.get()) != sizeof header ||
        memcmp(header, "DKIF", 4) || memcmp(header + 8, "VP80", 4)) return 1;
    auto listener = std::make_shared<ProbeListener>();
    std::shared_ptr<C2Component> codec;
    std::shared_ptr<android::Codec2Client::Component> remoteCodec;
    std::shared_ptr<android::Codec2Client> client;
    if (remote) {
        client = android::Codec2Client::CreateFromService("apklayer");
        if (!client || client->createComponent(kDecoderName, listener, &remoteCodec) != C2_OK) return 1;
    } else {
        if (makeStore()->createComponent(kDecoderName, &codec) != C2_OK ||
            codec->setListener_vb(listener, C2_MAY_BLOCK) != C2_OK) return 1;
    }
    unsigned width = readLE(header + 12, 2), height = readLE(header + 14, 2);
    unsigned rate = readLE(header + 16, 4), scale = readLE(header + 20, 4);
    unsigned count = readLE(header + 24, 4);
    if (!rate || !scale || count != 3) return 1;
    C2StreamPictureSizeInfo::output size(0u, width, height);
    std::vector<std::unique_ptr<C2SettingResult>> failures;
    auto config = remote ? remoteCodec->config({&size}, C2_MAY_BLOCK, &failures)
                         : codec->intf()->config_vb({&size}, C2_MAY_BLOCK, &failures);
    if (config != C2_OK) return 1;
    if ((remote ? remoteCodec->start() : codec->start()) != C2_OK) return 1;
    struct Stop {
        std::shared_ptr<C2Component> codec;
        std::shared_ptr<android::Codec2Client::Component> remoteCodec;
        ~Stop() {
            if (codec) { codec->stop(); codec->release(); }
            if (remoteCodec) { remoteCodec->stop(); remoteCodec->release(); }
        }
    } cleanup{codec, remoteCodec};
    auto allocator = std::make_shared<android::C2AllocatorBlob>(C2AllocatorStore::PLATFORM_START + 10);
    C2BasicLinearBlockPool inputPool(allocator);
    for (unsigned index = 0; index < count; index++) {
        uint8_t packet[12];
        if (fread(packet, 1, sizeof packet, file.get()) != sizeof packet) return 1;
        unsigned bytes = readLE(packet, 4);
        if (!bytes || bytes > 8 * 1024 * 1024) return 1;
        std::shared_ptr<C2LinearBlock> block;
        auto status = inputPool.fetchLinearBlock(bytes,
            {C2MemoryUsage::CPU_READ, C2MemoryUsage::CPU_WRITE}, &block);
        if (status != C2_OK) { fprintf(stderr, "codec2: input allocation failed %d\n", status); return 1; }
        {
            C2WriteView write = block->map().get();
            if (write.error() || fread(write.data(), 1, bytes, file.get()) != bytes) return 1;
            if (hideFirst && index == 0) write.data()[0] &= ~0x10;
        }
        std::list<std::unique_ptr<C2Work>> queue;
        auto work = std::make_unique<C2Work>();
        work->input.ordinal.frameIndex = index;
        work->input.ordinal.timestamp = readLE(packet + 4, 8) * scale * 1000000 / rate;
        work->input.flags = index + 1 == count ? C2FrameData::FLAG_END_OF_STREAM : C2FrameData::flags_t(0);
        work->input.buffers.push_back(C2Buffer::CreateLinearBuffer(block->share(0, bytes, C2Fence())));
        work->worklets.emplace_back(new C2Worklet);
        queue.push_back(std::move(work));
        if ((remote ? remoteCodec->queue(&queue) : codec->queue_nb(&queue)) != C2_OK) return 1;
        std::unique_lock lock(listener->mutex);
        if (!listener->ready.wait_for(lock, std::chrono::seconds(2), [&] { return listener->error || !listener->done.empty(); })) return 1;
        if (listener->error) return 1;
        auto output = std::move(listener->done.front()); listener->done.pop_front(); lock.unlock();
        if (output->result != C2_OK || output->workletsProcessed != 1) {
            fprintf(stderr, "codec2: work %u result %d processed %u\n", index, output->result, output->workletsProcessed); return 1;
        }
        const auto& result = output->worklets.front()->output;
        if (result.ordinal.frameIndex.peeku() != index ||
            result.ordinal.timestamp.peekll() != int64_t(readLE(packet + 4, 8) * scale * 1000000 / rate) ||
            bool(result.flags & C2FrameData::FLAG_END_OF_STREAM) != (index + 1 == count)) return 1;
        const auto& buffers = result.buffers;
        if (hideFirst && index == 0) {
            if (!buffers.empty()) return 1;
            fprintf(stderr, "codec2: PASS %shidden work 0 completed without output\n", remote ? "AIDL " : "");
            continue;
        }
        if (buffers.size() != 1 || buffers[0]->data().graphicBlocks().size() != 1) return 1;
        C2GraphicView view = buffers[0]->data().graphicBlocks()[0].map().get();
        if (view.error() || view.width() < width || view.height() < height) return 1;
        unsigned y = view.data()[C2PlanarLayout::PLANE_Y][0];
        unsigned u = view.data()[C2PlanarLayout::PLANE_U][0];
        unsigned v = view.data()[C2PlanarLayout::PLANE_V][0];
        if (y < 50 || y > 110 || u > 130 || v < 180) return 1;
        fprintf(stderr, "codec2: PASS %swork %u gralloc %ux%u YUV=%u,%u,%u\n", remote ? "AIDL " : "", index, view.width(), view.height(), y, u, v);
    }
    return 0;
}
}
