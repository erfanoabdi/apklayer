// android.nfc.tech.NdefFormatable -- formats a blank tag as NDEF.
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
// Mirrors packages/modules/Nfc framework/java/android/nfc/tech/NdefFormatable.java.
package android.nfc.tech;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.Tag;

import java.io.IOException;

public final class NdefFormatable implements TagTechnology {

    NdefFormatable() {}

    /** @see android.nfc.tech.NfcA#get -- same reasoning. */
    public static NdefFormatable get(Tag tag) { return null; }

    @Override public Tag getTag() { return null; }

    @Override public boolean isConnected() { return false; }

    @Override public void connect() throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    @Override public void close() throws IOException {}

    public void format(NdefMessage firstMessage) throws FormatException, IOException {
        throw new IOException("no NFC hardware on this device");
    }

    public void formatReadOnly(NdefMessage firstMessage) throws FormatException, IOException {
        throw new IOException("no NFC hardware on this device");
    }
}
