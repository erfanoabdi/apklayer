// Mac.apk v1.0.92 -- auto-generated stub.
// Class referenced by F-Droid's classes.jar but not previously
// implemented. Empty body so JVM-side bytecode verification of
// callers passes. NoSuchMethodError will fire lazily for any method
// callers invoke; add signatures on demand.
package android.os.ext;

public class SdkExtensions {
    public SdkExtensions() {}

    /** AOSP getExtensionVersion (API 30): the extension version for an SDK level.
     *  Mac.apk ships no mainline-module extensions, so every level reports 0. */
    public static int getExtensionVersion(int sdkVersion) { return 0; }

    /**
     * AOSP getAllExtensionVersions (API 31): every known extension SDK mapped to
     * its version. AOSP returns an unmodifiable Map and NEVER null.
     *
     * We ship no mainline-module extensions, so every known extension reports 0 --
     * the same answer {@link #getExtensionVersion(int)} already gives, kept
     * consistent so a caller cannot see the two disagree. The KEYS still have to be
     * present: callers iterate the map rather than probing levels one by one.
     *
     * Measured on Instagram 445: Facebook's lacrima crash reporter
     * (DirectReports.A01 -> X.0hn.A00) calls this during AppInit on the
     * Lacrima_background thread; the missing method threw NoSuchMethodError, which
     * is uncaught there, so the app's AOSP-style KillApplicationHandler ended the
     * process with System.exit(10). Generic -- any app that fingerprints the device
     * this way (it is the standard way to ask what mainline modules exist).
     */
    public static java.util.Map<Integer, Integer> getAllExtensionVersions() {
        java.util.Map<Integer, Integer> m = new java.util.HashMap<>();
        // The extension SDKs AOSP defines, by the Build.VERSION_CODES level that
        // introduced each: R(30), S(31), TIRAMISU(33), AD_SERVICES(33 ext), U(34),
        // V(35). Reporting 0 for each says "present, no extension revision".
        for (int level : new int[] { 30, 31, 33, 34, 35 }) m.put(level, 0);
        return java.util.Collections.unmodifiableMap(m);
    }
}

