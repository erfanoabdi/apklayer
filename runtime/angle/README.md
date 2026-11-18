# ANGLE Android libraries

These x86_64 and x86 Android EGL/GLES libraries are built from Google ANGLE revision
`f0d596b6f9e3dfe7a068786790516c035b1dfde4` for Android API 37. The GN build enables Vulkan and uses the `_angle` library suffix. The source includes apklayer patches `patches/angle/0001-use-host-safe-tls-for-vulkan.patch` and `0002-disable-nvidia-android-deferred-swapchain.patch`; see `docs/PATCHES.md`. ANGLE's Android GLES 3.2 exposure is enabled at runtime with `ANGLE_FEATURE_OVERRIDES_ENABLED=exposeES32ForTesting`.

Meson packages the files under `runtime/angle/vendor` into `/usr/lib/apklayer/angle/vendor`. Runtime setup links them into `/system/lib` and `/system/lib64`, where Android 17's EGL loader looks for the `angle` suffix when `ro.hardware.egl=angle`.

ANGLE is licensed under the BSD 3-Clause license. The binaries are built from the Google ANGLE source, not copied from the Waydroid NVIDIA Android 13 image.

SHA-256:

| ABI | Library | SHA-256 |
|---|---|---|
| x86_64 | `libEGL_angle.so` | `ae78e0ac613c94f3426dd55b46429c5d68fe662c70ea89f7f4c56b9908ad1a06` |
| x86_64 | `libGLESv1_CM_angle.so` | `63c2e858fb1d99aba9e896bfa1f7c31b789ec6d20da71fec74a64751bd213999` |
| x86_64 | `libGLESv2_angle.so` | `6860953c03abad8976ff209d90edd61d71c95ecc65d1181515c5715ee0a122f0` |
| x86 | `libEGL_angle.so` | `fd23048764fe33787f56cfc9927d7134e6a317c5924596785a57a347c36ebfc6` |
| x86 | `libGLESv1_CM_angle.so` | `e7af29754e1cd5038be6507e19968120ce2eaacf333fa63658ea18c513e9274a` |
| x86 | `libGLESv2_angle.so` | `41a3257ec8249061af569eb0ceb9bb544cf515f3a229cbb71b4eb4c44ed860cb` |
