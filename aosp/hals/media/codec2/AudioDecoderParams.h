/* AOSP C2SoftAacDec parameter vocabulary, restricted to tested raw AAC-LC PCM. */
#pragma once
#include "HostDecoder.h"
#include <SimpleC2Interface.h>
#include <C2Config.h>
namespace apklayer {
inline constexpr char kAudioDecoderName[] = "c2.apklayer.aac.decoder";
inline constexpr char kAudioDecoderMime[] = "audio/mp4a-latm";
class AudioDecoderParams : public android::SimpleInterface<void>::BaseParams {
public:
    explicit AudioDecoderParams(const std::shared_ptr<C2ReflectorHelper>& helper)
        : BaseParams(helper, kAudioDecoderName, C2Component::KIND_DECODER, C2Component::DOMAIN_AUDIO, kAudioDecoderMime) {
        noPrivateBuffers(); noInputReferences(); noOutputReferences(); noInputLatency(); noTimeStretch();
        setDerivedInstance(this);
        addParameter(DefineParam(mRate, C2_PARAMKEY_SAMPLE_RATE)
            .withDefault(new C2StreamSampleRateInfo::output(0u, 44100))
            .withFields({C2F(mRate, value).oneOf({8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000, 64000, 88200, 96000})})
            .withSetter(android::Setter<decltype(*mRate)>::StrictValueWithNoDeps).build());
        addParameter(DefineParam(mChannels, C2_PARAMKEY_CHANNEL_COUNT)
            .withDefault(new C2StreamChannelCountInfo::output(0u, 1))
            .withFields({C2F(mChannels, value).inRange(1, 2)})
            .withSetter(android::Setter<decltype(*mChannels)>::StrictValueWithNoDeps).build());
        addParameter(DefineParam(mPackaging, C2_PARAMKEY_AAC_PACKAGING)
            .withConstValue(new C2StreamAacFormatInfo::input(0u, C2Config::AAC_PACKAGING_RAW)).build());
        addParameter(DefineParam(mPcm, C2_PARAMKEY_PCM_ENCODING)
            .withConstValue(new C2StreamPcmEncodingInfo::output(0u, C2Config::PCM_16)).build());
        addParameter(DefineParam(mProfile, C2_PARAMKEY_PROFILE_LEVEL)
            .withDefault(new C2StreamProfileLevelInfo::input(0u, C2Config::PROFILE_AAC_LC, C2Config::LEVEL_UNUSED))
            .withFields({C2F(mProfile, profile).equalTo(C2Config::PROFILE_AAC_LC), C2F(mProfile, level).equalTo(C2Config::LEVEL_UNUSED)})
            .withSetter(ProfileSetter).build());
        addParameter(DefineParam(mDelay, C2_PARAMKEY_OUTPUT_DELAY)
            .withConstValue(new C2PortActualDelayTuning::output(4)).build());
        addParameter(DefineParam(mMaxInput, C2_PARAMKEY_INPUT_MAX_BUFFER_SIZE)
            .withConstValue(new C2StreamMaxBufferSizeInfo::input(0u, 65536)).build());
    }
    uint32_t rate() const { return mRate->value; }
    uint32_t channels() const { return mChannels->value; }
    c2_status_t format(uint32_t rate, uint32_t channels) {
        C2StreamSampleRateInfo::output r(0u, rate);
        C2StreamChannelCountInfo::output c(0u, channels);
        std::vector<std::unique_ptr<C2SettingResult>> failures;
        return config({&r, &c}, C2_MAY_BLOCK, &failures);
    }
private:
    static C2R ProfileSetter(bool, C2P<C2StreamProfileLevelInfo::input>& me) {
        return me.F(me.v.profile).validatePossible(me.v.profile).plus(me.F(me.v.level).validatePossible(me.v.level));
    }
    std::shared_ptr<C2StreamSampleRateInfo::output> mRate;
    std::shared_ptr<C2StreamChannelCountInfo::output> mChannels;
    std::shared_ptr<C2StreamAacFormatInfo::input> mPackaging;
    std::shared_ptr<C2StreamPcmEncodingInfo::output> mPcm;
    std::shared_ptr<C2StreamProfileLevelInfo::input> mProfile;
    std::shared_ptr<C2PortActualDelayTuning::output> mDelay;
    std::shared_ptr<C2StreamMaxBufferSizeInfo::input> mMaxInput;
};
std::shared_ptr<C2Component> makeAudioDecoder(c2_node_id_t, const std::shared_ptr<C2ReflectorHelper>&);
std::shared_ptr<C2ComponentInterface> makeAudioDecoderInterface(c2_node_id_t, const std::shared_ptr<C2ReflectorHelper>&);
}
