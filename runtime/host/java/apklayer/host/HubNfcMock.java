package apklayer.host;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;

/**
 * The mock tag source (no reader needed): a tag file {@code *.tag} dropped into the instance's {@code apklayer/nfc-mock} directory is a tag put on the reader, its
 * deletion is the tag taken away (inotify through WatchService, no polling). Write the file elsewhere and rename it in, so that it is read whole. Format, one
 * {@code key=value} per line, {@code #} comments:
 * <pre>
 * type=t2|t4|nfca    Type 2 (NfcA, MifareUltralight, Ndef: an NTAG213's pages), Type 4 (NfcA, IsoDep, Ndef: the NDEF application) or a bare NfcA tag; default t2
 * uid=04a1b2c3d4e5f6  hex; atqa=0044 sak=00 (hex; default by type)
 * ndef.text=en:Hello  ndef.uri=https://example.org  ndef.mime=text/plain:body   one record each, in this order (none: an empty formatted tag)
 * writable=1          0: Ndef.writeNdefMessage fails
 * apdu=00A4040007D2760000850101 9000   (t4, repeatable) a canned command and answer, before the built-in NDEF application
 * </pre>
 */
final class HubNfcMock {
    private HubNfcMock() {}

    /** Follows {@code dir}: the tags present at start, then the changes; {@code sink} hears arrivals and departures. */
    static void watch(Path dir, HubNfc sink) throws IOException {
        WatchService watcher = dir.getFileSystem().newWatchService();
        dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
        Map<Path, HubNfcTag> tags = new HashMap<>();
        try (DirectoryStream<Path> existing = Files.newDirectoryStream(dir, "*.tag")) { for (Path file : existing) arrive(file, tags, sink); }
        Thread thread = new Thread(() -> {
            try {
                for (WatchKey key; (key = watcher.take()) != null; key.reset()) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path file = dir.resolve((Path) event.context());
                        if (!file.getFileName().toString().endsWith(".tag")) continue;
                        if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) { HubNfcTag gone = tags.remove(file); if (gone != null) sink.left(gone); }
                        else arrive(file, tags, sink);
                    }
                }
            } catch (InterruptedException | java.nio.file.ClosedWatchServiceException stopped) { /* the hub ends */ }
        }, "hub-nfc-mock");
        thread.setDaemon(true);
        thread.start();
    }

    private static void arrive(Path file, Map<Path, HubNfcTag> tags, HubNfc sink) {
        try {
            HubNfcTag tag = parse(Files.readAllLines(file));
            HubNfcTag previous = tags.put(file, tag);
            if (previous != null) sink.left(previous);
            sink.arrived(tag);
        } catch (IOException | FormatException | RuntimeException bad) { System.out.println("[apklayer-hub] nfc: " + file.getFileName() + " is not a tag: " + bad); }
    }

    static HubNfcTag parse(java.util.List<String> lines) throws FormatException {
        Map<String, String> keys = new HashMap<>();
        Map<String, byte[]> canned = new HashMap<>();
        java.util.List<NdefRecord> records = new java.util.ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            int eq = line.indexOf('=');
            if (line.isEmpty() || line.startsWith("#") || eq < 0) continue;
            String key = line.substring(0, eq).trim(), value = line.substring(eq + 1).trim();
            switch (key) {
                case "apdu" -> { String[] pair = value.split("\\s+"); canned.put(pair[0].toUpperCase(), hex(pair[1])); }
                case "ndef.text" -> records.add(NdefRecord.createTextRecord(value.substring(0, value.indexOf(':')), value.substring(value.indexOf(':') + 1)));
                case "ndef.uri" -> records.add(NdefRecord.createUri(value));
                case "ndef.mime" -> records.add(NdefRecord.createMime(value.substring(0, value.indexOf(':')), value.substring(value.indexOf(':') + 1).getBytes(StandardCharsets.UTF_8)));
                default -> keys.put(key, value);
            }
        }
        boolean t4 = "t4".equals(keys.getOrDefault("type", "t2"));
        if ("nfca".equals(keys.get("type"))) {  // a bare NfcA tag: no NDEF, no ISO-DEP (what only TAG_DISCOVERED is for)
            Bundle bare = new Bundle();
            bare.putShort("sak", (short) 0);
            bare.putByteArray("atqa", hex(keys.getOrDefault("atqa", "4400")));
            return new HubNfcTag(hex(keys.getOrDefault("uid", "04a1b2c3d4e5f6")), new int[] {TagTechnology.NFC_A}, new Bundle[] {bare}, null, command -> { throw new IOException("NAK"); }, null);
        }
        byte[] uid = hex(keys.getOrDefault("uid", t4 ? "08a1b2c3" : "04a1b2c3d4e5f6"));
        boolean writable = !"0".equals(keys.get("writable"));
        NdefMessage ndef = records.isEmpty() ? null : new NdefMessage(records.toArray(new NdefRecord[0]));
        Bundle nfcA = new Bundle();
        nfcA.putShort("sak", (short) hex(keys.getOrDefault("sak", t4 ? "20" : "00"))[0]);
        nfcA.putByteArray("atqa", hex(keys.getOrDefault("atqa", t4 ? "0400" : "4400")));
        HubNfcTag tag;
        if (t4) {
            Type4 card = new Type4(ndef, canned, writable);
            Bundle isoDep = new Bundle();
            isoDep.putByteArray("histbytes", new byte[0]);
            tag = new HubNfcTag(uid, new int[] {TagTechnology.NFC_A, TagTechnology.ISO_DEP, TagTechnology.NDEF}, new Bundle[] {nfcA, isoDep, HubNfcTag.ndefExtras(ndef, 0xFFFE, writable, 4)},
                    ndef, card, writable ? card::write : null);
        } else {
            Type2 card = new Type2(uid, ndef);
            tag = new HubNfcTag(uid, new int[] {TagTechnology.NFC_A, TagTechnology.MIFARE_ULTRALIGHT, TagTechnology.NDEF}, new Bundle[] {nfcA, new Bundle(), HubNfcTag.ndefExtras(ndef, Type2.MAX, writable, 2)},
                    ndef, card, writable ? card::write : null);
        }
        return tag;
    }

    static byte[] hex(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(text.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    /** An NTAG213's memory (45 pages of 4 bytes: UID and check bytes, lock, capability container E1 10 12 00, the NDEF TLV from page 4), READ (30) and WRITE (A2). */
    private static final class Type2 implements HubNfcTag.Link {
        static final int PAGES = 45, MAX = 137;  // bytes of the NDEF message the TLV can hold (144 minus the TLV's own bytes)
        private final byte[] memory = new byte[PAGES * 4];

        Type2(byte[] uid, NdefMessage ndef) {
            if (uid.length == 7) {
                System.arraycopy(uid, 0, memory, 0, 3);
                memory[3] = (byte) (0x88 ^ uid[0] ^ uid[1] ^ uid[2]);
                System.arraycopy(uid, 3, memory, 4, 4);
                memory[8] = (byte) (uid[3] ^ uid[4] ^ uid[5] ^ uid[6]);
            } else System.arraycopy(uid, 0, memory, 0, Math.min(uid.length, 8));
            memory[12] = (byte) 0xE1; memory[13] = 0x10; memory[14] = 0x12;
            write(ndef);
        }

        void write(NdefMessage ndef) {
            byte[] message = ndef == null ? new byte[0] : ndef.toByteArray();
            if (message.length > MAX) throw new IllegalArgumentException("the message does not fit the tag");
            java.util.Arrays.fill(memory, 16, memory.length, (byte) 0);
            memory[16] = 0x03; memory[17] = (byte) message.length;
            System.arraycopy(message, 0, memory, 18, message.length);
            memory[18 + message.length] = (byte) 0xFE;
        }

        @Override public byte[] transmit(byte[] command) throws IOException {
            if (command.length == 2 && command[0] == 0x30) {  // READ: four pages from the page, wrapping at the end as the chip does
                byte[] out = new byte[16];
                for (int i = 0; i < 16; i++) out[i] = memory[(command[1] * 4 + i) % memory.length];
                return out;
            }
            if (command.length == 6 && command[0] == (byte) 0xA2 && command[1] >= 4 && command[1] < PAGES) {  // WRITE a page: ACK
                System.arraycopy(command, 2, memory, command[1] * 4, 4);
                return new byte[] {0x0A};
            }
            throw new IOException("NAK");
        }
    }

    /** The Type 4 tag's NDEF application (NFC Forum T4T): SELECT by name and by file id (capability container E103, NDEF file E104), READ BINARY; canned answers first. */
    private static final class Type4 implements HubNfcTag.Link {
        private final Map<String, byte[]> canned;
        private final boolean writable;
        private volatile byte[] file = new byte[0];  // NLEN and the message
        private byte[] selected = new byte[0];

        Type4(NdefMessage ndef, Map<String, byte[]> canned, boolean writable) { this.canned = canned; this.writable = writable; write(ndef); }

        void write(NdefMessage ndef) {
            byte[] message = ndef == null ? new byte[0] : ndef.toByteArray();
            file = new byte[2 + message.length];
            file[0] = (byte) (message.length >> 8); file[1] = (byte) message.length;
            System.arraycopy(message, 0, file, 2, message.length);
        }

        @Override public byte[] transmit(byte[] c) {
            StringBuilder key = new StringBuilder();
            for (byte b : c) key.append(String.format("%02X", b));
            byte[] answer = canned.get(key.toString());
            if (answer != null) return answer;
            if (c.length >= 5 && c[0] == 0 && c[1] == (byte) 0xA4 && c[2] == 4) return sw(key.toString().startsWith("00A4040007D2760000850101") ? 0x9000 : 0x6A82);  // SELECT by name
            if (c.length >= 7 && c[0] == 0 && c[1] == (byte) 0xA4 && c[2] == 0) {  // SELECT a file
                int id = (c[5] & 0xff) << 8 | c[6] & 0xff;
                if (id == 0xE103) selected = new byte[] {0, 0x0F, 0x20, 0, 0x3B, 0, 0x34, 4, 6, (byte) 0xE1, 4, (byte) 0xFF, (byte) 0xFE, 0, (byte) (writable ? 0 : 0xFF)};
                else if (id == 0xE104) selected = file;
                else return sw(0x6A82);
                return sw(0x9000);
            }
            if (c.length >= 5 && c[0] == 0 && c[1] == (byte) 0xB0) {  // READ BINARY
                int offset = (c[2] & 0xff) << 8 | c[3] & 0xff, length = Math.max(0, Math.min(c[4] & 0xff, selected.length - offset));
                byte[] out = new byte[length + 2];
                if (length > 0) System.arraycopy(selected, offset, out, 0, length);
                out[length] = (byte) 0x90;
                return out;
            }
            return sw(0x6D00);  // instruction not supported
        }

        private static byte[] sw(int status) { return new byte[] {(byte) (status >> 8), (byte) status}; }
    }
}
