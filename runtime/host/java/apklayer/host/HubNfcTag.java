package apklayer.host;

import android.nfc.INfcTag;
import android.nfc.NdefMessage;
import android.nfc.Tag;
import android.nfc.TransceiveResult;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * One tag in the field, as NfcService's tag service answers for it (INfcTag): the Tag the apps get carries this binder, so IsoDep, NfcA, MifareUltralight and Ndef
 * of the framework run unchanged over it. The air interface is a {@link Link} (a mock tag's memory, or a PC/SC reader's card).
 */
final class HubNfcTag extends INfcTag.Stub {
    /** Raw frames to the tag (an APDU for ISO-DEP, a Type 2 command for NfcA); an IOException is a failed exchange. */
    interface Link { byte[] transmit(byte[] command) throws IOException; }

    private static final AtomicInteger HANDLES = new AtomicInteger(1);
    private static final int RESULT_SUCCESS = 0, RESULT_FAILURE = 1, RESULT_TAGLOST = 2;  // TransceiveResult
    final Tag tag;
    final boolean hasNdef;
    private final Link link;
    private final Consumer<NdefMessage> store;  // writes a message into the tag's memory; null: read-only
    private volatile NdefMessage ndef;
    private volatile boolean present = true;
    private final int[] techs;

    /** {@code ndefType}: Ndef.TYPE_*, {@code ndef}: the message read at discovery (null: formatted but empty, or no NDEF at all when {@code techs} lacks NDEF). */
    HubNfcTag(byte[] uid, int[] techs, Bundle[] extras, NdefMessage ndef, Link link, Consumer<NdefMessage> store) {
        this.techs = techs; this.ndef = ndef; this.link = link; this.store = store;
        boolean any = false;
        for (int tech : techs) any |= tech == android.nfc.tech.TagTechnology.NDEF;
        hasNdef = any;
        tag = new Tag(uid, techs, extras, HANDLES.getAndIncrement(), 0, this);
    }

    /** The Ndef technology's extras NfcService gives a tag (Ndef.get reads them): message, size, card state (1 read-only, 2 read-write) and type. */
    static Bundle ndefExtras(NdefMessage message, int max, boolean writable, int type) {
        Bundle extras = new Bundle();
        extras.putParcelable(Ndef.EXTRA_NDEF_MSG, message);
        extras.putInt(Ndef.EXTRA_NDEF_MAXLENGTH, max);
        extras.putInt(Ndef.EXTRA_NDEF_CARDSTATE, writable ? Ndef.NDEF_MODE_READ_WRITE : Ndef.NDEF_MODE_READ_ONLY);
        extras.putInt(Ndef.EXTRA_NDEF_TYPE, type);
        return extras;
    }

    /** The tag left the field: every later call fails as TagLostException does. */
    void lost() { present = false; }

    @Override public int connect(int handle, int technology) { return present ? 0 : -1; }
    @Override public int reconnect(int handle) { return present ? 0 : -1; }
    @Override public int[] getTechList(int handle) { return techs; }
    @Override public boolean isNdef(int handle) { return hasNdef; }
    @Override public boolean isPresent(int handle) { return present; }
    @Override public TransceiveResult transceive(int handle, byte[] data, boolean raw) {
        if (!present) return new TransceiveResult(RESULT_TAGLOST, null);
        try { return new TransceiveResult(RESULT_SUCCESS, link.transmit(data)); }
        catch (IOException failed) { return new TransceiveResult(RESULT_FAILURE, null); }
    }
    @Override public NdefMessage ndefRead(int handle) { return present ? ndef : null; }
    @Override public int ndefWrite(int handle, NdefMessage message) {
        if (!present || store == null) return -1;
        try { store.accept(message); } catch (RuntimeException tooBig) { return -1; }
        ndef = message;
        return 0;
    }
    @Override public boolean ndefIsWritable(int handle) { return store != null; }
    @Override public Tag rediscover(int handle) { return tag; }
    @Override public int setTimeout(int handle, int timeout) { return 0; }
    @Override public int getTimeout(int handle) { return 618; }
    @Override public int getMaxTransceiveLength(int technology) { return 253; }
    @Override public boolean isTagUpToDate(long cookie) { return true; }
    // ndefMakeReadOnly, formatNdef, canMakeReadOnly, resetTimeouts, getExtendedLengthApdusSupported: not a desktop's to do (the defaults below say so)
    @Override public int ndefMakeReadOnly(int handle) { return -1; }
    @Override public int formatNdef(int handle, byte[] key) { return -1; }
    @Override public boolean canMakeReadOnly(int technology) { return false; }
    @Override public void resetTimeouts() {}
    @Override public boolean getExtendedLengthApdusSupported() { return false; }
}
