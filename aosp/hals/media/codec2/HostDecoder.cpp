/* Codec2 plumbing follows AOSP C2SoftVpxDec and SimpleC2Component (Apache-2.0).
 * apklayer patch: compressed access units go to the host decoder; software YUV
 * output is copied into actual C2 gralloc blocks with the mapper's layout. */
#include "HostDecoder.h"
#include "DecoderParams.h"
#include "GraphicOutput.h"
#include "VpxHeaders.h"
#include <apklayer/codec.h>
#include <C2PlatformSupport.h>
#include <SimpleC2Component.h>
#include <SimpleC2Interface.h>
#include <system/graphics.h>
#include <cstring>
#include <cerrno>
#include <cstdio>

namespace apklayer {
using namespace android;

class HostDecoder : public SimpleC2Component {
public:
    HostDecoder(c2_node_id_t id, std::shared_ptr<DecoderParams> params)
        : SimpleC2Component(std::make_shared<SimpleInterface<DecoderParams>>(params->info().name, id, params)),
          mParams(std::move(params)) {}
    ~HostDecoder() override { onRelease(); }
private:
    std::shared_ptr<DecoderParams> mParams;
    apklayer_codec *mCodec = nullptr;
    bool mEos = false;
    c2_status_t onInit() override { return C2_OK; }
    c2_status_t onStop() override {
        apklayer_codec_close(mCodec); mCodec = nullptr; mEos = false; return C2_OK;
    }
    void onReset() override { (void)onStop(); }
    void onRelease() override { (void)onStop(); }
    c2_status_t onFlush_sm() override { return onStop(); }
    c2_status_t drain(uint32_t mode, const std::shared_ptr<C2BlockPool>&) override {
        return mode == DRAIN_CHAIN ? C2_OMITTED : C2_OK; // VP8 has no reordered frames.
    }
    void process(const std::unique_ptr<C2Work>& work, const std::shared_ptr<C2BlockPool>& pool) override {
        work->result = C2_OK;
        work->workletsProcessed = 1;
        auto& output = work->worklets.front()->output;
        output.flags = work->input.flags;
        output.ordinal = work->input.ordinal;
        output.buffers.clear();
        if (mEos) { work->result = C2_BAD_STATE; return; }
        if (work->input.flags & C2FrameData::FLAG_CODEC_CONFIG) return;
        if (!mCodec) {
            apklayer_codec_config config = {mParams->info().mime, mParams->width(), mParams->height(), nullptr, 0};
            if (apklayer_codec_open(&config, &mCodec)) { work->result = C2_CANNOT_DO; return; }
        }
        bool hasInput = !work->input.buffers.empty();
        unsigned visible = 0;
        if (hasInput) {
            const auto& blocks = work->input.buffers[0]->data().linearBlocks();
            if (blocks.empty()) { work->result = C2_BAD_VALUE; return; }
            C2ReadView view = blocks.front().map().get();
            if (view.error()) { fprintf(stderr, "codec2: input map %d\n", view.error()); work->result = view.error(); return; }
            if (view.capacity() < (mParams->info().type == DecoderInfo::VP9 ? 1u : 3u)) { work->result = C2_BAD_VALUE; return; }
            // apklayer patch: libvpx uncompressed headers (vp8 frame tag;
            // vp9 read_uncompressed_header profile 0). Hidden references produce no output.
            VpxPacket packets[8] = {{0, view.capacity()}};
            unsigned packetCount = 1;
            if (mParams->info().type == DecoderInfo::VP9) {
                int shown = vp9Packets(view.data(), view.capacity(), packets, &packetCount);
                if (shown < 0) { work->result = C2_BAD_VALUE; return; }
                visible = shown;
            } else visible = (view.data()[0] & 0x10) != 0;
            int64_t timestamp = work->input.ordinal.timestamp.peekll();
            if (timestamp < 0 || timestamp > INT64_MAX / 1000) { work->result = C2_BAD_VALUE; return; }
            // Split indexed VP9 packets so the host decoder preserves every
            // displayed picture, including multiple pictures with the same PTS.
            for (unsigned p = 0; p < packetCount; p++) {
                if (apklayer_codec_queue(mCodec, view.data() + packets[p].offset, packets[p].size, timestamp * 1000)) {
                    work->result = C2_CORRUPTED; return;
                }
            }
        }
        if (work->input.flags & C2FrameData::FLAG_END_OF_STREAM) {
            if (apklayer_codec_end(mCodec)) { work->result = C2_CORRUPTED; return; }
            mEos = true;
        }
        if (!hasInput || !visible) {
            if (mEos) {
                apklayer_codec_frame *unexpected = nullptr;
                int result = apklayer_codec_pull(mCodec, 500000000, &unexpected);
                apklayer_codec_frame_release(unexpected);
                if (result != -ENODATA) work->result = C2_CORRUPTED;
            }
            return;
        }
        for (unsigned picture = 0; picture < visible; picture++) {
            emitFrame(work, pool, picture + 1 == visible);
            if (work->result != C2_OK) return;
        }
    }
    void emitFrame(const std::unique_ptr<C2Work>& work, const std::shared_ptr<C2BlockPool>& pool, bool last) {
        apklayer_codec_frame *raw = nullptr;
        int status = apklayer_codec_pull(mCodec, 500000000, &raw);
        if (status) { work->result = status == -EAGAIN ? C2_TIMED_OUT : C2_CORRUPTED; return; }
        std::unique_ptr<apklayer_codec_frame, decltype(&apklayer_codec_frame_release)> frame(raw, apklayer_codec_frame_release);
        DecodedGraphic graphic;
        work->result = copyGraphic(raw, pool, mParams, &graphic);
        if (work->result != C2_OK) return;
        auto buffer = createGraphicBuffer(graphic.block, C2Rect(graphic.width, graphic.height));
        auto fill = [buffer, width = graphic.width, height = graphic.height, changed = graphic.changed, last](const std::unique_ptr<C2Work>& target) {
            target->result = C2_OK;
            target->workletsProcessed = 1;
            auto& out = target->worklets.front()->output;
            out.ordinal = target->input.ordinal;
            out.flags = last ? target->input.flags : C2FrameData::FLAG_INCOMPLETE;
            out.buffers.clear();
            out.buffers.push_back(buffer);
            out.configUpdate.clear();
            if (changed) {
                C2StreamPictureSizeInfo::output size(0u, width, height);
                out.configUpdate.push_back(C2Param::Copy(size));
            }
        };
        if (last) fill(work);
        else cloneAndSend(work->input.ordinal.frameIndex.peekull(), work, fill);
    }
};
std::shared_ptr<C2Component> makeDecoder(c2_node_id_t id, const std::shared_ptr<C2ReflectorHelper>& helper, const DecoderInfo& info) {
    if (info.type == DecoderInfo::AVC) return makeReorderedDecoder(id, helper, info);
    return std::make_shared<HostDecoder>(id, std::make_shared<DecoderParams>(helper, info));
}
std::shared_ptr<C2ComponentInterface> makeDecoderInterface(c2_node_id_t id, const std::shared_ptr<C2ReflectorHelper>& helper, const DecoderInfo& info) {
    return std::make_shared<SimpleInterface<DecoderParams>>(info.name, id, std::make_shared<DecoderParams>(helper, info));
}
}
