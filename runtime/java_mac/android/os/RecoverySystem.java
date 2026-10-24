// android.os.RecoverySystem -- access to the recovery partition for OTA package
// verification + install + factory reset. Every operation here requires
// privileged system access that does not exist on Mac.apk's host, so we mirror
// AOSP's public surface and fail the way an unprivileged caller would: install /
// reboot paths throw IOException/SecurityException, verifyPackage runs the
// listener to completion (no real signature check). Apps almost never call this;
// the type is provided so references resolve.
//
// AOSP ref: frameworks/base/core/java/android/os/RecoverySystem.java
package android.os;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class RecoverySystem {
    public RecoverySystem() {}

    public interface ProgressListener {
        void onProgress(int progress);
    }

    // AOSP verifies the package against the OTA signing certs. We have no
    // recovery cert store, so we drive the listener to 100% and return without a
    // real cryptographic check. Callers that rely on this for security would not
    // be functional on a non-Android host regardless.
    public static void verifyPackage(File packageFile, ProgressListener listener,
                                     File deviceCertsZipFile)
            throws IOException, GeneralSecurityException {
        if (packageFile == null || !packageFile.exists()) {
            throw new IOException("package not found");
        }
        if (listener != null) {
            listener.onProgress(0);
            listener.onProgress(100);
        }
    }

    public static void installPackage(Context context, File packageFile) throws IOException {
        throw new IOException("recovery install unsupported on this host");
    }

    public static void processPackage(Context context, File packageFile,
                                      ProgressListener listener) throws IOException {
        throw new IOException("recovery install unsupported on this host");
    }

    public static void rebootWipeUserData(Context context) throws IOException {
        throw new IOException("factory reset unsupported on this host");
    }

    public static void rebootWipeCache(Context context) throws IOException {
        throw new IOException("cache wipe unsupported on this host");
    }

    public static String handleAftermath(Context context) { return null; }
}
