/* Shared AOSP SimpleInterface parameters for host video decoders. */
#pragma once
#include "HostDecoder.h"
#include <SimpleC2Interface.h>
#include <C2Config.h>
#include <system/graphics.h>
namespace apklayer {
using android::SimpleInterface;
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
        if (info.type == DecoderInfo::VP9) {
            // AOSP C2SoftVpxDec profile/level vocabulary; 8-bit 4:2:0 only.
            addParameter(DefineParam(mProfile, C2_PARAMKEY_PROFILE_LEVEL)
                .withDefault(new C2StreamProfileLevelInfo::input(0u, C2Config::PROFILE_VP9_0, C2Config::LEVEL_VP9_5))
                .withFields({C2F(mProfile, profile).equalTo(C2Config::PROFILE_VP9_0),
                    C2F(mProfile, level).oneOf({C2Config::LEVEL_VP9_1, C2Config::LEVEL_VP9_1_1,
                        C2Config::LEVEL_VP9_2, C2Config::LEVEL_VP9_2_1, C2Config::LEVEL_VP9_3,
                        C2Config::LEVEL_VP9_3_1, C2Config::LEVEL_VP9_4, C2Config::LEVEL_VP9_4_1,
                        C2Config::LEVEL_VP9_5})})
                .withSetter(ProfileSetter).build());
        } else if (info.type == DecoderInfo::AVC) {
            // AOSP C2SoftAvcDec's 8-bit profile/level and output-delay vocabulary.
            addParameter(DefineParam(mProfile, C2_PARAMKEY_PROFILE_LEVEL)
                .withDefault(new C2StreamProfileLevelInfo::input(0u, C2Config::PROFILE_AVC_CONSTRAINED_BASELINE, C2Config::LEVEL_AVC_5_2))
                .withFields({C2F(mProfile, profile).oneOf({C2Config::PROFILE_AVC_CONSTRAINED_BASELINE,
                    C2Config::PROFILE_AVC_BASELINE, C2Config::PROFILE_AVC_MAIN,
                    C2Config::PROFILE_AVC_CONSTRAINED_HIGH, C2Config::PROFILE_AVC_PROGRESSIVE_HIGH, C2Config::PROFILE_AVC_HIGH}),
                    C2F(mProfile, level).oneOf({C2Config::LEVEL_AVC_1, C2Config::LEVEL_AVC_1B, C2Config::LEVEL_AVC_1_1,
                        C2Config::LEVEL_AVC_1_2, C2Config::LEVEL_AVC_1_3, C2Config::LEVEL_AVC_2,
                        C2Config::LEVEL_AVC_2_1, C2Config::LEVEL_AVC_2_2, C2Config::LEVEL_AVC_3,
                        C2Config::LEVEL_AVC_3_1, C2Config::LEVEL_AVC_3_2, C2Config::LEVEL_AVC_4,
                        C2Config::LEVEL_AVC_4_1, C2Config::LEVEL_AVC_4_2, C2Config::LEVEL_AVC_5,
                        C2Config::LEVEL_AVC_5_1, C2Config::LEVEL_AVC_5_2})})
                .withSetter(ProfileSetter).build());
            addParameter(DefineParam(mDelay, C2_PARAMKEY_OUTPUT_DELAY)
                .withConstValue(new C2PortActualDelayTuning::output(16)).build());
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
    std::shared_ptr<C2PortActualDelayTuning::output> mDelay;
    const DecoderInfo& mInfo;
    std::shared_ptr<C2StreamPictureSizeInfo::output> mSize;
    std::shared_ptr<C2StreamProfileLevelInfo::input> mProfile;
    std::shared_ptr<C2StreamMaxBufferSizeInfo::input> mMaxInput;
    std::shared_ptr<C2StreamPixelFormatInfo::output> mPixelFormat;
};

}
