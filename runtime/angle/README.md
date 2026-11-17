# ANGLE Android vendor libraries

These unchanged Android ANGLE binaries were copied from the installed Waydroid NVIDIA v0.2.0 guest vendor tree:
`/usr/lib/waydroid-nvidia/guest/vendor/{lib64,lib}/egl/`. The package's vendor image is Android 20.0. They provide x86_64 and x86
EGL/GLES libraries. Meson installs them into the runtime's `android-root/vendor` on x86_64 builds; the Android EGL loader selects them
when an instance uses `ro.hardware.egl=angle`.

Waydroid NVIDIA source: <https://github.com/Shiro836/waydroid-nvidia> (package v0.2.0, vendor image digest
`692a0eeb45c288492f66ddba5b77ea15f00f88eca5354b048ec4b1e752aee29b`). ANGLE's source is BSD-style licensed. The Waydroid NVIDIA
package declares GPL-3.0-or-later; preserve the source and package license notices when redistributing these binaries.

SHA-256:

| ABI | Library | SHA-256 |
|---|---|---|
| x86_64 | `libEGL_angle.so` | `19325dde73b29f6c3594ab47ea9a343d978ed76642b0a02c00bcdf6c0990fb52` |
| x86_64 | `libGLESv1_CM_angle.so` | `365e08b7486ad6899ecfb439418ba066c07dcd3ce0b8a9d762f925c197ec6614` |
| x86_64 | `libGLESv2_angle.so` | `9f4ce826c924e22ec3863f4ee6d44e49cc3ad62c9fd2cb76ada473a4cbba3b3e` |
| x86 | `libEGL_angle.so` | `d6d6a08113f7a599aff6d0c67417d37b734b68ea5ff413d00280bddc996ddee0` |
| x86 | `libGLESv1_CM_angle.so` | `d5941f56b6f7ba00b05a70f525489e83008e3351d630f2164f1aa6cd10ecf58a` |
| x86 | `libGLESv2_angle.so` | `3f69fe02be70d16c9efb3944d6be957c7dcc75804e5024103c79de7b130bec06` |
