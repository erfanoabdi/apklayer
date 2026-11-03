package hello.system;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

/** PackageManager.hasSigningCertificate compares the real certificates: the app's own (raw and SHA-256) matches, other bytes do not; microG's spoofed one matches when installed. */
final class SigningCheck implements Check {
    @Override public String run(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        String self = context.getPackageName();
        Signature[] own = pm.getPackageInfo(self, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.getApkContentsSigners();
        byte[] cert = own[0].toByteArray(), sha = java.security.MessageDigest.getInstance("SHA-256").digest(cert);
        Check.require(pm.hasSigningCertificate(self, cert, PackageManager.CERT_INPUT_RAW_X509), "own raw certificate not matched");
        Check.require(pm.hasSigningCertificate(self, sha, PackageManager.CERT_INPUT_SHA256), "own SHA-256 not matched");
        Check.require(pm.hasSigningCertificate(android.os.Process.myUid(), cert, PackageManager.CERT_INPUT_RAW_X509), "own uid not matched");
        byte[] other = cert.clone();
        other[other.length / 2] ^= 1;
        Check.require(!pm.hasSigningCertificate(self, other, PackageManager.CERT_INPUT_RAW_X509), "a different certificate matched");
        String gms = "not installed";
        try {
            android.os.Bundle meta = pm.getApplicationInfo("com.google.android.gms", PackageManager.GET_META_DATA).metaData;
            String fake = meta == null ? null : meta.getString("fake-signature");
            if (fake != null) {
                Check.require(pm.hasSigningCertificate("com.google.android.gms", new Signature(fake).toByteArray(), PackageManager.CERT_INPUT_RAW_X509), "microG's spoofed certificate not matched");
                Check.require(!pm.hasSigningCertificate("com.google.android.gms", cert, PackageManager.CERT_INPUT_RAW_X509), "microG matched this app's certificate");
                gms = "spoofed certificate matched";
            }
        } catch (PackageManager.NameNotFoundException absent) { /* microG not installed in this instance */ }
        return "own certificate matched, a different one refused; microG: " + gms;
    }
}
