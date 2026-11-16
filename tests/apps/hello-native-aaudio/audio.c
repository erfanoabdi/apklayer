/* A native voice communication player: AAudio or OpenSL ES (no AudioTrack in Java, so the process never tells the hub about its player). */
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <aaudio/AAudio.h>
#include <jni.h>
#include <math.h>
#include <stdint.h>
#include <string.h>

static AAudioStream *stream;
static SLObjectItf engine, mix, player;
static SLAndroidSimpleBufferQueueItf queue;
static int16_t pcm[960 * 2];
static float phase;

static void fill(int16_t *out, int frames) {
    for (int i = 0; i < frames; i++, phase += 0.0587f) out[2 * i] = out[2 * i + 1] = (int16_t)(sinf(phase) * 8000);
}
static aaudio_data_callback_result_t on_data(AAudioStream *s, void *user, void *data, int32_t frames) {
    fill(data, frames);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
static void on_buffer(SLAndroidSimpleBufferQueueItf q, void *user) {
    fill(pcm, 960);
    (*q)->Enqueue(q, pcm, sizeof pcm);
}

/* api 0 = AAudio, 1 = OpenSL ES; the stream's usage is VOICE_COMMUNICATION. Returns 0 when it plays. */
JNIEXPORT jint JNICALL Java_hello_nativeaaudio_Main_play(JNIEnv *env, jclass c, jint api) {
    if (api == 0) {
        AAudioStreamBuilder *b;
        if (AAudio_createStreamBuilder(&b)) return 1;
        AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setSampleRate(b, 48000);
        AAudioStreamBuilder_setChannelCount(b, 2);
        AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_I16);
        AAudioStreamBuilder_setUsage(b, AAUDIO_USAGE_VOICE_COMMUNICATION);
        AAudioStreamBuilder_setDataCallback(b, on_data, NULL);
        aaudio_result_t r = AAudioStreamBuilder_openStream(b, &stream);
        AAudioStreamBuilder_delete(b);
        return r ? 2 : AAudioStream_requestStart(stream) ? 3 : 0;
    }
    SLDataLocator_AndroidSimpleBufferQueue loc = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};
    SLDataFormat_PCM fmt = {SL_DATAFORMAT_PCM, 2, SL_SAMPLINGRATE_48, SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
        SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT, SL_BYTEORDER_LITTLEENDIAN};
    SLDataSource src = {&loc, &fmt};
    SLDataLocator_OutputMix out = {SL_DATALOCATOR_OUTPUTMIX};
    SLEngineItf eng;
    SLPlayItf play;
    SLAndroidConfigurationItf config;
    const SLInterfaceID ids[2] = {SL_IID_BUFFERQUEUE, SL_IID_ANDROIDCONFIGURATION};
    const SLboolean req[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
    if (slCreateEngine(&engine, 0, NULL, 0, NULL, NULL) || (*engine)->Realize(engine, SL_BOOLEAN_FALSE) ||
        (*engine)->GetInterface(engine, SL_IID_ENGINE, &eng) || (*eng)->CreateOutputMix(eng, &mix, 0, NULL, NULL) ||
        (*mix)->Realize(mix, SL_BOOLEAN_FALSE)) return 1;
    out.outputMix = mix;
    SLDataSink sink = {&out, NULL};
    if ((*eng)->CreateAudioPlayer(eng, &player, &src, &sink, 2, ids, req) ||
        (*player)->GetInterface(player, SL_IID_ANDROIDCONFIGURATION, &config)) return 2;
    SLint32 stream_type = SL_ANDROID_STREAM_VOICE;  /* VOICE_COMMUNICATION usage */
    if ((*config)->SetConfiguration(config, SL_ANDROID_KEY_STREAM_TYPE, &stream_type, sizeof stream_type)) return 2;
    if ((*player)->Realize(player, SL_BOOLEAN_FALSE) || (*player)->GetInterface(player, SL_IID_BUFFERQUEUE, &queue) ||
        (*player)->GetInterface(player, SL_IID_PLAY, &play) || (*queue)->RegisterCallback(queue, on_buffer, NULL)) return 3;
    on_buffer(queue, NULL);
    return (*play)->SetPlayState(play, SL_PLAYSTATE_PLAYING) ? 4 : 0;
}

JNIEXPORT void JNICALL Java_hello_nativeaaudio_Main_stop(JNIEnv *env, jclass c) {
    if (stream) { AAudioStream_requestStop(stream); AAudioStream_close(stream); stream = NULL; }
    if (player) { (*player)->Destroy(player); player = NULL; }
    if (mix) { (*mix)->Destroy(mix); mix = NULL; }
    if (engine) { (*engine)->Destroy(engine); engine = NULL; }
}
