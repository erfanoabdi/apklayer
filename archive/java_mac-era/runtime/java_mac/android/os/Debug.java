// android.os.Debug -- diagnostic / memory-info / debugger APIs.
// Mac.apk impl: we don't have a real debugger pipeline, so most
// methods return safe defaults. Apps that call these are typically
// doing dev-mode self-instrumentation.
package android.os;

public class Debug {
    public Debug() {}

    public static final int TRACE_COUNT_ALLOCS = 1;
    public static final int SHOW_FULL_DETAIL = 1;
    public static final int SHOW_CLASSLOADER = 2;
    public static final int SHOW_INITIALIZED = 4;

    // DIAGNOSTIC (MACAPK_DEBUG_PROBE): pairip's integrity VM FindClass-loads
    // android.os.Debug right before the +0x440c4 crash; log which method it
    // actually invokes so we can tell anti-debug (isDebuggerConnected/
    // threadCpuTimeNanos) from something else. Body-only edits, no shape change.
    private static void pp(String m) {
        if (System.getenv("MACAPK_DEBUG_PROBE") != null)
            System.err.println("[pairip-debug-probe] android.os.Debug." + m);
    }
    public static boolean isDebuggerConnected() { pp("isDebuggerConnected"); return false; }
    public static boolean waitingForDebugger() { pp("waitingForDebugger"); return false; }
    public static void waitForDebugger() { pp("waitForDebugger"); }
    public static long getNativeHeapSize() { pp("getNativeHeapSize"); return 0; }
    public static long getNativeHeapAllocatedSize() { pp("getNativeHeapAllocatedSize"); return 0; }
    public static long getNativeHeapFreeSize() { pp("getNativeHeapFreeSize"); return 0; }
    public static long getPss() { pp("getPss"); return 0; }
    public static long threadCpuTimeNanos() { pp("threadCpuTimeNanos"); return android.os.SystemClock.elapsedRealtimeNanos(); }
    // AOSP: public static String getRuntimeStat(String statName) delegates to
    // VMDebug.getRuntimeStat(statName) and returns a decimal-formatted String
    // (frameworks/base/core/java/android/os/Debug.java), not a primitive --
    // matches the string-typed convention this file already uses for the
    // adjacent getRuntimeStats() below.
    public static String getRuntimeStat(String stat) { return "0"; }
    public static java.util.Map<String, String> getRuntimeStats() { return java.util.Collections.emptyMap(); }
    public static void getMemoryInfo(MemoryInfo memoryInfo) {}
    /** AOSP :2112 -- `VMDebug.getLoadedClassCount()`, "the number of loaded
     *  classes". HotSpot answers the same question through
     *  ClassLoadingMXBean.getLoadedClassCount(), so read the real number
     *  instead of 0 (host-first: java.lang.management is in the bundled JRE --
     *  jdk.management is asserted by scripts/dist_parity.sh). */
    public static int getLoadedClassCount() {
        try {
            return java.lang.management.ManagementFactory
                    .getClassLoadingMXBean().getLoadedClassCount();
        } catch (Throwable t) {
            return 0;
        }
    }
    public static int getBinderSentTransactions() { return 0; }
    public static int getBinderReceivedTransactions() { return 0; }
    public static int getBinderDeathObjectCount() { return 0; }
    public static int getBinderProxyObjectCount() { return 0; }
    public static int getBinderLocalObjectCount() { return 0; }
    // AOSP: all eight of these are `@Deprecated public static int` (legacy
    // Dalvik VMDebug.getAllocCount() counters), not long -- confirmed against
    // frameworks/base/core/java/android/os/Debug.java. A `long`-returning
    // version here is a different descriptor and never binds to a guest's
    // invokevirtual against the real signature (NoSuchMethodError), caught by
    // debug/framework_lint.py's return-type-diff check.
    public static int getGlobalAllocCount() { return 0; }
    public static int getGlobalAllocSize() { return 0; }
    public static int getGlobalFreedCount() { return 0; }
    public static int getGlobalFreedSize() { return 0; }
    public static int getGlobalGcInvocationCount() { return 0; }
    public static int getThreadAllocCount() { return 0; }
    public static int getThreadAllocSize() { return 0; }
    public static int getThreadGcInvocationCount() { return 0; }
    public static void resetAllCounts() {}
    public static void startAllocCounting() {}
    public static void stopAllocCounting() {}
    public static void startMethodTracing() {}
    public static void startMethodTracing(String traceName) {}
    public static void startMethodTracing(String tracePath, int bufferSize) {}
    public static void startMethodTracing(String tracePath, int bufferSize, int flags) {}
    public static void startMethodTracingSampling(String tracePath, int bufferSize, int intervalUs) {}
    public static void stopMethodTracing() {}
    public static void startNativeTracing() {}
    public static void stopNativeTracing() {}

    // ---- AOSP compat no-ops. Every one of these is documented upstream as
    // "This method exists for compatibility and has no effect" (the alloc-
    // counting feature was removed in Honeycomb), and the AOSP body really is
    // `{}` / `return 0` / `return -1`. Citations are line numbers in
    // core/java/android/os/Debug.java (android16-release).
    /** AOSP :1654 -- `return 0;` */
    @Deprecated public static int getGlobalExternalAllocCount() { return 0; }
    /** AOSP -- `return 0;` alongside :1654. */
    @Deprecated public static int getGlobalExternalAllocSize() { return 0; }
    @Deprecated public static int getGlobalExternalFreedCount() { return 0; }
    @Deprecated public static int getGlobalExternalFreedSize() { return 0; }
    @Deprecated public static int getThreadExternalAllocCount() { return 0; }
    @Deprecated public static int getThreadExternalAllocSize() { return 0; }
    /** AOSP :1663 etc -- `{}` */
    @Deprecated public static void resetGlobalExternalAllocCount() {}
    @Deprecated public static void resetGlobalExternalAllocSize() {}
    @Deprecated public static void resetGlobalExternalFreedCount() {}
    @Deprecated public static void resetGlobalExternalFreedSize() {}
    @Deprecated public static void resetThreadExternalAllocCount() {}
    @Deprecated public static void resetThreadExternalAllocSize() {}
    /** AOSP :2082 / :2095 -- "always returns -1 and has no effect". */
    @Deprecated public static int setAllocationLimit(int limit) { return -1; }
    @Deprecated public static int setGlobalAllocationLimit(int limit) { return -1; }
    /** AOSP :1143 -- `public static void changeDebugPort(int port) {}` */
    @Deprecated public static void changeDebugPort(int port) {}
    /** AOSP :1233 -- logs "Unimplemented" and does nothing; the qemu sysfs
     *  file it would poke does not exist off an emulator. */
    @Deprecated public static void enableEmulatorTraceOutput() {}

    // AOSP :1511 etc reset the VMDebug counters. Our counters are constant 0
    // (there is no ART alloc-counting instrumentation on HotSpot), so a reset
    // is genuinely a no-op rather than a swallowed request.
    @Deprecated public static void resetGlobalAllocCount() {}
    @Deprecated public static void resetGlobalAllocSize() {}
    @Deprecated public static void resetGlobalFreedCount() {}
    @Deprecated public static void resetGlobalFreedSize() {}
    @Deprecated public static void resetGlobalGcInvocationCount() {}
    @Deprecated public static void resetGlobalClassInitCount() {}
    @Deprecated public static void resetGlobalClassInitTime() {}
    @Deprecated public static void resetThreadAllocCount() {}
    @Deprecated public static void resetThreadAllocSize() {}
    @Deprecated public static void resetThreadGcInvocationCount() {}

    /** AOSP :1611 / :1633 -- VMDebug KIND_GLOBAL_CLASS_INIT_COUNT / _TIME.
     *  HotSpot exposes no per-class-init counter (ClassLoadingMXBean counts
     *  LOADED classes, which is a different question -- see
     *  getLoadedClassCount below), and ART itself reports 0 for these unless
     *  alloc counting is on. */
    @Deprecated public static int getGlobalClassInitCount() { return 0; }
    @Deprecated public static int getGlobalClassInitTime() { return 0; }

    /** AOSP :2104 -- `VMDebug.printLoadedClasses(flags)`, which dumps every
     *  loaded class to the log. HotSpot has no equivalent reachable from
     *  in-process Java (it needs -Xlog:class+load at VM start, and this VM is
     *  already up), so there is nothing to print. Deliberately silent rather
     *  than emitting a fake listing. */
    public static void printLoadedClasses(int flags) {}

    /** AOSP :2555 -- looks the service up in ServiceManager and, when it is
     *  not registered, logs "Can't find service to dump" and returns false.
     *  Mac.apk registers no system services in a binder ServiceManager (there
     *  is no binder), so every name takes that branch. */
    public static boolean dumpService(String name, java.io.FileDescriptor fd, String[] args) {
        return false;
    }

    /** AOSP :2688 -- `VMDebug.attachAgent(library[+"="+options], loader)`.
     *  JVMTI agents cannot be attached to a running HotSpot from inside the
     *  same process without the attach API and a matching agent library, and
     *  a guest-supplied .so is an ELF that would not load as a JVMTI agent on
     *  macOS anyway. AOSP declares `throws IOException` for exactly the "could
     *  not attach" case, so that is the honest answer -- NOT a silent no-op,
     *  which would tell the caller its agent is running when it is not. The
     *  two Preconditions checks are AOSP's own and come first. */
    public static void attachJvmtiAgent(String library, String options, ClassLoader classLoader)
            throws java.io.IOException {
        if (library == null) throw new NullPointerException("library");
        if (library.contains("=")) {
            throw new IllegalArgumentException("Argument library cannot contain '='");
        }
        throw new java.io.IOException(
                "JVMTI agent attach is not supported by the Mac.apk runtime");
    }

    /** AOSP :1992 -- native; `android_os_Debug_getRssPid` sums memtrack
     *  graphics memory with /proc/<pid>/status VmRSS, and VmRSS is in kB, so
     *  the return unit is kB. macOS has no /proc; `ps -o rss=` reports the
     *  same quantity (resident set size in kB) for a pid, so read it from the
     *  real host rather than answering 0. 1s TTL because an app may poll this
     *  every frame and each call is a fork+exec. */
    public static long getRss() {
        long now = System.nanoTime();
        synchronized (Debug.class) {
            if (sRssKb >= 0 && (now - sRssAt) < 1_000_000_000L) return sRssKb;
        }
        long kb = 0L;
        try {
            java.lang.Process p = new ProcessBuilder(
                    "/bin/ps", "-o", "rss=", "-p", Integer.toString(Process.myPid()))
                    .redirectErrorStream(false).start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                if (line != null) {
                    line = line.trim();
                    if (!line.isEmpty()) kb = Long.parseLong(line);
                }
            }
            p.waitFor();
        } catch (Exception e) {
            // AOSP returns 0 "if retrieval failed (i.e. the PID is gone)".
            kb = 0L;
        }
        synchronized (Debug.class) { sRssKb = kb; sRssAt = now; }
        return kb;
    }
    private static long sRssKb = -1L;
    private static long sRssAt;

    public static class MemoryInfo implements Parcelable {
        public int dalvikPss;
        public int dalvikPrivateDirty;
        public int dalvikSharedDirty;
        public int nativePss;
        public int nativePrivateDirty;
        public int nativeSharedDirty;
        public int otherPss;
        public int otherPrivateDirty;
        public int otherSharedDirty;
        public MemoryInfo() {}
        public int getTotalPss() { return dalvikPss + nativePss + otherPss; }
        public int getTotalPrivateDirty() { return dalvikPrivateDirty + nativePrivateDirty + otherPrivateDirty; }
        public int getTotalSharedDirty() { return dalvikSharedDirty + nativeSharedDirty + otherSharedDirty; }
        public int getTotalPrivateClean() { return 0; }
        public int getTotalSharedClean() { return 0; }
        public int getTotalSwappablePss() { return 0; }
        public String getMemoryStat(String stat) { return null; }
        public java.util.Map<String, String> getMemoryStats() { return java.util.Collections.emptyMap(); }
        public boolean hasSwappedOutPss() { return false; }

        // AOSP :916/:948 -- MemoryInfo is Parcelable. Its upstream wire format
        // carries 28 ints + an int[] of per-category stats, but 19 of those
        // fields plus otherStats are @hide, so they do not exist on this
        // class; writing them would mean inventing values. The format below is
        // the nine PUBLIC fields in AOSP's own relative order. That is safe
        // here and only here: Mac.apk has no binder and no second process, so
        // a Parcel is never read by anything but this same class in this same
        // VM -- the contract that must hold is writeToParcel <-> CREATOR
        // agreement, which debug/parcel_roundtrip.sh gates.
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(dalvikPss);
            dest.writeInt(dalvikPrivateDirty);
            dest.writeInt(dalvikSharedDirty);
            dest.writeInt(nativePss);
            dest.writeInt(nativePrivateDirty);
            dest.writeInt(nativeSharedDirty);
            dest.writeInt(otherPss);
            dest.writeInt(otherPrivateDirty);
            dest.writeInt(otherSharedDirty);
        }
        /** AOSP :948 -- an INSTANCE method that refills `this` in place, not a
         *  factory. Callers reuse one MemoryInfo across many reads. */
        public void readFromParcel(Parcel source) {
            dalvikPss = source.readInt();
            dalvikPrivateDirty = source.readInt();
            dalvikSharedDirty = source.readInt();
            nativePss = source.readInt();
            nativePrivateDirty = source.readInt();
            nativeSharedDirty = source.readInt();
            otherPss = source.readInt();
            otherPrivateDirty = source.readInt();
            otherSharedDirty = source.readInt();
        }
        public static final Parcelable.Creator<MemoryInfo> CREATOR =
                new Parcelable.Creator<MemoryInfo>() {
            public MemoryInfo createFromParcel(Parcel source) {
                MemoryInfo mi = new MemoryInfo();
                mi.readFromParcel(source);
                return mi;
            }
            public MemoryInfo[] newArray(int size) { return new MemoryInfo[size]; }
        };
    }

    public static void dumpHprofData(String fileName) throws java.io.IOException { }

    /** AOSP core/java/android/os/Debug.java:2279 -- @Deprecated, and its
     *  bodies really are `return false` / `return 0` on a real device too (the
     *  instruction-counting instrumentation was removed from the runtime; the
     *  class is kept for source compatibility). Verbatim. */
    @Deprecated
    public static class InstructionCount {
        public InstructionCount() {
        }
        public boolean resetAndStart() { return false; }
        public boolean collect() { return false; }
        public int globalTotal() { return 0; }
        public int globalMethodInvocations() { return 0; }
    }
}

