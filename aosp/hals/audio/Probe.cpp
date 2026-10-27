// Remote binder discovery/configuration and real command/reply/data FMQs.
#include "Stream.h"
#include <aidl/android/hardware/audio/core/IModule.h>
#include <aidl/android/media/audio/common/Int.h>
#include <android/binder_manager.h>
#include <android-base/logging.h>
#include <cmath>
#include <cstdio>

using namespace aidl::android::hardware::audio::core;
using namespace aidl::android::media::audio::common;
namespace {
void check(const ndk::ScopedAStatus& status) {
    if (!status.isOk()) fprintf(stderr, "audio AIDL: %s\n", status.getDescription().c_str());
    CHECK(status.isOk());
}
}
int audioProbe() {
    setvbuf(stdout, nullptr, _IOLBF, 0);
    auto module = IModule::fromBinder(ndk::SpAIBinder(
        AServiceManager_waitForService("android.hardware.audio.core.IModule/default")));
    CHECK(module && AIBinder_isRemote(module->asBinder().get()));
    std::vector<AudioPort> ports;
    check(module->getAudioPorts(&ports));
    CHECK(ports.size() == 2);
    int mixId = 0, deviceId = 0;
    for (const auto& port : ports) {
        AudioPortConfig request, applied;
        request.portId = port.id;
        request.flags = port.flags;
        request.sampleRate = Int{48000};
        request.channelMask = AudioChannelLayout::make<AudioChannelLayout::layoutMask>(AudioChannelLayout::LAYOUT_STEREO);
        AudioFormatDescription format;
        format.type = AudioFormatType::PCM; format.pcm = PcmType::INT_16_BIT;
        request.format = format;
        request.ext = port.ext;
        if (request.ext.getTag() == AudioPortExt::mix) request.ext.get<AudioPortExt::mix>().handle = 42;
        bool accepted = false;
        check(module->setAudioPortConfig(request, &applied, &accepted));
        CHECK(accepted);
        if (port.ext.getTag() == AudioPortExt::mix) mixId = applied.id;
        else deviceId = applied.id;
    }
    CHECK(mixId && deviceId);
    AudioPatch request, patch;
    request.sourcePortConfigIds = {mixId}; request.sinkPortConfigIds = {deviceId};
    check(module->setAudioPatch(request, &patch));
    IModule::OpenOutputStreamArguments args;
    args.portConfigId = mixId; args.bufferSizeFrames = 4096;
    IModule::OpenOutputStreamReturn opened;
    check(module->openOutputStream(args, &opened));
    CHECK(opened.stream && opened.desc.frameSizeBytes == 4);
    std::shared_ptr<IStreamCommon> common;
    check(opened.stream->getStreamCommon(&common));
    StreamContext::CommandMQ commands(opened.desc.command, false);
    StreamContext::ReplyMQ replies(opened.desc.reply, false);
    CHECK(opened.desc.audio.getTag() == StreamDescriptor::AudioBuffer::fmq);
    StreamContext::DataMQ data(opened.desc.audio.get<StreamDescriptor::AudioBuffer::fmq>(), false);
    CHECK(commands.isValid() && replies.isValid() && data.isValid());
    auto command = [&](const StreamDescriptor::Command& cmd) {
        CHECK(commands.writeBlocking(&cmd, 1, 2000000000));
        StreamDescriptor::Reply reply;
        CHECK(replies.readBlocking(&reply, 1, 2000000000));
        if (reply.status) fprintf(stderr, "audio command failed: %s\n", reply.toString().c_str());
        CHECK(reply.status == 0);
        return reply;
    };
    using Tag = StreamDescriptor::Command::Tag;
    command(StreamDescriptor::Command::make<Tag::start>(Void{}));
    bool sawPending = false;
    int64_t previous = 0;
    for (int offset = 0; offset < 48000; offset += 960) {
        int16_t pcm[1920];
        for (int n = 0; n < 960; n++) {
            pcm[n * 2] = std::sin(2 * M_PI * 440 * (offset + n) / 48000) * 6000;
            pcm[n * 2 + 1] = std::sin(2 * M_PI * 880 * (offset + n) / 48000) * 4000;
        }
        CHECK(data.write(reinterpret_cast<int8_t*>(pcm), sizeof pcm));
        auto reply = command(StreamDescriptor::Command::make<Tag::burst>(sizeof pcm));
        CHECK(reply.fmqByteCount == sizeof pcm);
        CHECK(reply.observable.frames >= previous && reply.observable.frames <= offset + 960);
        CHECK(reply.observable.timeNs > 0);
        sawPending |= reply.observable.frames < offset + 960;
        previous = reply.observable.frames;
        if ((offset + 960) % 9600 == 0)
            printf("audio: AIDL submitted=%d presented=%ld latency=%d\n", offset + 960, previous, reply.latencyMs);
    }
    CHECK(sawPending);
    auto reply = command(StreamDescriptor::Command::make<Tag::drain>(StreamDescriptor::DrainMode::DRAIN_ALL));
    CHECK(reply.observable.frames == 48000);
    // Query status resolves the original AOSP synchronous DRAINING transition.
    reply = command(StreamDescriptor::Command::make<Tag::getStatus>(Void{}));
    CHECK(reply.state == StreamDescriptor::State::IDLE);
    command(StreamDescriptor::Command::make<Tag::standby>(Void{}));
    command(StreamDescriptor::Command::make<Tag::start>(Void{}));
    int16_t replay[1920];
    for (int n = 0; n < 960; n++) {
        replay[n * 2] = std::sin(2 * M_PI * 440 * n / 48000) * 6000;
        replay[n * 2 + 1] = std::sin(2 * M_PI * 880 * n / 48000) * 4000;
    }
    for (int n = 0; n < 2; n++) {
        CHECK(data.write(reinterpret_cast<int8_t*>(replay), sizeof replay));
        reply = command(StreamDescriptor::Command::make<Tag::burst>(sizeof replay));
        CHECK(reply.fmqByteCount == sizeof replay);
    }
    command(StreamDescriptor::Command::make<Tag::pause>(Void{}));
    reply = command(StreamDescriptor::Command::make<Tag::flush>(Void{}));
    CHECK(reply.observable.frames == 49920);
    CHECK(data.write(reinterpret_cast<int8_t*>(replay), sizeof replay));
    reply = command(StreamDescriptor::Command::make<Tag::burst>(sizeof replay));
    CHECK(reply.fmqByteCount == sizeof replay && reply.observable.frames >= 49920);
    reply = command(StreamDescriptor::Command::make<Tag::drain>(StreamDescriptor::DrainMode::DRAIN_ALL));
    CHECK(reply.observable.frames == 50880);
    command(StreamDescriptor::Command::make<Tag::getStatus>(Void{}));
    command(StreamDescriptor::Command::make<Tag::standby>(Void{}));
    puts("PASS audio AIDL pause/flush/replay preserves lifetime frame epoch 50880");
    check(common->close());
    opened.stream.reset(); common.reset();
    check(module->resetAudioPatch(patch.id));
    check(module->resetAudioPortConfig(mixId));
    check(module->resetAudioPortConfig(deviceId));
    puts("PASS audio remote AIDL/FMQ 48000 stereo PCM frames, real position/drain and close");
    return 0;
}
