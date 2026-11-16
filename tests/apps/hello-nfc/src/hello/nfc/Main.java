package hello.nfc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import java.util.Arrays;

/**
 * NFC through the framework's own classes (tests/vm/nfc.py drops mock tags into the instance's nfc-mock directory and answers the "ready" lines):
 * the adapter, reader mode with a Type 2 tag (Ndef, NfcA, MifareUltralight, a write) and a Type 4 tag (IsoDep APDUs), a foreground dispatch whose PendingIntent
 * starts this activity, and the system's own dispatch: NDEF_DISCOVERED, TECH_DISCOVERED (nfc_tech_filter) and TAG_DISCOVERED activities. Steps print "[nfc] PASS|FAIL name".
 */
public final class Main extends Activity {
    private static boolean started, reading;
    private NfcAdapter adapter;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        handle(getIntent());
        if (started || !Intent.ACTION_MAIN.equals(getIntent().getAction())) return;
        started = true;
        adapter = NfcAdapter.getDefaultAdapter(this);
        boolean feature = getPackageManager().hasSystemFeature("android.hardware.nfc");
        if (adapter == null) { System.out.println("[nfc] no adapter feature=" + feature); return; }
        step("adapter", feature && adapter.isEnabled(), "enabled=" + adapter.isEnabled());
    }

    /** The framework hands reader mode and foreground dispatch to the service while the activity is resumed. */
    @Override protected void onResume() {
        super.onResume();
        if (adapter == null || reading) return;
        reading = true;
        adapter.enableReaderMode(this, this::read, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B, null);
        System.out.println("[nfc] ready reader");
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); handle(intent); }

    /** An NFC intent from the system (the foreground dispatch's PendingIntent or the activity the hub started). */
    private void handle(Intent intent) {
        String action = intent.getAction();
        if (action == null || !action.startsWith("android.nfc.action.")) return;
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag.class);
        Object[] messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        System.out.println("[nfc] intent " + action + " data=" + intent.getData() + " type=" + intent.getType() + " fg=" + intent.getBooleanExtra("fg", false)
                + " id=" + hex(tag.getId()) + " idextra=" + hex(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)) + " techs=" + String.join(",", tag.getTechList())
                + " ndef=" + (messages == null ? "none" : text((NdefMessage) messages[0])));
    }

    /** ReaderCallback: runs on a thread of the framework's. */
    private void read(Tag tag) {
        String[] techs = tag.getTechList();
        System.out.println("[nfc] reader tag " + hex(tag.getId()) + " " + String.join(",", techs));
        try {
            if (Arrays.asList(techs).contains(MifareUltralight.class.getName())) type2(tag); else type4(tag);
        } catch (Exception failed) { step("reader " + hex(tag.getId()), false, failed + " at " + String.join(" <- ", Arrays.stream(failed.getStackTrace()).limit(6).map(Object::toString).toArray(String[]::new))); }
        System.out.println("[nfc] reader done " + hex(tag.getId()));
        if (!Arrays.asList(techs).contains(MifareUltralight.class.getName())) runOnUiThread(this::foreground);  // the Type 4 tag is the second
    }

    private void type2(Tag tag) throws Exception {
        Ndef ndef = Ndef.get(tag);
        step("t2 cached ndef", "en:Hello".equals(text(ndef.getCachedNdefMessage())), text(ndef.getCachedNdefMessage()));
        ndef.connect();
        step("t2 ndef read", "en:Hello".equals(text(ndef.getNdefMessage())) && ndef.isWritable() && ndef.getMaxSize() == 137 && Ndef.NFC_FORUM_TYPE_2.equals(ndef.getType()),
                text(ndef.getNdefMessage()) + " writable=" + ndef.isWritable() + " max=" + ndef.getMaxSize() + " " + ndef.getType());
        ndef.close();
        NfcA a = NfcA.get(tag);
        a.connect();
        byte[] cc = a.transceive(new byte[] {0x30, 3});  // READ page 3: the capability container
        step("t2 NfcA transceive", cc.length == 16 && hex(Arrays.copyOf(cc, 4)).equals("e1101200") && a.getSak() == 0 && hex(a.getAtqa()).equals("4400"), hex(cc) + " sak=" + a.getSak());
        a.close();
        MifareUltralight mu = MifareUltralight.get(tag);
        mu.connect();
        byte[] pages = mu.readPages(4);
        step("t2 MifareUltralight readPages", hex(pages).startsWith("030cd1010854"), hex(pages));
        mu.close();
        ndef.connect();
        ndef.writeNdefMessage(new NdefMessage(NdefRecord.createTextRecord("en", "Changed")));
        step("t2 ndef write", "en:Changed".equals(text(ndef.getNdefMessage())), text(ndef.getNdefMessage()));
        ndef.close();
        mu.connect();
        pages = mu.readPages(4);
        step("t2 written pages", hex(pages).startsWith("030ed1010a54"), hex(pages));
        mu.close();
    }

    private void type4(Tag tag) throws Exception {
        Ndef ndef = Ndef.get(tag);
        step("t4 cached ndef", "en:Card".equals(text(ndef.getCachedNdefMessage())), text(ndef.getCachedNdefMessage()));
        IsoDep d = IsoDep.get(tag);
        d.connect();
        byte[] selected = d.transceive(hexBytes("00A4040007D2760000850101"));
        byte[] cc = (d.transceive(hexBytes("00A4000C02E103")).length == 2) ? d.transceive(hexBytes("00B000000F")) : null;
        step("t4 IsoDep select and CC", hex(selected).equals("9000") && cc != null && hex(cc).startsWith("000f20"), hex(selected) + " cc=" + (cc == null ? null : hex(cc)));
        d.transceive(hexBytes("00A4000C02E104"));
        byte[] nlen = d.transceive(hexBytes("00B0000002"));
        step("t4 IsoDep NDEF file", nlen.length == 4 && nlen[1] > 0, hex(nlen));
        byte[] canned = d.transceive(hexBytes("FFCA000000"));
        step("t4 canned APDU", hex(canned).equals("deadbeef9000"), hex(canned));
        step("t4 IsoDep info", d.getHistoricalBytes().length == 0 && !d.isExtendedLengthApduSupported() && d.getMaxTransceiveLength() == 253, "max=" + d.getMaxTransceiveLength());
        d.close();
    }

    /** The foreground dispatch: NDEF intents of a text/plain record to this activity (the PendingIntent's activity, not the system's pick). */
    private void foreground() {
        try {
            adapter.disableReaderMode(this);
            IntentFilter plain = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
            plain.addDataType("text/plain");
            PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, Main.class).putExtra("fg", true), PendingIntent.FLAG_MUTABLE);
            adapter.enableForegroundDispatch(this, pi, new IntentFilter[] {plain}, null);
            System.out.println("[nfc] ready foreground");
        } catch (Exception failed) { step("foreground", false, failed.toString()); }
    }

    private static void step(String name, boolean ok, String detail) { System.out.println("[nfc] " + (ok ? "PASS " : "FAIL ") + name + " (" + detail + ")"); }

    /** "lang:text" of a message's first text record, the URI of a URI record, else the MIME type. */
    private static String text(NdefMessage message) {
        if (message == null) return "null";
        NdefRecord record = message.getRecords()[0];
        byte[] p = record.getPayload();
        if (record.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(record.getType(), NdefRecord.RTD_TEXT)) {
            int lang = p[0] & 0x3f;
            return new String(p, 1, lang) + ":" + new String(p, 1 + lang, p.length - 1 - lang);
        }
        return record.toUri() != null ? record.toUri().toString() : record.toMimeType();
    }

    private static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder();
        for (byte b : bytes) s.append(String.format("%02x", b));
        return s.toString();
    }

    private static byte[] hexBytes(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }
}
