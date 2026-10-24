// android.opengl.ETC1Util -- the standard Android helper for loading
// ETC1-compressed textures out of .pkm streams. AOSP:
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/opengl/java/android/opengl/ETC1Util.java
//
// Every method here used to return a hardcoded constant (isETC1Supported ->
// false, createTexture -> null) or do nothing, so an app that packaged
// ETC1 atlases either took a fallback branch that its assets did not
// support, or NPE'd on the null texture. The codec below is a direct port
// of the AOSP reference implementation that backs android.opengl.ETC1's
// native methods:
//   platform/frameworks/native/opengl/libs/ETC1/etc1.cpp
//   (kModifierTable, kLookup, decode_subblock, etc1_decode_block,
//    etc1_decode_image, etc1_encode_block, etc1_encode_image, the PKM
//    header readers/writers and etc1_get_encoded_data_size)
// fetched verbatim from android.googlesource.com. Keeping the port inside
// this class rather than calling android.opengl.ETC1 is a stop-gap: the
// public ETC1 class does not exist in this tree yet, and the algorithm has
// to live somewhere for ETC1Util to be anything but a stub.
package android.opengl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class ETC1Util {
    /** ETC1_RGB8_OES, from GLES/glext.h (OES_compressed_ETC1_RGB8_texture). */
    private static final int ETC1_RGB8_OES = 0x8D64;
    /** ETC1.ETC_PKM_HEADER_SIZE. */
    private static final int ETC_PKM_HEADER_SIZE = 16;
    private static final int ETC1_ENCODED_BLOCK_SIZE = 8;
    private static final int ETC1_DECODED_BLOCK_SIZE = 48;

    public static class ETC1Texture {
        private final int width;
        private final int height;
        private final ByteBuffer data;
        public ETC1Texture(int w, int h, ByteBuffer d) {
            this.width = w;
            this.height = h;
            this.data = d;
        }
        public int getWidth()  { return width; }
        public int getHeight() { return height; }
        public ByteBuffer getData() { return data; }
    }

    /**
     * AOSP asks the driver whether ETC1_RGB8_OES is in
     * GL_COMPRESSED_TEXTURE_FORMATS. We ask the equivalent question through
     * the extension string instead: OES_compressed_ETC1_RGB8_texture is the
     * extension that DEFINES ETC1_RGB8_OES, so a conformant driver advertises
     * exactly one iff it advertises the other. The enumeration route is not
     * usable through this tree's binding, because the JNI glGetIntegerv
     * lands every query in a fixed 16-int stack buffer and a Metal-class
     * driver reports far more than 16 compressed formats.
     */
    public static boolean isETC1Supported() {
        String ext = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        if (ext == null || ext.isEmpty()) return false;
        // Match on a whole space-delimited token so
        // "GL_OES_compressed_ETC1_RGB8_texture_something" cannot false-positive.
        for (String token : ext.split(" ")) {
            if ("GL_OES_compressed_ETC1_RGB8_texture".equals(token)
                    || "OES_compressed_ETC1_RGB8_texture".equals(token)) {
                return true;
            }
        }
        return false;
    }

    public static void loadTexture(int target, int level, int border,
                                    int fallbackFormat, int fallbackType,
                                    InputStream input) throws IOException {
        loadTexture(target, level, border, fallbackFormat, fallbackType, createTexture(input));
    }

    public static void loadTexture(int target, int level, int border,
                                    int fallbackFormat, int fallbackType,
                                    ETC1Texture texture) {
        // AOSP validates the fallback pair unconditionally, before it looks at
        // driver support, so an app gets the same IllegalArgumentException on
        // every device.
        if (fallbackFormat != GLES20.GL_RGB) {
            throw new IllegalArgumentException("fallbackFormat must be GL_RGB");
        }
        if (!(fallbackType == GLES20.GL_UNSIGNED_SHORT_5_6_5
                || fallbackType == GLES20.GL_UNSIGNED_BYTE)) {
            throw new IllegalArgumentException("Unsupported fallbackType");
        }
        int width = texture.getWidth();
        int height = texture.getHeight();
        ByteBuffer data = texture.getData();
        if (isETC1Supported()) {
            int imageSize = data.remaining();
            GLES20.glCompressedTexImage2D(target, level, ETC1_RGB8_OES, width, height,
                    border, imageSize, data);
        } else {
            // The documented always-available path: decode on the CPU and
            // upload as an uncompressed RGB texture.
            boolean useShorts = fallbackType != GLES20.GL_UNSIGNED_BYTE;
            int pixelSize = useShorts ? 2 : 3;
            int stride = pixelSize * width;
            byte[] encoded = toByteArray(data);
            byte[] decoded = new byte[stride * height];
            decodeImage(encoded, decoded, width, height, pixelSize, stride);
            ByteBuffer decodedData = ByteBuffer.allocateDirect(stride * height)
                    .order(ByteOrder.nativeOrder());
            decodedData.put(decoded);
            decodedData.position(0);
            GLES20.glTexImage2D(target, level, fallbackFormat, width, height, border,
                    fallbackFormat, fallbackType, decodedData);
        }
    }

    public static ETC1Texture createTexture(InputStream input) throws IOException {
        byte[] header = new byte[ETC_PKM_HEADER_SIZE];
        readFully(input, header, ETC_PKM_HEADER_SIZE, "Unable to read PKM file header.");
        if (!pkmIsValid(header)) {
            throw new IOException("Not a PKM file.");
        }
        int width = pkmGetWidth(header);
        int height = pkmGetHeight(header);
        int encodedSize = getEncodedDataSize(width, height);
        byte[] encoded = new byte[encodedSize];
        readFully(input, encoded, encodedSize, "Unable to read PKM file data.");
        ByteBuffer dataBuffer = ByteBuffer.allocateDirect(encodedSize).order(ByteOrder.nativeOrder());
        dataBuffer.put(encoded);
        dataBuffer.position(0);
        return new ETC1Texture(width, height, dataBuffer);
    }

    /** AOSP signature takes a Buffer, not a ByteBuffer; the DEX descriptor an
     *  app links against is (Ljava/nio/Buffer;IIII). */
    public static ETC1Texture compressTexture(Buffer input, int width, int height,
                                                int pixelSize, int stride) {
        int encodedImageSize = getEncodedDataSize(width, height);
        byte[] src = toByteArray(input);
        byte[] encoded = new byte[encodedImageSize];
        encodeImage(src, width, height, pixelSize, stride, encoded);
        ByteBuffer compressedImage = ByteBuffer.allocateDirect(encodedImageSize)
                .order(ByteOrder.nativeOrder());
        compressedImage.put(encoded);
        compressedImage.position(0);
        return new ETC1Texture(width, height, compressedImage);
    }

    public static void writeTexture(ETC1Texture texture, OutputStream output) throws IOException {
        ByteBuffer dataBuffer = texture.getData();
        int originalPosition = dataBuffer.position();
        try {
            int width = texture.getWidth();
            int height = texture.getHeight();
            byte[] header = new byte[ETC_PKM_HEADER_SIZE];
            pkmFormatHeader(header, width, height);
            output.write(header, 0, ETC_PKM_HEADER_SIZE);
            int encodedSize = getEncodedDataSize(width, height);
            byte[] ioBuffer = new byte[4096];
            for (int i = 0; i < encodedSize; ) {
                int chunkSize = Math.min(ioBuffer.length, encodedSize - i);
                dataBuffer.get(ioBuffer, 0, chunkSize);
                output.write(ioBuffer, 0, chunkSize);
                i += chunkSize;
            }
        } finally {
            dataBuffer.position(originalPosition);
        }
    }

    // ------------------------------------------------------------------
    // PKM container (etc1.cpp: kMagic + the *_OFFSET constants, big-endian
    // uint16 fields).
    // ------------------------------------------------------------------
    private static final byte[] PKM_MAGIC = { 'P', 'K', 'M', ' ', '1', '0' };
    private static final int PKM_FORMAT_OFFSET = 6;
    private static final int PKM_ENCODED_WIDTH_OFFSET = 8;
    private static final int PKM_ENCODED_HEIGHT_OFFSET = 10;
    private static final int PKM_WIDTH_OFFSET = 12;
    private static final int PKM_HEIGHT_OFFSET = 14;
    private static final int ETC1_RGB_NO_MIPMAPS = 0;

    private static int readBEUint16(byte[] p, int off) {
        return ((p[off] & 0xff) << 8) | (p[off + 1] & 0xff);
    }
    private static void writeBEUint16(byte[] p, int off, int data) {
        p[off] = (byte) (data >> 8);
        p[off + 1] = (byte) data;
    }

    private static boolean pkmIsValid(byte[] header) {
        for (int i = 0; i < PKM_MAGIC.length; i++) {
            if (header[i] != PKM_MAGIC[i]) return false;
        }
        int format = readBEUint16(header, PKM_FORMAT_OFFSET);
        int encodedWidth = readBEUint16(header, PKM_ENCODED_WIDTH_OFFSET);
        int encodedHeight = readBEUint16(header, PKM_ENCODED_HEIGHT_OFFSET);
        int width = readBEUint16(header, PKM_WIDTH_OFFSET);
        int height = readBEUint16(header, PKM_HEIGHT_OFFSET);
        return format == ETC1_RGB_NO_MIPMAPS
                && encodedWidth >= width && encodedWidth - width < 4
                && encodedHeight >= height && encodedHeight - height < 4;
    }

    private static int pkmGetWidth(byte[] header)  { return readBEUint16(header, PKM_WIDTH_OFFSET); }
    private static int pkmGetHeight(byte[] header) { return readBEUint16(header, PKM_HEIGHT_OFFSET); }

    private static void pkmFormatHeader(byte[] header, int width, int height) {
        System.arraycopy(PKM_MAGIC, 0, header, 0, PKM_MAGIC.length);
        writeBEUint16(header, PKM_FORMAT_OFFSET, ETC1_RGB_NO_MIPMAPS);
        writeBEUint16(header, PKM_ENCODED_WIDTH_OFFSET, (width + 3) & ~3);
        writeBEUint16(header, PKM_ENCODED_HEIGHT_OFFSET, (height + 3) & ~3);
        writeBEUint16(header, PKM_WIDTH_OFFSET, width);
        writeBEUint16(header, PKM_HEIGHT_OFFSET, height);
    }

    /** etc1_get_encoded_data_size: 8 bytes per 4x4 block. */
    private static int getEncodedDataSize(int width, int height) {
        return (((width + 3) & ~3) * ((height + 3) & ~3)) >> 1;
    }

    // ------------------------------------------------------------------
    // I/O + Buffer helpers.
    // ------------------------------------------------------------------
    // AOSP issues a single InputStream.read() per chunk and treats a short
    // read as a corrupt file. A short read is legal for any stream that is
    // not fully buffered (an APK asset served through a decompressing
    // stream, for one), so loop until EOF instead: identical outcome for
    // well-formed data, no spurious IOException.
    private static void readFully(InputStream input, byte[] out, int count, String msg)
            throws IOException {
        int off = 0;
        while (off < count) {
            int n = input.read(out, off, count - off);
            if (n < 0) throw new IOException(msg);
            off += n;
        }
    }

    private static byte[] toByteArray(Buffer b) {
        if (b instanceof ByteBuffer) {
            ByteBuffer bb = ((ByteBuffer) b).duplicate();
            byte[] out = new byte[bb.remaining()];
            bb.get(out);
            return out;
        }
        if (b instanceof ShortBuffer) {
            // etc1.cpp's 2-byte pixel path reads p[0] as the low half and
            // p[1] as the high half of the RGB565 word, i.e. it walks raw
            // memory, so honour the buffer's own byte order here.
            ShortBuffer sb = ((ShortBuffer) b).duplicate();
            boolean little = sb.order() == ByteOrder.LITTLE_ENDIAN;
            byte[] out = new byte[sb.remaining() * 2];
            int i = 0;
            while (sb.hasRemaining()) {
                int v = sb.get() & 0xffff;
                if (little) { out[i++] = (byte) v; out[i++] = (byte) (v >> 8); }
                else        { out[i++] = (byte) (v >> 8); out[i++] = (byte) v; }
            }
            return out;
        }
        throw new IllegalArgumentException(
                "ETC1Util: unsupported Buffer type " + b.getClass().getName());
    }

    // ------------------------------------------------------------------
    // ETC1 codec -- port of frameworks/native/opengl/libs/ETC1/etc1.cpp.
    // ------------------------------------------------------------------
    private static final int[] K_MODIFIER_TABLE = {
        /* 0 */  2,   8,  -2,   -8,
        /* 1 */  5,  17,  -5,  -17,
        /* 2 */  9,  29,  -9,  -29,
        /* 3 */ 13,  42, -13,  -42,
        /* 4 */ 18,  60, -18,  -60,
        /* 5 */ 24,  80, -24,  -80,
        /* 6 */ 33, 106, -33, -106,
        /* 7 */ 47, 183, -47, -183 };

    private static final int[] K_LOOKUP = { 0, 1, 2, 3, -4, -3, -2, -1 };

    private static int clamp(int x) { return x >= 0 ? (x < 255 ? x : 255) : 0; }
    private static int convert4To8(int b) { int c = b & 0xf;  return (c << 4) | c; }
    private static int convert5To8(int b) { int c = b & 0x1f; return (c << 3) | (c >> 2); }
    private static int convert6To8(int b) { int c = b & 0x3f; return (c << 2) | (c >> 4); }
    private static int divideBy255(int d) { return (d + 128 + (d >> 8)) >> 8; }
    private static int convert8To4(int b) { return divideBy255((b & 0xff) * 15); }
    private static int convert8To5(int b) { return divideBy255((b & 0xff) * 31); }
    private static int convertDiff(int base, int diff) {
        return convert5To8((0x1f & base) + K_LOOKUP[0x7 & diff]);
    }
    private static int square(int x) { return x * x; }

    private static void decodeSubblock(byte[] out, int r, int g, int b, int tableOff,
                                       int low, boolean second, boolean flipped) {
        int baseX = 0;
        int baseY = 0;
        if (second) {
            if (flipped) baseY = 2; else baseX = 2;
        }
        for (int i = 0; i < 8; i++) {
            int x, y;
            if (flipped) { x = baseX + (i >> 1); y = baseY + (i & 1); }
            else         { x = baseX + (i >> 2); y = baseY + (i & 3); }
            int k = y + (x * 4);
            // `low` is a uint32 in etc1.cpp -- shift logically, never
            // arithmetically, or bit 31 leaks into the modifier selector.
            int offset = ((low >>> k) & 1) | ((low >>> (k + 15)) & 2);
            int delta = K_MODIFIER_TABLE[tableOff + offset];
            int q = 3 * (x + 4 * y);
            out[q]     = (byte) clamp(r + delta);
            out[q + 1] = (byte) clamp(g + delta);
            out[q + 2] = (byte) clamp(b + delta);
        }
    }

    /** etc1_decode_block: 8 encoded bytes in, a 4x4 block of RGB triples out. */
    private static void decodeBlock(byte[] in, int inOff, byte[] out) {
        int high = ((in[inOff] & 0xff) << 24) | ((in[inOff + 1] & 0xff) << 16)
                 | ((in[inOff + 2] & 0xff) << 8) | (in[inOff + 3] & 0xff);
        int low  = ((in[inOff + 4] & 0xff) << 24) | ((in[inOff + 5] & 0xff) << 16)
                 | ((in[inOff + 6] & 0xff) << 8) | (in[inOff + 7] & 0xff);
        int r1, r2, g1, g2, b1, b2;
        if ((high & 2) != 0) {
            // differential mode: 5-bit base + signed 3-bit delta per channel
            int rBase = high >>> 27;
            int gBase = high >>> 19;
            int bBase = high >>> 11;
            r1 = convert5To8(rBase);
            r2 = convertDiff(rBase, high >>> 24);
            g1 = convert5To8(gBase);
            g2 = convertDiff(gBase, high >>> 16);
            b1 = convert5To8(bBase);
            b2 = convertDiff(bBase, high >>> 8);
        } else {
            // individual mode: two independent 4-bit-per-channel base colors
            r1 = convert4To8(high >>> 28);
            r2 = convert4To8(high >>> 24);
            g1 = convert4To8(high >>> 20);
            g2 = convert4To8(high >>> 16);
            b1 = convert4To8(high >>> 12);
            b2 = convert4To8(high >>> 8);
        }
        int tableA = (7 & (high >>> 5)) * 4;
        int tableB = (7 & (high >>> 2)) * 4;
        boolean flipped = (high & 1) != 0;
        decodeSubblock(out, r1, g1, b1, tableA, low, false, flipped);
        decodeSubblock(out, r2, g2, b2, tableB, low, true, flipped);
    }

    /** etc1_decode_image. pixelSize 3 = RGB888, 2 = RGB565 (little-endian pair). */
    private static void decodeImage(byte[] in, byte[] out, int width, int height,
                                    int pixelSize, int stride) {
        if (pixelSize < 2 || pixelSize > 3) {
            throw new IllegalArgumentException("pixelSize must be 2 or 3");
        }
        byte[] block = new byte[ETC1_DECODED_BLOCK_SIZE];
        int encodedWidth = (width + 3) & ~3;
        int encodedHeight = (height + 3) & ~3;
        int inOff = 0;
        for (int y = 0; y < encodedHeight; y += 4) {
            int yEnd = Math.min(height - y, 4);
            for (int x = 0; x < encodedWidth; x += 4) {
                int xEnd = Math.min(width - x, 4);
                decodeBlock(in, inOff, block);
                inOff += ETC1_ENCODED_BLOCK_SIZE;
                for (int cy = 0; cy < yEnd; cy++) {
                    int q = (cy * 4) * 3;
                    int p = pixelSize * x + stride * (y + cy);
                    if (pixelSize == 3) {
                        System.arraycopy(block, q, out, p, xEnd * 3);
                    } else {
                        for (int cx = 0; cx < xEnd; cx++) {
                            int r = block[q++] & 0xff;
                            int g = block[q++] & 0xff;
                            int b = block[q++] & 0xff;
                            int pixel = ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3);
                            out[p++] = (byte) pixel;
                            out[p++] = (byte) (pixel >> 8);
                        }
                    }
                }
            }
        }
    }

    /** etc_compressed: the running best encoding for one 4x4 block. */
    private static final class EtcCompressed {
        int high;
        int low;
        long score;   // uint32 in etc1.cpp; a long keeps the ~0 sentinel ordered
    }

    private static void takeBest(EtcCompressed a, EtcCompressed b) {
        if (a.score > b.score) { a.high = b.high; a.low = b.low; a.score = b.score; }
    }

    private static void averageColorsSubblock(byte[] in, int inMask, byte[] colors,
                                              int colorsOff, boolean flipped, boolean second) {
        int r = 0, g = 0, b = 0;
        if (flipped) {
            int by = second ? 2 : 0;
            for (int y = 0; y < 2; y++) {
                int yy = by + y;
                for (int x = 0; x < 4; x++) {
                    int i = x + 4 * yy;
                    if ((inMask & (1 << i)) != 0) {
                        r += in[i * 3] & 0xff;
                        g += in[i * 3 + 1] & 0xff;
                        b += in[i * 3 + 2] & 0xff;
                    }
                }
            }
        } else {
            int bx = second ? 2 : 0;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 2; x++) {
                    int xx = bx + x;
                    int i = xx + 4 * y;
                    if ((inMask & (1 << i)) != 0) {
                        r += in[i * 3] & 0xff;
                        g += in[i * 3 + 1] & 0xff;
                        b += in[i * 3 + 2] & 0xff;
                    }
                }
            }
        }
        colors[colorsOff]     = (byte) ((r + 4) >> 3);
        colors[colorsOff + 1] = (byte) ((g + 4) >> 3);
        colors[colorsOff + 2] = (byte) ((b + 4) >> 3);
    }

    private static long chooseModifier(byte[] baseColors, int baseOff, byte[] in, int inOff,
                                       EtcCompressed c, int bitIndex, int tableOff) {
        long bestScore = 0xFFFFFFFFL;
        int bestIndex = 0;
        int pixelR = in[inOff] & 0xff;
        int pixelG = in[inOff + 1] & 0xff;
        int pixelB = in[inOff + 2] & 0xff;
        int r = baseColors[baseOff] & 0xff;
        int g = baseColors[baseOff + 1] & 0xff;
        int b = baseColors[baseOff + 2] & 0xff;
        for (int i = 0; i < 4; i++) {
            int modifier = K_MODIFIER_TABLE[tableOff + i];
            int decodedG = clamp(g + modifier);
            long score = 6L * square(decodedG - pixelG);
            if (score >= bestScore) continue;
            int decodedR = clamp(r + modifier);
            score += 3L * square(decodedR - pixelR);
            if (score >= bestScore) continue;
            int decodedB = clamp(b + modifier);
            score += square(decodedB - pixelB);
            if (score < bestScore) { bestScore = score; bestIndex = i; }
        }
        c.low |= (((bestIndex >> 1) << 16) | (bestIndex & 1)) << bitIndex;
        return bestScore;
    }

    private static void encodeSubblockHelper(byte[] in, int inMask, EtcCompressed c,
                                             boolean flipped, boolean second,
                                             byte[] baseColors, int baseOff, int tableOff) {
        long score = c.score;
        if (flipped) {
            int by = second ? 2 : 0;
            for (int y = 0; y < 2; y++) {
                int yy = by + y;
                for (int x = 0; x < 4; x++) {
                    int i = x + 4 * yy;
                    if ((inMask & (1 << i)) != 0) {
                        score += chooseModifier(baseColors, baseOff, in, i * 3, c,
                                yy + x * 4, tableOff);
                    }
                }
            }
        } else {
            int bx = second ? 2 : 0;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 2; x++) {
                    int xx = bx + x;
                    int i = xx + 4 * y;
                    if ((inMask & (1 << i)) != 0) {
                        score += chooseModifier(baseColors, baseOff, in, i * 3, c,
                                y + xx * 4, tableOff);
                    }
                }
            }
        }
        c.score = score;
    }

    private static boolean inRange4bitSigned(int color) { return color >= -4 && color <= 3; }

    private static void encodeBaseColors(byte[] baseColors, byte[] colors, EtcCompressed c) {
        int r1, g1, b1, r2, g2, b2;
        int r51 = convert8To5(colors[0]);
        int g51 = convert8To5(colors[1]);
        int b51 = convert8To5(colors[2]);
        int r52 = convert8To5(colors[3]);
        int g52 = convert8To5(colors[4]);
        int b52 = convert8To5(colors[5]);

        r1 = convert5To8(r51);
        g1 = convert5To8(g51);
        b1 = convert5To8(b51);

        int dr = r52 - r51;
        int dg = g52 - g51;
        int db = b52 - b51;

        boolean differential = inRange4bitSigned(dr) && inRange4bitSigned(dg)
                && inRange4bitSigned(db);
        if (differential) {
            r2 = convert5To8(r51 + dr);
            g2 = convert5To8(g51 + dg);
            b2 = convert5To8(b51 + db);
            c.high |= (r51 << 27) | ((7 & dr) << 24) | (g51 << 19)
                    | ((7 & dg) << 16) | (b51 << 11) | ((7 & db) << 8) | 2;
        } else {
            int r41 = convert8To4(colors[0]);
            int g41 = convert8To4(colors[1]);
            int b41 = convert8To4(colors[2]);
            int r42 = convert8To4(colors[3]);
            int g42 = convert8To4(colors[4]);
            int b42 = convert8To4(colors[5]);
            r1 = convert4To8(r41);
            g1 = convert4To8(g41);
            b1 = convert4To8(b41);
            r2 = convert4To8(r42);
            g2 = convert4To8(g42);
            b2 = convert4To8(b42);
            c.high |= (r41 << 28) | (r42 << 24) | (g41 << 20) | (g42 << 16)
                    | (b41 << 12) | (b42 << 8);
        }
        baseColors[0] = (byte) r1;
        baseColors[1] = (byte) g1;
        baseColors[2] = (byte) b1;
        baseColors[3] = (byte) r2;
        baseColors[4] = (byte) g2;
        baseColors[5] = (byte) b2;
    }

    private static void encodeBlockHelper(byte[] in, int inMask, byte[] colors,
                                          EtcCompressed c, boolean flipped) {
        c.score = 0xFFFFFFFFL;
        c.high = flipped ? 1 : 0;
        c.low = 0;

        byte[] baseColors = new byte[6];
        encodeBaseColors(baseColors, colors, c);

        int originalHigh = c.high;

        for (int i = 0; i < 8; i++) {
            EtcCompressed temp = new EtcCompressed();
            temp.score = 0;
            temp.high = originalHigh | (i << 5);
            temp.low = 0;
            encodeSubblockHelper(in, inMask, temp, flipped, false, baseColors, 0, i * 4);
            takeBest(c, temp);
        }

        EtcCompressed firstHalf = new EtcCompressed();
        firstHalf.high = c.high; firstHalf.low = c.low; firstHalf.score = c.score;
        for (int i = 0; i < 8; i++) {
            EtcCompressed temp = new EtcCompressed();
            temp.score = firstHalf.score;
            temp.high = firstHalf.high | (i << 2);
            temp.low = firstHalf.low;
            encodeSubblockHelper(in, inMask, temp, flipped, true, baseColors, 3, i * 4);
            if (i == 0) { c.high = temp.high; c.low = temp.low; c.score = temp.score; }
            else takeBest(c, temp);
        }
    }

    private static void writeBigEndian(byte[] out, int off, int d) {
        out[off]     = (byte) (d >>> 24);
        out[off + 1] = (byte) (d >>> 16);
        out[off + 2] = (byte) (d >>> 8);
        out[off + 3] = (byte) d;
    }

    /** etc1_encode_block. inMask marks which of the 16 pixels are real. */
    private static void encodeBlock(byte[] in, int inMask, byte[] out, int outOff) {
        byte[] colors = new byte[6];
        byte[] flippedColors = new byte[6];
        averageColorsSubblock(in, inMask, colors, 0, false, false);
        averageColorsSubblock(in, inMask, colors, 3, false, true);
        averageColorsSubblock(in, inMask, flippedColors, 0, true, false);
        averageColorsSubblock(in, inMask, flippedColors, 3, true, true);

        EtcCompressed a = new EtcCompressed();
        EtcCompressed b = new EtcCompressed();
        encodeBlockHelper(in, inMask, colors, a, false);
        encodeBlockHelper(in, inMask, flippedColors, b, true);
        takeBest(a, b);
        writeBigEndian(out, outOff, a.high);
        writeBigEndian(out, outOff + 4, a.low);
    }

    private static final int[] K_Y_MASK = { 0x0, 0xf, 0xff, 0xfff, 0xffff };
    private static final int[] K_X_MASK = { 0x0, 0x1111, 0x3333, 0x7777, 0xffff };

    /** etc1_encode_image. */
    private static void encodeImage(byte[] in, int width, int height,
                                    int pixelSize, int stride, byte[] out) {
        if (pixelSize < 2 || pixelSize > 3) {
            throw new IllegalArgumentException("pixelSize must be 2 or 3");
        }
        byte[] block = new byte[ETC1_DECODED_BLOCK_SIZE];
        byte[] encoded = new byte[ETC1_ENCODED_BLOCK_SIZE];
        int encodedWidth = (width + 3) & ~3;
        int encodedHeight = (height + 3) & ~3;
        int outOff = 0;
        for (int y = 0; y < encodedHeight; y += 4) {
            int yEnd = Math.min(height - y, 4);
            int ymask = K_Y_MASK[yEnd];
            for (int x = 0; x < encodedWidth; x += 4) {
                int xEnd = Math.min(width - x, 4);
                int mask = ymask & K_X_MASK[xEnd];
                for (int cy = 0; cy < yEnd; cy++) {
                    int q = (cy * 4) * 3;
                    int p = pixelSize * x + stride * (y + cy);
                    if (pixelSize == 3) {
                        System.arraycopy(in, p, block, q, xEnd * 3);
                    } else {
                        for (int cx = 0; cx < xEnd; cx++) {
                            int pixel = ((in[p + 1] & 0xff) << 8) | (in[p] & 0xff);
                            block[q++] = (byte) convert5To8(pixel >> 11);
                            block[q++] = (byte) convert6To8(pixel >> 5);
                            block[q++] = (byte) convert5To8(pixel);
                            p += pixelSize;
                        }
                    }
                }
                encodeBlock(block, mask, encoded, 0);
                System.arraycopy(encoded, 0, out, outOff, ETC1_ENCODED_BLOCK_SIZE);
                outOff += ETC1_ENCODED_BLOCK_SIZE;
            }
        }
    }
}
