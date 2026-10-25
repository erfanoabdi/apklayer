// android.nfc.tech.Ndef -- the NDEF tag technology (read/write an NDEF message
// on a tag).
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
// NOTE the split: the NDEF BYTE FORMAT is fully implemented in
// android.nfc.NdefMessage / android.nfc.NdefRecord, because it needs no
// hardware. What is absent is only the RADIO that would move those bytes to or
// from a card -- which is why the methods here fail rather than returning
// fabricated tag contents.
//
// Mirrors packages/modules/Nfc framework/java/android/nfc/tech/Ndef.java.
package android.nfc.tech;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.Tag;

import java.io.IOException;

public final class Ndef implements TagTechnology {

    public static final String NFC_FORUM_TYPE_1 = "org.nfcforum.ndef.type1";
    public static final String NFC_FORUM_TYPE_2 = "org.nfcforum.ndef.type2";
    public static final String NFC_FORUM_TYPE_3 = "org.nfcforum.ndef.type3";
    public static final String NFC_FORUM_TYPE_4 = "org.nfcforum.ndef.type4";
    public static final String MIFARE_CLASSIC = "com.nxp.ndef.mifareclassic";

    Ndef() {}

    /** @see android.nfc.tech.NfcA#get -- same reasoning. */
    public static Ndef get(Tag tag) { return null; }

    @Override public Tag getTag() { return null; }

    @Override public boolean isConnected() { return false; }

    @Override public void connect() throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    @Override public void close() throws IOException {}

    /** AOSP returns the message captured at discovery, or null when the tag
     *  carried none. Nothing was ever discovered here. */
    public NdefMessage getCachedNdefMessage() { return null; }

    public String getType() { return null; }

    /** AOSP reports the tag's NDEF capacity. There is no tag, so 0 -- an app
     *  cannot read this as "there is room". */
    public int getMaxSize() { return 0; }

    public boolean isWritable() { return false; }

    public NdefMessage getNdefMessage() throws FormatException, IOException {
        throw new IOException("no NFC hardware on this device");
    }

    public void writeNdefMessage(NdefMessage msg) throws FormatException, IOException {
        throw new IOException("no NFC hardware on this device");
    }

    public boolean canMakeReadOnly() { return false; }

    public boolean makeReadOnly() throws IOException {
        throw new IOException("no NFC hardware on this device");
    }
}
