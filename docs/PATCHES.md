# Thin patches over copied AOSP sources (M1 seed)

All marked inline with `// apklayer patch`. Principle: keep upstream files byte-identical
where possible; patches are minimal diffs, logged here.

| File | Patch |
|---|---|
| android/util/Log.java | dropped imports/usages of RuntimeInit (wtf -> stderr), FastPrintWriter, LineBreakBufferedWriter (plain PrintWriter/BufferedWriter) |
| android/os/SystemProperties.java | Binder.clear/restoreCallingIdentity -> no-op |
| android/os/Build.java | minimal stub (constants only; upstream graph too heavy) |
| android/os/DeadObjectException.java | hand-written (upstream generated from .aidl) |
| com/android/internal/util/Preconditions.java | minimal stand-in (upstream pulls Build/TextUtils/errorprone) |
| tests/smoke/Smoke.java | apklayer-authored smoke test (apklayer.smoke package) |

Copied unmodified from los24: Base64, RemoteException, AndroidException, DeadSystemException,
DeadSystemRuntimeException, MutableInt, MutableBoolean, HexEncoding, IntDef/NonNull/Nullable/
SystemApi, UnsupportedAppUsage, GuardedBy, VisibleForTesting, Ravenwood* annotations,
FastNative/CriticalNative.
