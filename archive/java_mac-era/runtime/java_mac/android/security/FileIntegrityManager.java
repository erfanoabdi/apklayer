// android.security.FileIntegrityManager -- the app-facing view of fs-verity,
// the kernel feature that hashes a read-only file into a Merkle tree so every
// page read is verified against a signed root.
//
// AOSP source: frameworks/base/core/java/android/security/FileIntegrityManager.java
// (android16-release). Public surface: isApkVeritySupported() and the
// deprecated isAppSourceCertificateTrusted(), which AOSP itself now hardcodes
// to `return false` (FileIntegrityManager.java:165-168).
//
// Mac.apk answer: fs-verity is a Linux VFS feature. APFS has no equivalent the
// app can address, so isApkVeritySupported() is false -- the honest report of a
// capability we do not have, and the same answer real devices with the feature
// disabled give. Apps use it as a feature probe and fall back to their own
// integrity checking.
package android.security;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

public final class FileIntegrityManager {

    /** Mac.apk wiring, not AOSP API: AOSP's constructor takes a Context and the
     *  IFileIntegrityService binder and is @hide, so there is nothing for
     *  Context.getSystemService to call. A public no-arg constructor is the
     *  safe direction for conformance -- an app compiled against real Android
     *  can never invoke it, since it does not exist there. */
    public FileIntegrityManager() {}

    /** fs-verity is not available on macOS. */
    public boolean isApkVeritySupported() { return false; }

    /** @deprecated AOSP returns false unconditionally as of API 33+. */
    @Deprecated
    public boolean isAppSourceCertificateTrusted(X509Certificate certificate)
            throws CertificateEncodingException {
        return false;
    }
}
