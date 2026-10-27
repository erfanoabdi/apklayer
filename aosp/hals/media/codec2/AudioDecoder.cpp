/* Original SimpleC2Component worker/pending machinery over owned host AAC PCM. */
#include "AudioDecoderParams.h"
#include <apklayer/audio_codec.h>
#include <SimpleC2Component.h>
#include <media/stagefright/foundation/ABitReader.h>
#include <cerrno>
#include <cstring>
#include <deque>
#include <optional>
namespace apklayer {
using namespace android;
class AudioDecoder final : public SimpleC2Component {
    struct Pending { uint64_t index; C2WorkOrdinalStruct ordinal; C2FrameData::flags_t flags; };
    std::shared_ptr<AudioDecoderParams> mParams;
    apklayer_codec *mCodec = nullptr;
    std::vector<uint8_t> mConfig;
    std::deque<Pending> mPending;
    std::optional<Pending> mEos;
    bool mEnded = false, mFirst = true;
    static void ready(void *opaque) { (void)static_cast<AudioDecoder *>(opaque)->drain_nb(DRAIN_COMPONENT_NO_EOS); }
    c2_status_t close() {
        apklayer_codec_close(mCodec); mCodec = nullptr;
        mPending.clear(); mEos.reset(); mEnded = false; mFirst = true; return C2_OK;
    }
    c2_status_t onInit() override { return C2_OK; }
    c2_status_t onStop() override { mConfig.clear(); return close(); }
    void onReset() override { (void)onStop(); }
    void onRelease() override { (void)onStop(); }
    c2_status_t onFlush_sm() override { return close(); } // configured ASC survives flush.
    c2_status_t configure(const uint8_t *bytes, size_t size) {
        if (mCodec || size < 2 || size > 65536) return C2_BAD_VALUE;
        ABitReader bits(bytes, size);
        if (bits.getBits(5) != 2) return C2_CANNOT_DO; // AAC-LC only.
        uint32_t index = bits.getBits(4);
        constexpr uint32_t rates[] = {96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000,7350};
        uint32_t rate;
        if (index == 15) {
            if (bits.numBitsLeft() < 28) return C2_BAD_VALUE;
            rate = bits.getBits(24);
        } else { if (index >= std::size(rates)) return C2_BAD_VALUE; rate = rates[index]; }
        if (bits.numBitsLeft() < 4) return C2_BAD_VALUE;
        uint32_t channels = bits.getBits(4);
        if (mParams->format(rate, channels) != C2_OK) return C2_BAD_VALUE;
        mConfig.assign(bytes, bytes + size); return C2_OK;
    }
    void finishAll(c2_status_t status) {
        std::optional<uint64_t> eosIndex;
        if (mEos) eosIndex = mEos->index;
        auto fill = [status, eosIndex](const std::unique_ptr<C2Work>& work) {
            work->result = status; work->workletsProcessed = 1;
            auto& out = work->worklets.front()->output;
            out.ordinal = work->input.ordinal; out.buffers.clear();
            out.flags = eosIndex && work->input.ordinal.frameIndex.peekull() == *eosIndex
                    ? C2FrameData::FLAG_END_OF_STREAM : C2FrameData::flags_t(0);
        };
        bool eosPending = false;
        for (const auto& pending : mPending) {
            if (mEos && pending.index == mEos->index) eosPending = true;
            finish(pending.index, fill);
        }
        mPending.clear();
        if (mEos && !eosPending) finish(mEos->index, fill);
        mEos.reset();
    }
    void process(const std::unique_ptr<C2Work>& work, const std::shared_ptr<C2BlockPool>&) override {
        work->result = C2_OK; work->workletsProcessed = 1;
        auto& out = work->worklets.front()->output;
        out.ordinal = work->input.ordinal; out.flags = work->input.flags; out.buffers.clear();
        if (mEnded) { work->result = C2_BAD_STATE; return; }
        bool eos = work->input.flags & C2FrameData::FLAG_END_OF_STREAM;
        bool config = work->input.flags & C2FrameData::FLAG_CODEC_CONFIG;
        bool input = false;
        if (!work->input.buffers.empty()) {
            const auto& blocks = work->input.buffers[0]->data().linearBlocks();
            if (blocks.empty()) { work->result = C2_BAD_VALUE; return; }
            C2ReadView view = blocks[0].map().get();
            if (view.error()) { work->result = view.error(); return; }
            if (config) { work->result = eos ? C2_BAD_VALUE : configure(view.data(), view.capacity()); return; }
            input = view.capacity() != 0;
            if (input) {
                int64_t pts = work->input.ordinal.timestamp.peekll();
                if (mConfig.empty() || pts < 0 || pts > INT64_MAX / 1000 || view.capacity() > 65536) {
                    work->result = C2_BAD_VALUE; return;
                }
                if (!mCodec) {
                    apklayer_audio_codec_config setup = {kAudioDecoderMime, mParams->rate(), mParams->channels(), mConfig.data(), mConfig.size()};
                    if (apklayer_audio_codec_open(&setup, &mCodec) || apklayer_codec_set_notify(mCodec, ready, this)) {
                        work->result = C2_CANNOT_DO; return;
                    }
                }
                if (apklayer_codec_queue(mCodec, view.data(), view.capacity(), pts * 1000)) {
                    work->result = C2_CORRUPTED; return;
                }
            }
        }
        if (!mCodec) { mEnded = eos; return; }
        Pending pending{work->input.ordinal.frameIndex.peekull(), work->input.ordinal, work->input.flags};
        if (input) mPending.push_back(pending);
        if (eos) { mEos = pending; mEnded = true; }
        if (input || eos) work->workletsProcessed = 0;
        if (eos && apklayer_codec_end(mCodec)) {
            if (input) mPending.pop_back();
            mEos.reset();
            work->result = C2_CORRUPTED; work->workletsProcessed = 1;
        }
    }
    c2_status_t drain(uint32_t mode, const std::shared_ptr<C2BlockPool>& pool) override {
        if (mode == DRAIN_CHAIN) return C2_OMITTED;
        if (!mCodec) return C2_OK;
        if (mode == DRAIN_COMPONENT_WITH_EOS && !mEnded) {
            mEnded = true;
            if (!mPending.empty()) mEos = mPending.back();
            if (apklayer_codec_end(mCodec)) return C2_CORRUPTED;
        }
        for (;;) {
            apklayer_codec_frame *raw = nullptr;
            int status = apklayer_codec_pull(mCodec, 0, &raw);
            if (status == -EAGAIN) return C2_OK;
            if (status == -ENODATA) {
                c2_status_t result = mPending.empty() ? C2_OK : C2_CORRUPTED;
                finishAll(result); return result;
            }
            if (status || mPending.empty()) { if (raw) apklayer_codec_frame_release(raw); finishAll(C2_CORRUPTED); return C2_CORRUPTED; }
            std::unique_ptr<apklayer_codec_frame, decltype(&apklayer_codec_frame_release)> frame(raw, apklayer_codec_frame_release);
            apklayer_audio_codec_frame_info info;
            const void *bytes; size_t size;
            if (apklayer_audio_codec_frame_describe(raw, &info) || apklayer_audio_codec_frame_map(raw, &bytes, &size) || size > 4 * 1024 * 1024) {
                finishAll(C2_CORRUPTED); return C2_CORRUPTED;
            }
            bool changed = mFirst || info.sample_rate != mParams->rate() || info.channels != mParams->channels();
            c2_status_t result = mParams->format(info.sample_rate, info.channels);
            std::shared_ptr<C2LinearBlock> block;
            if (result == C2_OK) result = pool->fetchLinearBlock(size, {C2MemoryUsage::CPU_READ, C2MemoryUsage::CPU_WRITE}, &block);
            if (result != C2_OK) { finishAll(result); return result; }
            {
                C2WriteView target = block->map().get();
                if (target.error()) { finishAll(target.error()); return target.error(); }
                memcpy(target.data(), bytes, size);
            }
            Pending pending = mPending.front(); mPending.pop_front();
            bool intermediate = mEos && pending.index == mEos->index;
            auto buffer = createLinearBuffer(block, 0, size);
            auto fill = [buffer, info, changed, intermediate](const std::unique_ptr<C2Work>& work) {
                work->result = C2_OK; work->workletsProcessed = 1;
                auto& out = work->worklets.front()->output;
                out.ordinal = work->input.ordinal;
                if (info.pts_ns >= 0) out.ordinal.timestamp = info.pts_ns / 1000;
                out.flags = intermediate ? C2FrameData::FLAG_INCOMPLETE : C2FrameData::flags_t(0);
                out.buffers = {buffer}; out.configUpdate.clear();
                if (changed) {
                    out.configUpdate.push_back(C2Param::Copy(C2StreamSampleRateInfo::output(0u, info.sample_rate)));
                    out.configUpdate.push_back(C2Param::Copy(C2StreamChannelCountInfo::output(0u, info.channels)));
                }
            };
            if (intermediate) {
                auto context = std::make_unique<C2Work>(); context->input.ordinal = pending.ordinal; context->input.flags = pending.flags;
                cloneAndSend(pending.index, context, fill);
            } else finish(pending.index, fill);
            mFirst = false;
        }
    }
public:
    AudioDecoder(c2_node_id_t id, std::shared_ptr<AudioDecoderParams> params)
        : SimpleC2Component(std::make_shared<SimpleInterface<AudioDecoderParams>>(kAudioDecoderName, id, params)), mParams(std::move(params)) {}
    ~AudioDecoder() override { onRelease(); }
};
std::shared_ptr<C2Component> makeAudioDecoder(c2_node_id_t id, const std::shared_ptr<C2ReflectorHelper>& helper) {
    return std::make_shared<AudioDecoder>(id, std::make_shared<AudioDecoderParams>(helper));
}
std::shared_ptr<C2ComponentInterface> makeAudioDecoderInterface(c2_node_id_t id, const std::shared_ptr<C2ReflectorHelper>& helper) {
    return std::make_shared<SimpleInterface<AudioDecoderParams>>(kAudioDecoderName, id, std::make_shared<AudioDecoderParams>(helper));
}
}
