// android.security.MacApkProviderPreference -- make the password-based (PBE)
// crypto services resolve to the "BC" provider, as they do on Android.
//
// WHY (not an AOSP class, hence the MacApk prefix -- this is Mac.apk wiring):
//   On a device the provider list is AndroidOpenSSL (Conscrypt) first, then BC.
//   Conscrypt implements NO PBE SecretKeyFactory, NO PKCS#5 v1 / PKCS#12 PBE
//   Cipher and NO PBEWithHmac* Mac (google/conscrypt OpenSSLProvider.java: its
//   SecretKeyFactory table is DESEDE + SCRYPT only), so an unqualified
//   Cipher/SecretKeyFactory/Mac.getInstance("PBEWith...") is answered by BC.
//   On Mac.apk the host's SunJCE sits ahead of BC (Boot adds BC at the END, so
//   it only fills gaps) and answers those names with JDK semantics instead:
//     - PBES1 (PBEWithMD5AndDES): SunJCE throws "Salt must be 8 bytes long" for
//       any other salt; BC, i.e. Android, takes any length. Pocket Casts 8.22
//       salts with the 16-char ANDROID_ID, so its Application.onCreate died in
//       SettingsImpl.encrypt and the app opened to a black screen (issue #7).
//     - The key classes do not mix: a SunJCE PBEKey handed to BC's cipher is
//       "Algorithm requires a PBE key", so the JDK's delayed provider selection
//       cannot fall through to BC on its own.
//     - PBEWithHmacSHA* Macs give DIFFERENT bytes (SunJCE derives the key with
//       PBKDF2, BC with the PKCS#12 generator), so a MAC computed here would
//       not verify against one computed on a phone.
//
// HOW: the JDK's own per-algorithm preference, the "jdk.security.provider.
//   preferred" security property (JDK-7092821). It is read ONCE, when the
//   process-wide ProviderList is first built, so install() must run before
//   anything touches a security provider: jvm_loader.cpp calls it straight
//   after JNI_CreateJavaVM, before any of our Java runs. A preferred provider
//   that is not registered is skipped, so the entries are inert until Boot
//   adds BC. The provider LIST itself is unchanged.
//
// SCOPE: exactly the PBE-named services that SunJCE and the bundled bcprov BOTH
//   offer (the only names where SunJCE shadows BC; a name only BC has already
//   resolves to BC). Re-derived and compared against this table by
//   debug/bcpbe_gate.sh, so a bcprov or JDK update that changes the overlap
//   fails the gate instead of drifting. PBKDF2WithHmacSHA* are deliberately NOT
//   here: SunJCE and BC give identical bytes for them (measured) and SunJCE's
//   is ~5x faster on HotSpot's SHA intrinsics.
//
// MACAPK_BC_PBE_PREFERRED=0 leaves the property alone (the pre-fix behaviour),
// for one A/B.
package android.security;

import java.security.Security;

public final class MacApkProviderPreference {
    static final String PROPERTY = "jdk.security.provider.preferred";
    static final String BC = "BC";

    /** Type.Algorithm entries Android resolves to BC and SunJCE would answer. */
    static final String[] BC_PREFERRED = {
        "Cipher.PBEWithMD5AndDES",
        "SecretKeyFactory.PBEWithMD5AndDES",
        "Cipher.PBEWithSHA1AndDESede",
        "SecretKeyFactory.PBEWithSHA1AndDESede",
        "Mac.PBEWithHmacSHA1",
        "Mac.PBEWithHmacSHA224",
        "Mac.PBEWithHmacSHA256",
        "Mac.PBEWithHmacSHA384",
        "Mac.PBEWithHmacSHA512",
    };

    private MacApkProviderPreference() {}

    /** Append the BC entries to jdk.security.provider.preferred, keeping any
     *  entries already configured ahead of ours. Idempotent. */
    public static void install() {
        if ("0".equals(System.getenv("MACAPK_BC_PBE_PREFERRED"))) return;
        String cur = Security.getProperty(PROPERTY);
        StringBuilder sb = new StringBuilder();
        if (cur != null && !cur.trim().isEmpty()) sb.append(cur.trim());
        for (String e : BC_PREFERRED) {
            String entry = e + ":" + BC;
            if (cur != null && cur.contains(entry)) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(entry);
        }
        Security.setProperty(PROPERTY, sb.toString());
    }
}
