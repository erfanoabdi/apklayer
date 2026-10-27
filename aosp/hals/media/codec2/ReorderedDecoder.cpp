/* apklayer patch: use SimpleC2Component pending work and its existing handler.
 * Streaming notifications enqueue drain work; no worker races pending insertion. */
#include "DecoderParams.h"
#include "GraphicOutput.h"
#include <SimpleC2Component.h>
#include <cerrno>
#include <deque>
#include <map>
#include <chrono>
#include <optional>
#include <vector>

namespace apklayer {
using namespace android;
class ReorderedDecoder final : public SimpleC2Component {
    struct Pending { C2WorkOrdinalStruct ordinal; C2FrameData::flags_t flags; };
    std::shared_ptr<DecoderParams> mParams;
    apklayer_codec *mCodec = nullptr;
    std::map<uint64_t, Pending> mPending;
    std::map<int64_t, std::deque<uint64_t>> mByPts;
    std::optional<uint64_t> mEosIndex;
    bool mEosQueued = false;
    // The codec-config buffers (H.264 SPS/PPS) of the stream: a real decoder keeps them across flush and ExoPlayer does not send them again
    // after a seek, so a pipeline reopened by flush must be fed them first.
    std::vector<uint8_t> mConfig;
    int64_t mConfigPts = 0;
    uint64_t mFrames = 0;
    int64_t mCopyUs = 0, mFetchUs = 0;
    std::chrono::steady_clock::time_point mWindow = std::chrono::steady_clock::now();
    static void ready(void *opaque) {
        // The queued handler runs after process() has returned and the base
        // component has inserted the work into its pending map.
        (void)static_cast<ReorderedDecoder *>(opaque)->drain_nb(DRAIN_COMPONENT_NO_EOS);
    }
    c2_status_t onInit() override { return C2_OK; }
    c2_status_t onStop() override {
        apklayer_codec_close(mCodec); mCodec = nullptr;
        mPending.clear(); mByPts.clear(); mEosIndex.reset(); mEosQueued = false;
        return C2_OK;
    }
    void onReset() override { (void)onStop(); mConfig.clear(); }
    void onRelease() override { (void)onStop(); mConfig.clear(); }
    c2_status_t onFlush_sm() override { return onStop(); }  // mConfig survives
    void finishRemaining(c2_status_t status) {
        std::vector<uint64_t> indices;
        for (const auto& [index, pending] : mPending) {
            (void)pending;
            if (!mEosIndex || index != *mEosIndex) indices.push_back(index);
        }
        if (mEosIndex && mPending.count(*mEosIndex)) indices.push_back(*mEosIndex);
        mPending.clear(); mByPts.clear();
        for (uint64_t index : indices) {
            bool eos = mEosIndex && index == *mEosIndex;
            finish(index, [status, eos](const std::unique_ptr<C2Work>& work) {
                work->result = status; work->workletsProcessed = 1;
                auto& out = work->worklets.front()->output;
                out.ordinal = work->input.ordinal;
                out.flags = eos ? C2FrameData::FLAG_END_OF_STREAM : C2FrameData::flags_t(0);
                out.buffers.clear();
            });
        }
    }
    void process(const std::unique_ptr<C2Work>& work, const std::shared_ptr<C2BlockPool>&) override {
        work->result = C2_OK; work->workletsProcessed = 1;
        auto& out = work->worklets.front()->output;
        out.ordinal = work->input.ordinal; out.flags = work->input.flags; out.buffers.clear();
        if (mEosQueued) { work->result = C2_BAD_STATE; return; }
        bool eos = work->input.flags & C2FrameData::FLAG_END_OF_STREAM;
        bool config = work->input.flags & C2FrameData::FLAG_CODEC_CONFIG;
        bool input = !work->input.buffers.empty();
        if (!input && !mCodec) { mEosQueued = eos; return; }
        int64_t pts = work->input.ordinal.timestamp.peekll();
        if (pts < 0 || pts > INT64_MAX / 1000) { work->result = C2_BAD_VALUE; return; }
        if (!mCodec) {
            apklayer_codec_config setup = {mParams->info().mime, mParams->width(), mParams->height(), nullptr, 0};
            if (apklayer_codec_open(&setup, &mCodec) || apklayer_codec_set_notify(mCodec, ready, this)) {
                work->result = C2_CANNOT_DO; return;
            }
            if (!mConfig.empty() && apklayer_codec_queue(mCodec, mConfig.data(), mConfig.size(), -1)) {
                work->result = C2_CORRUPTED; return;
            }
        }
        if (input) {
            const auto& blocks = work->input.buffers[0]->data().linearBlocks();
            if (blocks.empty()) { work->result = C2_BAD_VALUE; return; }
            C2ReadView view = blocks[0].map().get();
            if (view.error()) { work->result = view.error(); return; }
            if (view.capacity() && apklayer_codec_queue(mCodec, view.data(), view.capacity(), config ? -1 : pts * 1000)) {
                work->result = C2_CORRUPTED; return;
            }
            input = view.capacity() != 0;
            if (config && input) {
                mConfig.insert(mConfig.end(), view.data(), view.data() + view.capacity());
                mConfigPts = pts;
            }
        }
        if ((!config && input) || eos) {
            uint64_t index = work->input.ordinal.frameIndex.peekull();
            mPending[index] = {work->input.ordinal, work->input.flags};
            if (!config && input) mByPts[pts].push_back(index);
            work->workletsProcessed = 0;
            if (eos) { mEosIndex = index; mEosQueued = true; }
        }
        if (eos && apklayer_codec_end(mCodec)) {
            mPending.erase(work->input.ordinal.frameIndex.peekull());
            work->result = C2_CORRUPTED; work->workletsProcessed = 1;
        }
    }
    c2_status_t drain(uint32_t mode, const std::shared_ptr<C2BlockPool>& pool) override {
        if (mode == DRAIN_CHAIN) return C2_OMITTED;
        if (!mCodec) return C2_OK;
        if (mode == DRAIN_COMPONENT_WITH_EOS && !mEosQueued) {
            mEosQueued = true;
            if (!mPending.empty()) mEosIndex = mPending.rbegin()->first;
            if (apklayer_codec_end(mCodec)) return C2_CORRUPTED;
        }
        for (;;) {
            apklayer_codec_frame *raw = nullptr;
            int status = apklayer_codec_pull(mCodec, 0, &raw);
            if (status == -EAGAIN) return C2_OK;
            if (status == -ENODATA) { finishRemaining(C2_OK); return C2_OK; }
            if (status) { finishRemaining(C2_CORRUPTED); return C2_CORRUPTED; }
            std::unique_ptr<apklayer_codec_frame, decltype(&apklayer_codec_frame_release)> frame(raw, apklayer_codec_frame_release);
            apklayer_codec_frame_info info;
            apklayer_codec_frame_describe(raw, &info);
            auto found = mByPts.find(info.pts_ns / 1000);
            if (info.pts_ns < 0 || found == mByPts.end() || found->second.empty()) {
                fprintf(stderr, "codec2: unmatched reordered output PTS %lld (%zu pending pts, first %lld)\n", (long long)info.pts_ns, mByPts.size(),
                        mByPts.empty() ? -1LL : (long long)mByPts.begin()->first);
                finishRemaining(C2_CORRUPTED); return C2_CORRUPTED;
            }
            uint64_t index = found->second.front();
            found->second.pop_front();
            if (found->second.empty()) mByPts.erase(found);
            DecodedGraphic graphic;
            auto t0 = std::chrono::steady_clock::now();
            c2_status_t result = copyGraphic(raw, pool, mParams, &graphic);
            // timing line every 60 decoded frames: milliseconds in the copy into the graphic block, and the wall time of the 60
            mFetchUs += graphic.fetchUs;
            mCopyUs += std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now() - t0).count();
            if (++mFrames % 60 == 0) {
                fprintf(stderr, "codec2: 60 frames in %lld ms, graphic %lld ms total, of which waiting for a free buffer %lld ms\n", (long long)std::chrono::duration_cast<std::chrono::milliseconds>(t0 - mWindow).count(), (long long)(mCopyUs / 1000), (long long)(mFetchUs / 1000));
                mCopyUs = mFetchUs = 0; mWindow = t0;
            }
            if (result != C2_OK) { finishRemaining(result); return result; }
            bool intermediate = mEosIndex && index == *mEosIndex;
            auto buffer = createGraphicBuffer(graphic.block, C2Rect(graphic.width, graphic.height));
            auto fill = [buffer, graphic, intermediate](const std::unique_ptr<C2Work>& work) {
                work->result = C2_OK; work->workletsProcessed = 1;
                auto& out = work->worklets.front()->output;
                out.ordinal = work->input.ordinal;
                out.flags = intermediate ? C2FrameData::FLAG_INCOMPLETE : C2FrameData::flags_t(0);
                out.buffers.clear(); out.buffers.push_back(buffer); out.configUpdate.clear();
                if (graphic.changed) {
                    C2StreamPictureSizeInfo::output size(0u, graphic.width, graphic.height);
                    out.configUpdate.push_back(C2Param::Copy(size));
                }
            };
            if (intermediate) {
                // cloneAndSend accepts the current-work ordinal as its context;
                // retain the actual pending EOS work until backend EOS arrives.
                auto context = std::make_unique<C2Work>();
                context->input.ordinal = mPending.at(index).ordinal;
                context->input.flags = mPending.at(index).flags;
                cloneAndSend(index, context, fill);
            } else {
                mPending.erase(index);
                finish(index, fill);
            }
        }
    }
public:
    ReorderedDecoder(c2_node_id_t id, std::shared_ptr<DecoderParams> params)
        : SimpleC2Component(std::make_shared<SimpleInterface<DecoderParams>>(params->info().name, id, params)),
          mParams(std::move(params)) {}
    ~ReorderedDecoder() override { onRelease(); }
};
std::shared_ptr<C2Component> makeReorderedDecoder(c2_node_id_t id,
        const std::shared_ptr<C2ReflectorHelper>& helper, const DecoderInfo& info) {
    return std::make_shared<ReorderedDecoder>(id, std::make_shared<DecoderParams>(helper, info));
}
}
