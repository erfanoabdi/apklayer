// android.net.ipsec.ike.IkeSessionParams
//
// ⚠ THE android.net.ipsec.ike PACKAGE IS NOT IMPLEMENTED. It is the IKEv2 key
// exchange library, shipped by AOSP as a mainline module (which is why it does
// not even appear in the android16 signature file this tree is diffed against).
// It is a large subsystem -- session/child params, SA proposals, identities,
// callbacks -- and Mac.apk cannot use ANY of it, because there is no tunnel
// interface to attach the resulting SAs to (see android.net.VpnManager).
//
// THIS FILE EXISTS FOR LINKING ONLY. android.net.Ikev2VpnProfile names
// IkeTunnelConnectionParams in two public signatures, and a class that merely
// REFERENCES those signatures cannot resolve without the type present. Nothing
// here fabricates IKE behaviour: there is deliberately NO public constructor
// and NO builder, so an app cannot obtain an instance and then be told a
// tunnel was negotiated. An app that genuinely drives IKE will fail at the
// point it tries to BUILD one, which is the truthful failure.
//
// If a future session implements this package for real, delete this note along
// with the placeholder and mirror the module's API properly.
package android.net.ipsec.ike;

public final class IkeSessionParams {

    /** No public constructor: see the file header. */
    IkeSessionParams() {}
}
