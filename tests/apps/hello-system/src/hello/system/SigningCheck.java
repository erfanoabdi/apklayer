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
        // this app names Google's certificate in "fake-signature" too, but it is not microG (LineageOS spoofs only microG-signed gms and vending): its own shows
        Signature google = new Signature(pm.getApplicationInfo(self, PackageManager.GET_META_DATA).metaData.getString("fake-signature"));
        Check.require(!pm.getPackageInfo(self, PackageManager.GET_SIGNATURES).signatures[0].equals(google) && !own[0].equals(google), "this app was spoofed with its fake-signature");
        String gms = "not installed";
        try {
            android.os.Bundle meta = pm.getApplicationInfo("com.google.android.gms", PackageManager.GET_META_DATA).metaData;
            String fake = meta == null ? null : meta.getString("fake-signature");
            if (fake != null) {
                Check.require(pm.hasSigningCertificate("com.google.android.gms", new Signature(fake).toByteArray(), PackageManager.CERT_INPUT_RAW_X509), "microG's spoofed certificate not matched");
                Check.require(!pm.hasSigningCertificate("com.google.android.gms", cert, PackageManager.CERT_INPUT_RAW_X509), "microG matched this app's certificate");
                // what GooglePlayServicesUtilLight compares: one signature (GET_SIGNATURES), Google's, and the Play Store (microG Companion) signed alike
                Signature[] services = pm.getPackageInfo("com.google.android.gms", PackageManager.GET_SIGNATURES).signatures;
                Check.require(services.length == 1 && services[0].equals(google), "microG's GET_SIGNATURES is not Google's certificate");
                gms = "spoofed certificate matched";
                Signature[] store = pm.getPackageInfo("com.android.vending", PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.GET_SIGNATURES).signatures;
                Check.require(store.length == 1 && store[0].equals(services[0]), "microG Companion not signed as microG Services");
                gms += ", Companion too";
            }
        } catch (PackageManager.NameNotFoundException absent) { /* microG (or its Companion) not installed in this instance */ }
        // hello.rotated rotated its key (v3 lineage, as Google Meet): GET_SIGNATURES shows the oldest key (what microG's list of Google apps names), the
        // signing info the current one and the history, and either key matches it
        android.content.pm.SigningInfo rotated = pm.getPackageInfo("hello.rotated", PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
        Signature[] legacy = pm.getPackageInfo("hello.rotated", PackageManager.GET_SIGNATURES).signatures, history = rotated.getSigningCertificateHistory();
        Check.require(rotated.hasPastSigningCertificates() && history.length == 2, "hello.rotated has no signing history");
        Check.require(history[1].equals(rotated.getApkContentsSigners()[0]) && !history[0].equals(history[1]), "hello.rotated's history does not end in its current key");
        Check.require(legacy.length == 1 && legacy[0].equals(history[0]), "GET_SIGNATURES of hello.rotated is not its oldest key");
        Check.require(pm.hasSigningCertificate("hello.rotated", history[0].toByteArray(), PackageManager.CERT_INPUT_RAW_X509)
                && pm.hasSigningCertificate("hello.rotated", history[1].toByteArray(), PackageManager.CERT_INPUT_RAW_X509), "a key of hello.rotated not matched");
        // the framework's certificate is one per device: the same in every process of the app
        String platform = OtherProcess.platformCertificate(context), inOther = OtherProcess.ask(context)[1];
        Check.require(platform.equals(inOther), "android certificate " + platform + " here, " + inOther + " in :other");
        return "own certificate matched, a different one refused; rotated key: oldest in GET_SIGNATURES; microG: " + gms + "; android " + platform;
    }
}
