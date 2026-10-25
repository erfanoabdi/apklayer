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

## M1b additions (message loop)
- android/os/MessageQueue.java (from LegacyMessageQueue): native epoll bridge -> JVM
  wait/notify poll; fd-event natives -> no-ops; fd.getInt$() -> identityHashCode.
- android/os/SystemProperties.java, android/os/Trace.java: all `static native` decls
  stripped to JVM-safe defaults.
- android/os/Looper.java: CompatChanges->false; Perfetto blocks removed; LooperDoctor off;
  dumpDebug(proto) removed.
- android/os/Handler.java: IMessenger/Messenger block removed.
- android/os/Message.java: Parcelable + CREATOR + writeToParcel/readFromParcel + proto dump
  removed; TimeUtils.formatDuration -> plain ms.
- android/os/HandlerThread.java: HandlerExecutor -> inline lambda executor.
- New apklayer stubs (patch-marked): Binder, Process, SystemClock, Bundle, Messenger,
  ThreadLocalWorkSource, StrictMode, dalvik.system.VMRuntime, android.os.Flags,
  PerfettoCategories, com.android.internal.dev.perfetto.sdk.PerfettoTrace, LooperDoctor,
  android.os.Build (constants), DeadObjectException.
- Copied unmodified (M1b): Looper, Handler, HandlerThread, Message, LegacyMessageQueue
  MessageQueue, Pools, Trace*, SparseArray, ArraySet, MapCollections, UtilConfig,
  ContainerHelpers, EmptyArray, ArrayUtils, GrowingArrayUtils, Slog, Printer,
  TraceNameSupplier, ChangeId/EnabledSince/Overridable, FlaggedApi/TestApi/SuppressLint,
  NeverCompile, RavenwoodReplace (+ earlier seed). (* = natives stripped)
