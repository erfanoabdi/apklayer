/* Codec2 plumbing follows AOSP C2SoftVpxDec and SimpleC2Component (Apache-2.0).
 * apklayer patch: compressed access units go to the host decoder; software YUV
 * output is copied into actual C2 gralloc blocks with the mapper's layout. */
#include "HostDecoder.h"
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
class DecoderParams : public SimpleInterface<void>::BaseParams {
public:
    DecoderParams(const std::shared_ptr<C2ReflectorHelper>& helper, const DecoderInfo& info)
        : BaseParams(helper, info.name, C2Component::KIND_DECODER,
                     C2Component::DOMAIN_VIDEO, info.mime), mInfo(info) {
        noPrivateBuffers(); noInputReferences(); noOutputReferences();
        noInputLatency(); noTimeStretch(); setDerivedInstance(this);
        addParameter(DefineParam(mSize, C2_PARAMKEY_PICTURE_SIZE)
            .withDefault(new C2StreamPictureSizeInfo::output(0u, 320, 240))
            .withFields({C2F(mSize, width).inRange(2, 4096), C2F(mSize, height).inRange(2, 4096)})
            .withSetter(SizeSetter).build());
        if (info.vp9) {
            // AOSP C2SoftVpxDec profile/level vocabulary; 8-bit 4:2:0 only.
            addParameter(DefineParam(mProfile, C2_PARAMKEY_PROFILE_LEVEL)
                .withDefault(new C2StreamProfileLevelInfo::input(0u, C2Config::PROFILE_VP9_0, C2Config::LEVEL_VP9_5))
                .withFields({C2F(mProfile, profile).equalTo(C2Config::PROFILE_VP9_0),
                    C2F(mProfile, level).oneOf({C2Config::LEVEL_VP9_1, C2Config::LEVEL_VP9_1_1,
                        C2Config::LEVEL_VP9_2, C2Config::LEVEL_VP9_2_1, C2Config::LEVEL_VP9_3,
                        C2Config::LEVEL_VP9_3_1, C2Config::LEVEL_VP9_4, C2Config::LEVEL_VP9_4_1,
                        C2Config::LEVEL_VP9_5})})
                .withSetter(ProfileSetter).build());
        } else {
            addParameter(DefineParam(mProfile, C2_PARAMKEY_PROFILE_LEVEL)
                .withDefault(new C2StreamProfileLevelInfo::input(0u, C2Config::PROFILE_VP8_0, C2Config::LEVEL_UNUSED))
                .withFields({C2F(mProfile, profile).equalTo(C2Config::PROFILE_VP8_0),
                             C2F(mProfile, level).equalTo(C2Config::LEVEL_UNUSED)})
                .withSetter(ProfileSetter).build());
        }
        addParameter(DefineParam(mMaxInput, C2_PARAMKEY_INPUT_MAX_BUFFER_SIZE)
            .withConstValue(new C2StreamMaxBufferSizeInfo::input(0u, 8 * 1024 * 1024)).build());
        addParameter(DefineParam(mPixelFormat, C2_PARAMKEY_PIXEL_FORMAT)
            .withConstValue(new C2StreamPixelFormatInfo::output(0u, HAL_PIXEL_FORMAT_YCBCR_420_888)).build());
    }
    const DecoderInfo& info() const { return mInfo; }
    uint32_t width() const { return mSize->width; }
    uint32_t height() const { return mSize->height; }
private:
    static C2R SizeSetter(bool, C2P<C2StreamPictureSizeInfo::output>& me) {
        C2R result = me.F(me.v.width).validatePossible(me.v.width);
        return result.plus(me.F(me.v.height).validatePossible(me.v.height));
    }
    static C2R ProfileSetter(bool, C2P<C2StreamProfileLevelInfo::input>& me) {
        C2R result = me.F(me.v.profile).validatePossible(me.v.profile);
        return result.plus(me.F(me.v.level).validatePossible(me.v.level));
    }
    const DecoderInfo& mInfo;
    std::shared_ptr<C2StreamPictureSizeInfo::output> mSize;
    std::shared_ptr<C2StreamProfileLevelInfo::input> mProfile;
    std::shared_ptr<C2StreamMaxBufferSizeInfo::input> mMaxInput;
    std::shared_ptr<C2StreamPixelFormatInfo::output> mPixelFormat;
};

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
            if (view.capacity() < (mParams->info().vp9 ? 1u : 3u)) { work->result = C2_BAD_VALUE; return; }
            // apklayer patch: libvpx uncompressed headers (vp8 frame tag;
            // vp9 read_uncompressed_header profile 0). Hidden references produce no output.
            VpxPacket packets[8] = {{0, view.capacity()}};
            unsigned packetCount = 1;
            if (mParams->info().vp9) {
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
        apklayer_codec_frame_info info;
        apklayer_codec_frame_describe(raw, &info);
        const void *planes[4];
        if (strcmp(info.format, "I420") || apklayer_codec_frame_map(raw, planes)) {
            work->result = C2_CANNOT_DO; return;
        }
        std::shared_ptr<C2GraphicBlock> block;
        work->result = pool->fetchGraphicBlock((info.width + 15) & ~15u, (info.height + 1) & ~1u,
            HAL_PIXEL_FORMAT_YCBCR_420_888, {C2MemoryUsage::CPU_READ, C2MemoryUsage::CPU_WRITE}, &block);
        if (work->result != C2_OK) { fprintf(stderr, "codec2: fetch graphic block %d\n", work->result); return; }
        { // Publish CPU plane writes before notifying any cloned output work.
            C2GraphicView target = block->map().get();
            if (target.error()) { fprintf(stderr, "codec2: output map %d\n", target.error()); work->result = target.error(); return; }
            auto layout = target.layout();
            for (unsigned p = 0; p < 3; p++) {
                unsigned width = p ? (info.width + 1) / 2 : info.width;
                unsigned height = p ? (info.height + 1) / 2 : info.height;
                const auto& plane = layout.planes[p];
                for (unsigned y = 0; y < height; y++) {
                    auto *dst = target.data()[p] + y * plane.rowInc;
                    auto *src = static_cast<const uint8_t *>(planes[p]) + y * info.stride[p];
                    for (unsigned x = 0; x < width; x++) dst[x * plane.colInc] = src[x];
                }
            }
        }
        C2StreamPictureSizeInfo::output size(0u, info.width, info.height);
        bool changed = info.width != mParams->width() || info.height != mParams->height();
        if (changed) {
            std::vector<std::unique_ptr<C2SettingResult>> failures;
            work->result = mParams->config({&size}, C2_MAY_BLOCK, &failures);
            if (work->result != C2_OK) return;
        }
        auto buffer = createGraphicBuffer(block, C2Rect(info.width, info.height));
        auto fill = [buffer, width = info.width, height = info.height, changed, last](const std::unique_ptr<C2Work>& target) {
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
    return std::make_shared<HostDecoder>(id, std::make_shared<DecoderParams>(helper, info));
}
std::shared_ptr<C2ComponentInterface> makeDecoderInterface(c2_node_id_t id, const std::shared_ptr<C2ReflectorHelper>& helper, const DecoderInfo& info) {
    return std::make_shared<SimpleInterface<DecoderParams>>(info.name, id, std::make_shared<DecoderParams>(helper, info));
}
}
