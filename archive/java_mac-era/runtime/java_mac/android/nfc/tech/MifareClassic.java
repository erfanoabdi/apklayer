// android.nfc.tech.MifareClassic -- the MIFARE Classic tag technology.
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
// The SIZE_*/BLOCK_SIZE/KEY_* constants below are properties of the MIFARE
// Classic FORMAT, not of any device, so they are exact -- an app that computes
// sector/block arithmetic from them gets the same answers it would on Android,
// and blockToSector/sectorToBlock/getBlockCountInSector below are that same
// pure arithmetic and are implemented for real.
//
// Mirrors packages/modules/Nfc framework/java/android/nfc/tech/MifareClassic.java.
package android.nfc.tech;

import android.nfc.Tag;

import java.io.IOException;

public final class MifareClassic implements TagTechnology {

    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_CLASSIC = 0;
    public static final int TYPE_PLUS = 1;
    public static final int TYPE_PRO = 2;

    public static final int SIZE_MINI = 320;
    public static final int SIZE_1K = 1024;
    public static final int SIZE_2K = 2048;
    public static final int SIZE_4K = 4096;

    public static final int BLOCK_SIZE = 16;

    public static final byte[] KEY_DEFAULT = {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
    public static final byte[] KEY_MIFARE_APPLICATION_DIRECTORY = {
            (byte) 0xA0, (byte) 0xA1, (byte) 0xA2, (byte) 0xA3, (byte) 0xA4, (byte) 0xA5 };
    public static final byte[] KEY_NFC_FORUM = {
            (byte) 0xD3, (byte) 0xF7, (byte) 0xD3, (byte) 0xF7, (byte) 0xD3, (byte) 0xF7 };

    MifareClassic() {}

    /** @see android.nfc.tech.NfcA#get -- same reasoning. */
    public static MifareClassic get(Tag tag) { return null; }

    @Override public Tag getTag() { return null; }

    @Override public boolean isConnected() { return false; }

    @Override public void connect() throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    @Override public void close() throws IOException {}

    public int getType() { return TYPE_UNKNOWN; }

    /** No tag, so no size. AOSP would report the card's; 0 cannot be
     *  mistaken for a real capacity. */
    public int getSize() { return 0; }

    public int getSectorCount() { return 0; }

    public int getBlockCount() { return 0; }

    /**
     * Pure MIFARE Classic geometry, real on any platform: the first 32 sectors
     * hold 4 blocks each, the remaining 8 (on a 4K card) hold 16.
     */
    public int getBlockCountInSector(int sectorIndex) {
        validateSector(sectorIndex);
        return sectorIndex < 32 ? 4 : 16;
    }

    /** @see #getBlockCountInSector -- the inverse mapping, same geometry. */
    public int blockToSector(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= 256) {
            throw new IndexOutOfBoundsException("blockIndex out of bounds: " + blockIndex);
        }
        if (blockIndex < 128) {
            return blockIndex / 4;
        }
        return 32 + (blockIndex - 128) / 16;
    }

    /** @see #getBlockCountInSector -- the forward mapping, same geometry. */
    public int sectorToBlock(int sectorIndex) {
        return sectorIndex < 32 ? sectorIndex * 4 : 128 + (sectorIndex - 32) * 16;
    }

    private static void validateSector(int sectorIndex) {
        // AOSP validates against the 4K maximum rather than the connected
        // card, so this check is faithful even with no card present.
        if (sectorIndex < 0 || sectorIndex >= 40) {
            throw new IndexOutOfBoundsException("sector out of bounds: " + sectorIndex);
        }
    }

    public boolean authenticateSectorWithKeyA(int sectorIndex, byte[] key) throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    public boolean authenticateSectorWithKeyB(int sectorIndex, byte[] key) throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    public byte[] readBlock(int blockIndex) throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    public void writeBlock(int blockIndex, byte[] data) throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    public void increment(int blockIndex, int value) throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    public void decrement(int blockIndex, int value) throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    public void transfer(int blockIndex) throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    public void restore(int blockIndex) throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    public byte[] transceive(byte[] data) throws IOException {
        throw new IOException("no NFC hardware on this device");
    }

    public int getMaxTransceiveLength() { return 0; }

    public void setTimeout(int timeout) {}

    public int getTimeout() { return 0; }
}
