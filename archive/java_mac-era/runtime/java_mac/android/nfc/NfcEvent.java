// android.nfc.NfcEvent -- the event object passed to the Android Beam
// callbacks (CreateNdefMessageCallback and friends).
//
// Android Beam was REMOVED from Android in API 34, and this device has no NFC
// radio to run it on, so no NfcEvent is ever constructed here -- the
// constructor is package-private, as it is on AOSP, so an app cannot fabricate
// one either. The type exists because the beam callback interfaces name it in
// their signatures, and an app implementing one of those interfaces cannot be
// DEFINED without it.
//
// Mirrors packages/modules/Nfc framework/java/android/nfc/NfcEvent.java: three
// public final fields, no methods.
package android.nfc;

public final class NfcEvent {

    public final NfcAdapter nfcAdapter;
    public final int peerLlcpMajorVersion;
    public final int peerLlcpMinorVersion;

    NfcEvent(NfcAdapter adapter, int majorVersion, int minorVersion) {
        nfcAdapter = adapter;
        peerLlcpMajorVersion = majorVersion;
        peerLlcpMinorVersion = minorVersion;
    }
}
