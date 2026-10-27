# apklayer HALs, Android side

One directory per HAL domain, named as AOSP names them. Each holds the AIDL frontend (a thin service/library built here by Soong
against the tree's real AIDL interfaces) that calls the host side through a small C API (`bridge/hals/<domain>/`). The Android
side translates Android calls and types; everything that touches the desktop lives in the host side.

| Directory | AOSP interface | Host side (`bridge/hals/`) | Status |
|---|---|---|---|
| `graphics/allocator`, `graphics/mapper` | `android.hardware.graphics.allocator`, stable-C `IMapper` | `graphics/` over libgbm, dmabuf | host side done and tested (`bridge/hals/graphics`); Android side next (P7.1) |
| `graphics/composer` | `android.hardware.graphics.composer3` | `graphics/` over Wayland `zwp_linux_dmabuf_v1`, real vsync | planned (P7.1) |
| `media` | `android.hardware.media.c2` | `media/` over native GStreamer | VP8/VP9/AVC native, AIDL, app MediaCodec and SurfaceView tested; other codecs/audio next |
| `camera` | `android.hardware.camera.provider` | PipeWire / V4L2 | planned |
| `gnss` | `android.hardware.gnss` | GeoClue / gpsd | planned |
| `vibrator` | `android.hardware.vibrator` | force feedback (evdev FF), logind haptics | planned |
| `sensors` | `android.hardware.sensors` | iio-sensor-proxy / IIO | planned |
| `power`, `thermal`, `health` | optional answers for SurfaceFlinger and the framework | UPower, thermal zones | later |
| `input` | InputFlinger inputs | evdev / the shell's Wayland input | later |
| `audio` | `android.hardware.audio.core` | PipeWire | host S16/F32 PCM and real AIDL/FMQ output verified; AudioFlinger/Policy and app JNI next |

## `graphics/vdisplay` (not a HAL: a per-app virtual display sink)
One process per app window: a SurfaceFlinger virtual display whose output BufferQueue it consumes, handing the finished dmabufs to
apklayer-shell through the host display HAL. Written new because nothing local fits: the composer is the primary display's HWC (a
service SurfaceFlinger calls, client composition only, no virtual displays by design), `servicetool` is a one-shot client, and AOSP's
`screenrecord` (the same virtual display + consumer pattern) is built around a video encoder. It is a `cc_library_shared` exporting
`main` only because that is how every Android-side process here is run (`btload --main`).


## Media decoder backend (in progress)
`bridge/hals/media` reuses the existing GStreamer decoding engine as native C because the Python/RGBA-file protocol cannot retain decoded YUV samples or dmabuf ownership across Codec2 output lifetimes. Its plain C API provides bounded compressed input, real decoder discovery, timestamps/EOS and reference-counted output samples. Hardware samples expose borrowed dmabuf planes/modifiers; software samples can be mapped for the necessary copy into gralloc. VP8/VP9 software decode, Codec2 AIDL transport and app MediaCodec buffer/Surface output are verified. Hardware decoder dmabuf import and wider media/audio remain incomplete.

`media/codec2` reuses AOSP’s AIDL ComponentStore and SimpleC2Component/Interface plumbing. The new adapter is necessary because the supplied vendor store is empty and the software store directly loads Android decoders. Integration advertises available host VP8, VP9 profile 0 and 8-bit AVC and copies software I420 output into the mapper’s real flexible-YUV gralloc layout. Hidden references and one-visible-picture VP9 superframes are supported. Multiple displayed pictures use cloneAndSend with final-picture EOS; the AVC adapter completes reordered pending work by PTS on the existing component handler; hardware buffer import and other codecs follow; the interface and three-frame real gralloc probe pass tests/vm/codec2.sh; this frontend is not yet part of default startup.

`media/services` starts the existing AOSP MediaPlayerService and ResourceManagerService using the same registration calls as main_mediaserver.cpp. MediaCodec needs those process boundaries for resource ownership and codec discovery. A new btload entrypoint is required because the tree ships mediaserver as an executable; playback/policy stay in the original service libraries. The --extractor entrypoint runs the existing MediaExtractorService in a separate process with original MP4/WebM plugins; native callback-source MP4 metadata, seek and extracted-sample decoding are verified.

## Audio output
`audio` derives directly from AOSP Module and reuses StreamCommonImpl, StreamOut and the existing FMQ worker state machine in libaudioserviceexampleimpl. The only new driver delegates PCM and timing to the host PipeWire API. An unprivileged transfer-worker creator selects normal host priority; PipeWire owns graph scheduling. No stub stream is instantiated. Only tested synchronous 48k mono/stereo S16/F32 output profiles are advertised; capture, offload, MMAP and asynchronous output return errors. Unsupported hardware master controls return errors so AudioFlinger can apply software controls. The version script exports only main, allowing unused example implementations to be discarded. `tests/vm/audio-aidl.sh` verifies binder configuration, real FMQ PCM capture, presentation/drain, standby, pause/flush/replay and complete teardown. Startup is opt-in with APKLAYER_AUDIO=1; AudioFlinger/Policy and app native audio are subsequent work.
