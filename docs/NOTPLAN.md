# Not planned (large tasks)

Moved out of `docs/PLAN.md` on 2026-11-15: each is a large piece of work for an unmeasured or small gain. Take one back into PLAN only when an app or the owner needs it.

- Translator, translated code only (acediff known-diffs `inv `, `dn `, `nn `, `default-NaN`, `vsqrtq_f`): the negative default NaN of an invalid FADD/FSUB/FMUL/FDIV/FSQRT/FMLA/FMADD, FPCR.DN ignored, two NaN operands of different kinds, NaN sign of FMSUB/FNMADD/FNMSUB. A fix-up after each operation, or a bail while DN is set, would close them at a speed cost. None explains ACE. A guest fault without a guest handler still goes to HotSpot's handler.
- Present fences signal at hand-over, not the real present: that needs an unsignaled kernel fence, and syncobj export and TRANSFER of an unsubmitted point fail (PC, Intel, 2026-11-15); remaining options are vgem or blocking `presentDisplay` until the frame callback (stalls SurfaceFlinger's HWC thread).
- Shared libraries: a native library's path in the app's linker namespace (`<uses-native-library>` itself works).
- Direct buffer `array()` writes are lost: documented limit (a copy-back would overwrite newer buffer contents; only array pinning would fix it, see `docs/PATCHES.md`).
- Hub ready is ~2.0 s with 148 packages; left: JVM main to Robolectric ready 0.8 s and the settings provider's `installProviders` 0.34 s.
- No translation cache across starts.
- Isolated processes (uid 99000+) hold no permissions and see only the hub. `useAppZygote` runs as a plain isolated process (no app zygote, no `ZygotePreload`); `externalService` (`BIND_EXTERNAL_SERVICE`) is not implemented; an isolated service of the hub's own package cannot be hosted.
- Sailfish: libhybris in every Android process, a Chum/OBS repository, no text input in the manager.
- Binder devices of a removed instance stay until reboot.
- Lomiri suspend policy (owner decided 2026-11-15: keep today's behaviour, never suspend; this is the rule to implement if wanted). Follow Android: a window that is visible on screen stays resumed and keeps rendering whether or not it has focus (multi-resume since Android 10; focus is only `onTopResumedActivityChanged`; camera and microphone follow focus in multi-window). A window that is hidden or minimised, or whose surface Lomiri occluded or suspended, gets `onPause` then `onStop`, stops producing frames and its process is frozen (cgroup freezer), unless it has a foreground service, audio playing or a call; it resumes on show. Merely unfocused windows are never suspended. Process lifetime stays a separate choice (no cached app processes: see the ground rules). Check first what Lomiri's own app lifecycle sends on the X23.
- Turned-window keyboard inset needs a framework patch (new android-all): Android treats the IME as a bottom inset (reason in CHANGELOG).
