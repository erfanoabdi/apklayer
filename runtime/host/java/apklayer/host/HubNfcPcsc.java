package apklayer.host;

import android.nfc.NdefMessage;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;

/**
 * A contactless PC/SC reader (pcscd: ACR122U, ACR1252, any CCID reader with a contactless slot) as the NFC controller, through the JDK's javax.smartcardio over
 * libpcsclite. A card in the field is a Tag: its UID from the reader's pseudo-APDU {@code FF CA 00 00 00}; a card that is ISO 14443-4 (the ATR lacks the PC/SC
 * storage-card marker) is NfcA + IsoDep, its APDUs go through as they are, and a Type 4 NDEF application is read at discovery (Ndef, read-only); a storage card
 * (Type 2, MIFARE) is NfcA, its raw frames go through the reader's direct-transmit pseudo-APDU {@code FF 00 00 00 Lc data} (ACR122U and PN532-based readers).
 * Not run against a reader yet (docs/NFC.md).
 */
final class HubNfcPcsc {
    private HubNfcPcsc() {}

    static void watch(HubNfc sink) {
        Thread thread = new Thread(() -> {
            Map<String, HubNfcTag> tags = new HashMap<>();
            try {
                CardTerminals terminals = TerminalFactory.getDefault().terminals();
                while (true) {
                    sink.readers(terminals.list().size());
                    for (CardTerminal terminal : terminals.list()) {
                        boolean present = terminal.isCardPresent();
                        HubNfcTag known = tags.get(terminal.getName());
                        if (present && known == null) {
                            HubNfcTag tag = tag(terminal);
                            if (tag != null) { tags.put(terminal.getName(), tag); sink.arrived(tag); }
                        } else if (!present && known != null) { tags.remove(terminal.getName()); sink.left(known); }
                    }
                    terminals.waitForChange();  // blocks in SCardGetStatusChange: a card or a reader came or went
                }
            } catch (Throwable unavailable) { System.out.println("[apklayer-hub] nfc: no PC/SC: " + unavailable); }  // no libpcsclite, no pcscd: the adapter stays off
        }, "hub-nfc-pcsc");
        thread.setDaemon(true);
        thread.start();
    }

    private static HubNfcTag tag(CardTerminal terminal) {
        try {
            Card card = terminal.connect("*");
            CardChannel channel = card.getBasicChannel();
            ResponseAPDU id = channel.transmit(new CommandAPDU(0xFF, 0xCA, 0, 0, 256));
            if (id.getSW() != 0x9000) return null;
            byte[] uid = id.getData(), atr = card.getATR().getBytes();
            Bundle nfcA = new Bundle();
            nfcA.putShort("sak", (short) 0);
            nfcA.putByteArray("atqa", new byte[2]);  // the reader does not say: the framework's NfcA accepts zeros
            boolean storage = contains(atr, new byte[] {(byte) 0xA0, 0, 0, 3, 6});  // the PC/SC part 3 contactless storage card marker
            if (storage) {  // raw frames through the reader's direct transmit
                return new HubNfcTag(uid, new int[] {TagTechnology.NFC_A}, new Bundle[] {nfcA}, null, command -> {
                    byte[] wrapped = new byte[5 + command.length];
                    wrapped[0] = (byte) 0xFF; wrapped[4] = (byte) command.length;
                    System.arraycopy(command, 0, wrapped, 5, command.length);
                    ResponseAPDU answer = exchange(channel, wrapped);
                    if (answer.getSW() != 0x9000) throw new IOException("reader SW " + Integer.toHexString(answer.getSW()));
                    return answer.getData();
                }, null);
            }
            NdefMessage ndef = readType4(channel);
            Bundle isoDep = new Bundle();
            isoDep.putByteArray("histbytes", new byte[0]);
            boolean has = ndef != null;
            return new HubNfcTag(uid, has ? new int[] {TagTechnology.NFC_A, TagTechnology.ISO_DEP, TagTechnology.NDEF} : new int[] {TagTechnology.NFC_A, TagTechnology.ISO_DEP},
                    has ? new Bundle[] {nfcA, isoDep, HubNfcTag.ndefExtras(ndef, 0xFFFE, false, 4)} : new Bundle[] {nfcA, isoDep}, ndef, command -> {
                        ResponseAPDU answer = exchange(channel, command);
                        return answer.getBytes();
                    }, null);
        } catch (CardException | RuntimeException failed) {
            System.out.println("[apklayer-hub] nfc: cannot read the card in " + terminal.getName() + ": " + failed);
            return null;
        }
    }

    private static ResponseAPDU exchange(CardChannel channel, byte[] command) throws IOException {
        try { return channel.transmit(new CommandAPDU(command)); }
        catch (CardException | IllegalArgumentException failed) { throw new IOException(failed); }
    }

    /** The NDEF message of a Type 4 tag (select the NDEF application, the capability container, then the NDEF file it names and read it), null when there is none. */
    private static NdefMessage readType4(CardChannel channel) {
        try {
            if (!ok(channel, 0x00, 0xA4, 0x04, 0x00, HubNfcMock.hex("D2760000850101"))) return null;
            if (!ok(channel, 0x00, 0xA4, 0x00, 0x0C, HubNfcMock.hex("E103"))) return null;
            byte[] cc = read(channel, 0, 15);
            if (cc.length < 15) return null;
            if (!ok(channel, 0x00, 0xA4, 0x00, 0x0C, new byte[] {cc[9], cc[10]})) return null;
            byte[] length = read(channel, 0, 2);
            int size = (length[0] & 0xff) << 8 | length[1] & 0xff;
            ByteArrayOutputStream message = new ByteArrayOutputStream();
            while (message.size() < size) message.writeBytes(read(channel, 2 + message.size(), Math.min(0xFF, size - message.size())));
            return new NdefMessage(message.toByteArray());
        } catch (CardException | IOException | android.nfc.FormatException | RuntimeException none) { return null; }
    }

    private static boolean ok(CardChannel channel, int cla, int ins, int p1, int p2, byte[] data) throws CardException {
        return channel.transmit(new CommandAPDU(cla, ins, p1, p2, data)).getSW() == 0x9000;
    }

    private static byte[] read(CardChannel channel, int offset, int length) throws CardException, IOException {
        ResponseAPDU answer = channel.transmit(new CommandAPDU(0x00, 0xB0, offset >> 8, offset & 0xff, length));
        if (answer.getSW() != 0x9000) throw new IOException("READ BINARY " + Integer.toHexString(answer.getSW()));
        return answer.getData();
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        for (int i = 0; i + needle.length <= haystack.length; i++) if (java.util.Arrays.equals(haystack, i, i + needle.length, needle, 0, needle.length)) return true;
        return false;
    }
}
