// android.os.SecurityStateManager (API 35) -- reports the device's security
// patch levels, fetched via getSystemService(SECURITY_STATE_SERVICE).
//
// AOSP ref: frameworks/base/core/java/android/os/SecurityStateManager.java
// (android16-release). AOSP fills the Bundle from ro.build.version.security_patch
// (system SPL), the vendor SPL property, the running kernel version, and one
// entry per security-relevant package. Mac.apk answers the two it genuinely
// knows -- the system SPL is exactly what Build.VERSION.SECURITY_PATCH already
// reports, and the kernel version is the real host kernel via `uname -r`
// (host-first: no invented string) -- and OMITS the vendor SPL, because there
// is no vendor image. AOSP itself omits the vendor key on a device with no
// vendor SPL property, so an absent key is a state the API already has.
package android.os;

public class SecurityStateManager {
    public static final String KEY_SYSTEM_SPL = "system_spl";
    public static final String KEY_VENDOR_SPL = "vendor_spl";
    public static final String KEY_KERNEL_VERSION = "kernel_version";

    public SecurityStateManager() {}

    /** AOSP: @NonNull. */
    public Bundle getGlobalSecurityState() {
        Bundle b = new Bundle();
        b.putString(KEY_SYSTEM_SPL, Build.VERSION.SECURITY_PATCH);
        String kernel = kernelVersion();
        if (kernel != null) {
            b.putString(KEY_KERNEL_VERSION, kernel);
        }
        return b;
    }

    // `uname -r` is the same value /proc/version's release field carries on a
    // device, and it is a real property of the machine we are running on.
    // Cached: the kernel cannot change while the process lives.
    private static String sKernel;
    private static boolean sKernelResolved;

    private static synchronized String kernelVersion() {
        if (sKernelResolved) return sKernel;
        sKernelResolved = true;
        try {
            // java.lang.Process, fully qualified: this package declares its own
            // android.os.Process, which shadows the JDK subprocess type.
            java.lang.Process p = new ProcessBuilder("/usr/bin/uname", "-r").start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                if (line != null && !line.trim().isEmpty()) sKernel = line.trim();
            }
            p.waitFor();
        } catch (Exception e) {
            // Leave it null; the key is simply absent, which AOSP also allows.
        }
        return sKernel;
    }
}
