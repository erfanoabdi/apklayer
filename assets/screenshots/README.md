# Capture provenance

Captured on 2026-11-05 from the owner's Parallels VM: Ubuntu 24.04 LTS arm64, GNOME Wayland, virgl (Apple M5 Max compatibility renderer), host OpenGL ES 3.0 / Mesa 25.2.8. These are development-tree builds, not a claim that this is the current release UI. The installed manager build predates the Hardware page.

All published files are lossless WebP conversions of PNG captures at their captured dimensions. Decoded RGB pixels were compared with the PNGs before inclusion. No UI was generated, composited, cropped, or retouched. Full-size images are linked from the website. Each capture was visually inspected for private content.

| File | Captured state | Capture method |
| --- | --- | --- |
| `manager.webp` | LocalSend 1.18.2 app details and permissions; Organic Maps also installed in the temporary `website-gallery` environment | Native manager `--screenshot`, `--app org.localsend.localsend_app`, `--dark`; 958 × 701 |
| `microg.webp` | microG Settings 0.3.17.252432 showing account and service controls; device registration, Cloud Messaging, and attestation are off | `apklayer --instance website screenshot com.google.android.gms`; 325 × 579 |
| `newpipe.webp` | NewPipe 0.29.1 playing Big Buck Bunny from the Blender channel, with video and app UI visible | Upstream `tests/vm/newpipe-media.sh aqz-KE-bpKQ` in the `website` instance with `NEWPIPE_SECS=25`; subsequent direct SurfaceFlinger capture during playback; 720 × 1280 |
| `instances.webp` | Manager Instances page: `website` running and `default` stopped | Native manager `--screenshot`, `--page 1`, `--dark`; 958 × 701 |

The capture instance uses fresh per-instance app data with the VM's existing app library. No account was signed into and no physical camera was opened. The owner's installed APKs were not updated or reconverted. The existing NewPipe test sets the preferred link action to its video player in this dedicated instance's preferences.

NewPipe playback proof from this session:

- Two SurfaceFlinger captures two seconds apart differed by 21,838 pixels.
- The media check found one running PipeWire output stream from this instance.
- The earlier paired captures established playback. A later, sharper 720 × 1280 SurfaceFlinger capture with the film’s characters visible now appears on the website. The full captured pixels are preserved; the hero uses a CSS crop with a link to the full image.

Big Buck Bunny is by Blender Foundation, licensed CC BY 3.0. The website includes creator and license links. Source: https://www.youtube.com/watch?v=aqz-KE-bpKQ and https://peach.blender.org/.

The microG image documents the real settings interface, not successful account sign-in, push delivery, or attestation. The instance-management image does not demonstrate two simultaneously running stacks. The LocalSend, Organic Maps onboarding, and KeePassDX app captures were removed. The original manager overview was restored at the owner's request; it shows the real temporary app library, not a transfer or map-navigation demo. OTR2 was dropped at the owner's request; no game capture is published.
