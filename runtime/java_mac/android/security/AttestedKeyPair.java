// android.security.AttestedKeyPair -- a generated key pair together with the
// attestation certificate chain that proves where it lives.
//
// AOSP source: frameworks/base/keystore/java/android/security/AttestedKeyPair.java
// (android16-release). A pure value holder with two getters; the CONSTRUCTOR is
// public API, so app / DPC code that builds one (or, far more often, receives
// one from DevicePolicyManager.generateKeyPair) links against it.
//
// ⚠ SCOPE NOTE, and it is the important part of this file: Mac.apk NEVER
// produces one of these with a real attestation record. Hardware key
// attestation is a signed statement, rooted in a Google-provisioned device key,
// that the private key lives in certified secure hardware. macOS's Secure
// Enclave cannot produce that statement and we will not forge one -- a
// self-signed chain presented as an attestation record is precisely the lie
// this project forbids. So the type exists and behaves correctly as a
// container, and the paths that would MINT one (DevicePolicyManager.generate-
// KeyPair with an attestation challenge) fail the way a device without
// attestation support fails. See getAttestationRecord() below.
package android.security;

import java.security.KeyPair;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AttestedKeyPair {
    private final KeyPair mKeyPair;
    private final List<Certificate> mAttestationRecord;

    public AttestedKeyPair(KeyPair keyPair, List<Certificate> attestationRecord) {
        mKeyPair = keyPair;
        mAttestationRecord = attestationRecord != null
                ? Collections.unmodifiableList(new ArrayList<>(attestationRecord))
                : Collections.<Certificate>emptyList();
    }

    /** The generated pair, or null if generation failed. */
    public KeyPair getKeyPair() { return mKeyPair; }

    /** The attestation chain as supplied to the constructor. AOSP returns an
     *  EMPTY list when the key was generated without an attestation challenge,
     *  and that is the only shape Mac.apk can ever hand back honestly: we have
     *  no attestation authority, so we never fabricate a chain. */
    public List<Certificate> getAttestationRecord() { return mAttestationRecord; }
}
