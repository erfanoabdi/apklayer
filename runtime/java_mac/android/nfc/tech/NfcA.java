// android.nfc.tech.NfcA -- the NfcA tag technology.
//
// A Mac has NO NFC radio, so no Tag is ever dispatched: android.nfc.NfcAdapter
// .getDefaultAdapter() returns null (the AOSP answer for a device without NFC),
// which means nothing can ever hand an app a Tag to pass to get(). get()
// therefore returns null -- which is ALSO exactly what AOSP returns when the
// tag does not support the technology, so a caller already has to handle it.
//
// The constructor is package-private, as on AOSP, so an app cannot fabricate
// an instance and then be told a card responded. The instance methods below
// are consequently unreachable; the I/O ones declare and throw IOException,
// the honest "this technology is not connected" failure AOSP raises in the
// same state.
//
// The class exists so an app that NAMES the type links. That is not
// hypothetical: `implements`/field/cast references are resolved at class
// definition time, so a missing tech class aborts whatever component loaded
// it, long before any NFC code would have run.
//
// Mirrors packages/modules/Nfc framework/java/android/nfc/tech/NfcA.java.
package android.nfc.tech;

import android.nfc.Tag;

import java.io.IOException;

public final class NfcA implements TagTechnology {

    NfcA() {}

    /** Null: no Tag can exist here, and null is AOSP's own "this tag does not
     *  support NfcA" answer. */
    public static NfcA get(Tag tag) { return null; }

    @Override public Tag getTag() { return null; }

    @Override public boolean isConnected() { return false; }

    @Override public void connect() throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    @Override public void close() throws IOException {}

    public byte[] transceive(byte[] data) throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    /** AOSP reports the radio's per-technology limit. There is no radio, so
     *  zero: an app sizing a command against this cannot conclude that a
     *  larger APDU would have worked. */
    public int getMaxTransceiveLength() { return 0; }
    /** AOSP stores the value on the radio. There is none, so the setter is
     *  inert and the getter reports 0 rather than echoing a value that was
     *  never applied. */
    public void setTimeout(int timeout) {}

    public int getTimeout() { return 0; }

    /** AOSP: the tag's ATQA/SENS_RES. No tag, so no response bytes. */
    public byte[] getAtqa() { return new byte[0]; }

    /** AOSP: the tag's SAK/SEL_RES. */
    public short getSak() { return 0; }
}
