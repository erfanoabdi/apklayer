# UiBench GL TextureView and Modern Combat 3 notes (moved from docs/PLAN.md)

## UiBench GL TextureView (2026-10-27)
- Finished the user's TextureView WIP on the real GPU route. Original TextureView/TextureLayer/SurfaceTexture JNI, EGL10/GLImpl, GLES20 and GLUtils render the original canyon bitmap through a real buffer queue and HWUI; no UiBench source changes.
- Use the actual Android libnativedisplay (already staged), removing the obsolete whole-library mapping to weak stubs. Original SF supplies display/choreographer services. EGL retains its ANativeWindow through eglDestroySurface, including EGL10's temporary Surface wrapper.
- Sandbox acquires Android's javax.microedition.khronos classes instead of outer SDK stubs. GLES20 static initialization is deferred until JNI registration. libnativehelper's NIO helpers use public Buffer methods, element types and JNI direct addresses rather than Android-only fields/NIOAccess.
- `tests/vm/texture-view.sh` passes: two captures have >60k canyon pixels, >20k changed animation pixels, >80 real dmabuf frames, physical Back reaches depth 0, no leftover runtime processes. Capture briefly SIGSTOPs submissions to avoid Parallels partial scanout reads, then always resumes. GPU dialog/resize/Back/clipboard and original Chromium WebView JavaScript probe regressions pass.

## Modern Combat 3 launch check (2026-10-27)
- User supplied Modern-Combat-3-1.1.7g APK and `com.gameloft.android.ANMP.GloftM3HM` data directory. APK converted (4960 classes), installed as MC3, and launched on the ARM64 device-library route.
- Blocked before gameplay: `UnsatisfiedLinkError: no ModernCombat3 ... (ABI not in the APK?)` from GL2JNIActivity/onResume. APK contains libgenerator.so/libModernCombat3.so for armeabi, armeabi-v7a and x86 only; no arm64-v8a or x86_64. The ARM64 JVM/bionic linker cannot load these 32-bit engines. Java/Dex conversion does not translate native instructions or pointer ABI.
- Local data contains main.1120 and patch.11428 OBBs (1,424,225,001 bytes combined). Not copied to the VM because native engine loading fails before data use. No gameplay/render/input pass claimed; supporting this APK requires a separate 32-bit execution path (and hardware/translation support), or an ARM64 build of the engine. Full VM launch log: `/tmp/apklayer-mc3.log`. App and native stack stopped; no runtime processes left.

