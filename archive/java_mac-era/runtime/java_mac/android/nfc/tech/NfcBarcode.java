// android.nfc.tech.NfcBarcode -- the Kovio/Thinfilm barcode tag technology.
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
// The class exists so an app that NAMES the type links -- `implements`/field/
// cast references are resolved at class definition time, so a missing tech
// class aborts whatever component loaded it, long before any NFC code runs.
//
// Mirrors packages/modules/Nfc framework/java/android/nfc/tech/NfcBarcode.java.
package android.nfc.tech;

import android.nfc.Tag;

import java.io.IOException;

public final class NfcBarcode implements TagTechnology {

    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_KOVIO = 1;

    NfcBarcode() {}

    /** @see android.nfc.tech.NfcA#get -- same reasoning. */
    public static NfcBarcode get(Tag tag) { return null; }

    @Override public Tag getTag() { return null; }

    @Override public boolean isConnected() { return false; }

    @Override public void connect() throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    @Override public void close() throws IOException {}

    public int getType() { return TYPE_UNKNOWN; }

    /** No tag, so no barcode bytes. */
    public byte[] getBarcode() { return null; }
}
