package android.os;
public final class StrictMode {
    public static final class ThreadPolicy {
        public static final ThreadPolicy LAX = new ThreadPolicy();
        public static final class Builder {
            public Builder() {}
            public Builder(ThreadPolicy policy) {}
            public Builder detectAll() { return this; }
            public Builder detectDiskReads() { return this; }
            public Builder detectDiskWrites() { return this; }
            public Builder detectNetwork() { return this; }
            public Builder permitAll() { return this; }
            public Builder permitDiskReads() { return this; }
            public Builder permitDiskWrites() { return this; }
            public Builder permitNetwork() { return this; }
            public Builder penaltyLog() { return this; }
            public Builder penaltyDeath() { return this; }
            public Builder penaltyDeathOnNetwork() { return this; }
            public Builder penaltyDropBox() { return this; }
            public Builder penaltyDialog() { return this; }
            public Builder penaltyFlashScreen() { return this; }
            // v1.0.110: API 26+. Ktor's CIO engine touches this when
            // wiring up its non-blocking-IO socket pipeline; Glide's
            // F-Droid pipeline routes through Ktor for icon fetching,
            // so this must be a no-op (not an error). AOSP:
            //   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/os/StrictMode.java
            //   (look for permitUnbufferedIo)
            public Builder permitUnbufferedIo() { return this; }
            public Builder detectUnbufferedIo() { return this; }
            public Builder permitResourceMismatches() { return this; }
            public Builder detectResourceMismatches() { return this; }
            public Builder permitCustomSlowCalls() { return this; }
            public Builder detectCustomSlowCalls() { return this; }
            public Builder detectExplicitGc() { return this; }
            public Builder permitExplicitGc() { return this; }
            public Builder penaltyListener(java.util.concurrent.Executor exec, OnThreadViolationListener l) { return this; }
            public ThreadPolicy build() { return LAX; }
        }
    }
    public static final class VmPolicy {
        public static final VmPolicy LAX = new VmPolicy();
        public static final class Builder {
            public Builder() {}
            public Builder(VmPolicy policy) {}
            public Builder detectAll() { return this; }
            public Builder detectLeakedSqlLiteObjects() { return this; }
            public Builder detectLeakedClosableObjects() { return this; }
            public Builder detectLeakedRegistrationObjects() { return this; }
            public Builder detectActivityLeaks() { return this; }
            public Builder detectFileUriExposure() { return this; }
            public Builder penaltyLog() { return this; }
            public Builder penaltyDeath() { return this; }
            public Builder penaltyDropBox() { return this; }
            public Builder detectCleartextNetwork() { return this; }
            public Builder permitCleartextNetwork() { return this; }
            public Builder detectContentUriWithoutPermission() { return this; }
            public Builder detectUntaggedSockets() { return this; }
            public Builder detectImplicitDirectBoot() { return this; }
            public Builder detectCredentialProtectedWhileLocked() { return this; }
            public Builder detectIncorrectContextUse() { return this; }
            public Builder detectUnsafeIntentLaunch() { return this; }
            // API 31 explicit opt-out companion (AOSP body:
            // disable(DETECT_VM_UNSAFE_INTENT_LAUNCH)). A shared
            // androidx/Play-adjacent StrictMode setup snippet chains
            // detectUnsafeIntentLaunch()+permitUnsafeIntentLaunch() in one
            // fluent Builder; a missing method here aborted the whole chain,
            // including the terminal build()/setVmPolicy() calls.
            public Builder permitUnsafeIntentLaunch() { return this; }
            // AOSP core/java/android/os/StrictMode.java VmPolicy.Builder --
            // the remaining public detect*/permit*/penalty* selectors. Like
            // every other builder method in this class they only set a bit in
            // the policy mask, and Mac.apk installs no violation detectors --
            // build() returns the LAX policy regardless. So these are the same
            // chainable no-op every sibling already is, not a new special case.
            public Builder detectBlockedBackgroundActivityLaunch() { return this; }
            public Builder ignoreBlockedBackgroundActivityLaunch() { return this; }
            public Builder detectNonSdkApiUsage() { return this; }
            public Builder permitNonSdkApiUsage() { return this; }
            public Builder penaltyDeathOnCleartextNetwork() { return this; }
            public Builder penaltyDeathOnFileUriExposure() { return this; }
            /** AOSP: "Set an upper bound on how many instances of a class can
             *  be in memory at once." Chainable like the rest. */
            public Builder setClassInstanceLimit(Class<?> klass, int instanceLimit) { return this; }
            public Builder penaltyListener(java.util.concurrent.Executor exec, OnVmViolationListener l) { return this; }
            public VmPolicy build() { return LAX; }
        }
    }
    public interface OnThreadViolationListener { void onThreadViolation(Violation v); }
    public interface OnVmViolationListener { void onVmViolation(Violation v); }
    public static class Violation extends Throwable {
        public Violation() { super(); }
        public Violation(String s) { super(s); }
    }
    public static void setThreadPolicy(ThreadPolicy policy) {}
    public static void setVmPolicy(VmPolicy policy) {}
    public static ThreadPolicy getThreadPolicy() { return ThreadPolicy.LAX; }
    public static VmPolicy getVmPolicy() { return VmPolicy.LAX; }
    public static ThreadPolicy allowThreadDiskReads() { return ThreadPolicy.LAX; }
    public static ThreadPolicy allowThreadDiskWrites() { return ThreadPolicy.LAX; }
    public static void enableDefaults() {}
    // Detected-slow-call hooks (no-op without an active StrictMode policy).
    // AOSP records these against detectCustomSlowCalls(); Reddit's onCreate
    // wraps work in StrictMode.noteSlowCall(...).
    public static void noteSlowCall(String name) {}
    public static void noteSlowCall(String name, long durationMillis) {}
    public static void noteDiskRead() {}
    public static void noteDiskWrite() {}
    public static void setThreadPolicyMask(int policyMask) {}

    /** AOSP StrictMode.disableDeathOnFileUriExposure() -- core/java/android/os/
     *  StrictMode.java:1549, android14-release. Its body clears two bits of the
     *  process VmPolicy:
     *      sVmPolicy = new VmPolicy(
     *          sVmPolicy.mask & ~(DETECT_VM_FILE_URI_EXPOSURE
     *                             | PENALTY_DEATH_ON_FILE_URI_EXPOSURE),
     *          sVmPolicy.classInstanceLimit, sVmPolicy.mListener,
     *          sVmPolicy.mCallbackExecutor);
     *  i.e. "stop killing me for handing out a file:// Uri".
     *
     *  A NO-OP HERE IS THE FAITHFUL TRANSLATION, not a shortcut: this StrictMode
     *  carries no policy mask at all -- VmPolicy.Builder.build() returns LAX and
     *  setVmPolicy is empty (see above), so nothing in this runtime ever detects
     *  file-Uri exposure and nothing ever penalises it. Clearing bits of a mask
     *  that does not exist would change no observable behaviour. Callers use it
     *  for its side effect and ignore the (void) return.
     *
     *  Marked @hide/@UnsupportedAppUsage in AOSP but reached by real apps
     *  through reflection -- which is exactly how the 2026-08-27 fleet census
     *  found it: klooni's log carried
     *  `NoSuchMethodException: android.os.StrictMode.disableDeathOnFileUriExposure()`.
     *  Declaring it turns that reflective probe from a miss into a hit. */
    public static void disableDeathOnFileUriExposure() {}
    private StrictMode() {}
}
