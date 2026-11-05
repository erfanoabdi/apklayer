/* The audio domain's probe (docs/PLUGINS.md, cli/providers.cpp): libapklayer-audio-<backend>.so is a hardware provider backend, picked per
 * instance (`apklayer providers set audio <backend>`, else the best one that answers here). Built into each backend, with APKLAYER_AUDIO_PULSE
 * for the PulseAudio one. PipeWire ranks above PulseAudio: a desktop with PipeWire answers on pulse/native too (pipewire-pulse); a host with
 * PulseAudio only (Ubuntu Touch) has no pipewire-0. */
#include <apklayer/audio.h>
#include <apklayer/plugin.h>
#include <apklayer/sound_server.h>

const int apklayer_plugin_abi = APKLAYER_PLUGIN_ABI;
#ifdef APKLAYER_AUDIO_PULSE
int apklayer_audio_probe(void) { return apklayer_pulse_answers() ? 10 : -1; }
const char apklayer_audio_needs[] = "a PulseAudio server ($PULSE_SERVER or pulse/native)";
#else
int apklayer_audio_probe(void) { return apklayer_pipewire_answers() ? 20 : -1; }
const char apklayer_audio_needs[] = "a PipeWire daemon (pipewire-0)";
#endif
