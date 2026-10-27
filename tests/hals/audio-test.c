/* Actual PipeWire buffers, queue-based clock, drain and control transitions. */
#include <apklayer/audio.h>
#include <assert.h>
#include <errno.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <time.h>

static uint64_t now(void) {
    struct timespec time; clock_gettime(CLOCK_MONOTONIC, &time);
    return (uint64_t)time.tv_sec * 1000000000 + time.tv_nsec;
}
static void write_tone(struct apklayer_audio *audio, unsigned frames, const char *label, int mono) {
    int16_t pcm[960 * 2];
    float floating[960];
    unsigned submitted = 0;
    uint64_t position = 0, stamp = 0;
    int saw_queued = 0;
    while (submitted < frames) {
        unsigned count = frames - submitted > 960 ? 960 : frames - submitted;
        for (unsigned n = 0; n < count; n++) {
            floating[n] = sin(2 * M_PI * 440 * (submitted + n) / 48000) * (6000.0 / 32768);
            pcm[n * 2] = sin(2 * M_PI * 440 * (submitted + n) / 48000) * 6000;
            pcm[n * 2 + 1] = sin(2 * M_PI * 880 * (submitted + n) / 48000) * 4000;
        }
        unsigned done = 0;
        while (done < count) {
            ssize_t result = apklayer_audio_write(audio, mono ? (void *)(floating + done) : (void *)(pcm + done * 2), count - done);
            if (result < 0) fprintf(stderr, "audio write failed %zd\n", result);
            assert(result > 0); done += result;
        }
        submitted += count;
        struct apklayer_audio_position current;
        assert(apklayer_audio_position(audio, &current) == 0);
        assert(current.frames >= position && current.frames <= submitted && current.timestamp_ns >= stamp);
        saw_queued |= current.pending_frames > 0 && current.frames < submitted;
        position = current.frames; stamp = current.timestamp_ns;
        if (submitted % 9600 == 0) printf("audio: %s submitted=%u presented=%lu pending=%lu\n", label, submitted, position, current.pending_frames);
    }
    assert(saw_queued); /* Never report bytes queued as already played. */
    int result = apklayer_audio_drain(audio);
    if (result < 0) fprintf(stderr, "audio drain failed %d\n", result);
    assert(result == 0);
    struct apklayer_audio_position final;
    assert(apklayer_audio_position(audio, &final) == 0 && final.frames == frames && final.pending_frames == 0);
    printf("PASS audio %s drained %u actual frames\n", label, frames);
}
int main(int argc, char **argv) {
    setvbuf(stdout, NULL, _IOLBF, 0);
    assert(argc == 2 || argc == 3);
    int mono = argc == 3;
    struct apklayer_audio_config config = {48000, 3, APKLAYER_AUDIO_S16, argv[1]};
    struct apklayer_audio *audio = NULL;
    assert(apklayer_audio_open(&config, &audio) == -EINVAL && !audio);
    config.channels = mono ? 1 : 2;
    config.format = mono ? APKLAYER_AUDIO_F32 : APKLAYER_AUDIO_S16;
    int result = apklayer_audio_open(&config, &audio);
    if (result) fprintf(stderr, "audio open failed %d\n", result);
    assert(result == 0);
    puts("audio: connected real PipeWire target");
    uint64_t start = now();
    write_tone(audio, 48000, mono ? "F32 mono" : "S16 stereo", mono);
    assert(now() - start > 500000000); /* Real graph pacing, not a sink that drops input. */
    assert(apklayer_audio_pause(audio) == 0);
    int16_t sample[2] = {0};
    assert(apklayer_audio_write(audio, sample, 1) == -EAGAIN);
    assert(apklayer_audio_flush(audio) == 0);
    struct apklayer_audio_position position;
    assert(apklayer_audio_position(audio, &position) == 0 && position.frames == 0);
    assert(apklayer_audio_start(audio) == 0);
    write_tone(audio, 4800, "after flush/resume", mono);
    apklayer_audio_close(audio);
    puts("PASS native PipeWire playback, delayed clock, drain, pause/flush/resume and close");
    return 0;
}
