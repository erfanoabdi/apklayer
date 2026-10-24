// android.security.keystore.KeyStoreManager -- the API-36 keystore GRANT
// surface (@FlaggedApi android.security.keystore_grant_api): an app can hand
// another UID scoped access to one of its keystore keys, and the grantee
// retrieves it by the opaque grant id.
//
// AOSP source: frameworks/base/keystore/java/android/security/keystore/
// KeyStoreManager.java (android16-release). Obtained on a device via
// Context.getSystemService(KeyStoreManager.class).
//
// ⚠ EVERY METHOD HERE IS AN HONEST REFUSAL, and each refuses for a reason:
//
//  * grantKeyAccess / revokeKeyAccess -- a grant is a cross-UID capability
//    issued by the keystore daemon. Mac.apk runs ONE app in ONE process; there
//    is no second UID to grant to and no daemon to arbitrate. We report
//    ERROR_UNIMPLEMENTED (a system error, per AOSP's own classification)
//    instead of returning a fabricated grant id that would later resolve to
//    nothing.
//
//  * getGranted{Key,KeyPair,CertificateChainFromId} -- no grant was ever
//    issued, so no id resolves. UnrecoverableKeyException is the documented
//    failure for an id that cannot be turned into a key.
//
//  * getSupplementaryAttestationInfo -- ATTESTATION-ADJACENT, and the one to
//    be most careful with. MODULE_HASH asks the platform for a signed
//    statement about which attestation modules the device is running. We have
//    no attestation authority and will not invent that statement, so it
//    refuses with ERROR_UNIMPLEMENTED rather than returning plausible bytes.
package android.security.keystore;

import android.security.KeyStoreException;
import java.security.Key;
import java.security.KeyPair;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.List;

public final class KeyStoreManager {

    /** Tag for getSupplementaryAttestationInfo: the attestation module hash. */
    public static final int MODULE_HASH = -1879047468;

    /** Mac.apk wiring, not AOSP API -- AOSP's constructor is @hide, so a future
     *  Context.getSystemService binding needs something to call. */
    public KeyStoreManager() {}

    public long grantKeyAccess(String alias, int uid)
            throws KeyStoreException, UnrecoverableKeyException {
        throw new KeyStoreException(KeyStoreException.ERROR_UNIMPLEMENTED,
                "Keystore key grants are not available on this device");
    }

    public void revokeKeyAccess(String alias, int uid)
            throws KeyStoreException, UnrecoverableKeyException {
        throw new KeyStoreException(KeyStoreException.ERROR_UNIMPLEMENTED,
                "Keystore key grants are not available on this device");
    }

    public Key getGrantedKeyFromId(long id)
            throws KeyPermanentlyInvalidatedException, UnrecoverableKeyException {
        throw new UnrecoverableKeyException("No such grant id: " + id);
    }

    public KeyPair getGrantedKeyPairFromId(long id)
            throws KeyPermanentlyInvalidatedException, UnrecoverableKeyException {
        throw new UnrecoverableKeyException("No such grant id: " + id);
    }

    public List<X509Certificate> getGrantedCertificateChainFromId(long id)
            throws KeyPermanentlyInvalidatedException, UnrecoverableKeyException {
        throw new UnrecoverableKeyException("No such grant id: " + id);
    }

    /** See the class header: we do not fabricate attestation material. */
    public byte[] getSupplementaryAttestationInfo(int tag) throws KeyStoreException {
        throw new KeyStoreException(KeyStoreException.ERROR_UNIMPLEMENTED,
                "Supplementary attestation info is not available on this device");
    }
}
