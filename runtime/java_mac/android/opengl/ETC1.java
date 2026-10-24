// android.opengl.ETC1 -- encode/decode ETC1 (OES_compressed_ETC1_RGB8_texture)
// blocks and images, and read/write the 16-byte PKM header (API 8).
//
// AOSP declares every method `native`; the bodies live in two places, both
// ported here line for line:
//   * the codec:  frameworks/native/opengl/libs/ETC1/etc1.cpp (android14-release)
//   * the glue:   frameworks/base/core/jni/android/opengl/util.cpp, etc1_* --
//                 argument checks, IllegalArgumentException messages, and the
//                 Buffer handling (BufferHelper / getPointer).
// A pure-Java body instead of JNI is the host-first answer here: the codec is
// plain integer arithmetic with no platform dependency, so there is nothing for
// macOS to route to and no reason to cross JNI. The method descriptors are the
// SDK's, so app call sites link unchanged (the `native` flag is not part of a
// method's linkage).
//
// Faithful quirks, kept on purpose -- each is what a device does:
//  * DIRECT BUFFERS ONLY. getPointer() takes the address from java.nio.Buffer's
//    `address` field via jniGetNioBufferFields, and on Android that field stays
//    0 for a heap buffer (libcore ojluni HeapByteBuffer: "Android-removed:
//    buffer.address is only used by Direct*Buffer"), so every method throws
//    IllegalArgumentException("in"/"out"/"header") for a non-direct buffer.
//  * encodeBlock rejects validPixelMask outside [0, 15] even though the mask is
//    documented as 16 bits (util.cpp: `validPixelMask < 0 || validPixelMask >
//    15`). A full mask of 0xffff therefore throws, on a device and here.
//  * Buffers are addressed as RAW MEMORY from position() onward, (limit -
//    position) << elementSizeShift bytes, and positions are never moved -- the
//    JNI side never touches them. A ShortBuffer/IntBuffer view is read and
//    written through its own byte order, which is exactly how its bytes lie in
//    the direct memory it views.
//
// Relationship to ETC1Util: ETC1Util.java carries its own private port of the
// same etc1.cpp (written when this class did not exist). The two are
// independent ports of one source and debug/fwgap_gate.sh asserts they produce
// byte-identical output, so each is the other's oracle. Census + method:
// docs/fwgap-new-platform-classes_2026-09-26.md.
package android.opengl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

/**
 * Methods for encoding and decoding ETC1 textures.
 * <p>
 * The PKM file format is of a 16-byte header that describes the image bounds
 * followed by the encoded ETC1 texture data.
 *
 * @see ETC1Util
 */
public class ETC1 {

    /** Size in bytes of an encoded block. */
    public static final int ENCODED_BLOCK_SIZE = 8;

    /** Size in bytes of a decoded block. */
    public static final int DECODED_BLOCK_SIZE = 48;

    /** Size of a PKM file header, in bytes. */
    public static final int ETC_PKM_HEADER_SIZE = 16;

    /** Accepted by the internalformat parameter of glCompressedTexImage2D. */
    public static final int ETC1_RGB8_OES = 0x8D64;

    public ETC1() {
    }

    // ------------------------------------------------------------------
    // Public API -- util.cpp's etc1_* JNI functions.
    // ------------------------------------------------------------------

    /**
     * Encode a block of pixels.
     *
     * @param in a native order direct buffer of size DECODED_BLOCK_SIZE: a 4 x 4
     *        square of 3-byte R, G, B pixels, pixel (x, y) at byte 3 * (x + 4 * y).
     * @param validPixelMask bit (1 << (x + y * 4)) marks pixel (x, y) valid.
     * @param out a native order direct buffer of size ENCODED_BLOCK_SIZE.
     */
    public static void encodeBlock(Buffer in, int validPixelMask, Buffer out) {
        if (validPixelMask < 0 || validPixelMask > 15) {
            throw new IllegalArgumentException("validPixelMask");
        }
        checkPointer(in, "in");
        checkPointer(out, "out");
        if (remainingBytes(in) < DECODED_BLOCK_SIZE) {
            throw new IllegalArgumentException("in's remaining data < DECODED_BLOCK_SIZE");
        } else if (remainingBytes(out) < ENCODED_BLOCK_SIZE) {
            throw new IllegalArgumentException("out's remaining data < ENCODED_BLOCK_SIZE");
        }
        int[] block = toUnsigned(readRaw(in, DECODED_BLOCK_SIZE));
        byte[] encoded = new byte[ENCODED_BLOCK_SIZE];
        encodeBlockImpl(block, 0, validPixelMask, encoded, 0);
        writeRaw(out, encoded, ENCODED_BLOCK_SIZE);
    }

    /**
     * Decode a block of pixels.
     *
     * @param in a native order direct buffer of size ENCODED_BLOCK_SIZE.
     * @param out a native order direct buffer of size DECODED_BLOCK_SIZE.
     */
    public static void decodeBlock(Buffer in, Buffer out) {
        checkPointer(in, "in");
        checkPointer(out, "out");
        if (remainingBytes(in) < ENCODED_BLOCK_SIZE) {
            throw new IllegalArgumentException("in's remaining data < ENCODED_BLOCK_SIZE");
        } else if (remainingBytes(out) < DECODED_BLOCK_SIZE) {
            throw new IllegalArgumentException("out's remaining data < DECODED_BLOCK_SIZE");
        }
        byte[] encoded = readRaw(in, ENCODED_BLOCK_SIZE);
        byte[] decoded = new byte[DECODED_BLOCK_SIZE];
        decodeBlockImpl(encoded, 0, decoded, 0);
        writeRaw(out, decoded, DECODED_BLOCK_SIZE);
    }

    /** Return the size of the encoded image data (does not include size of PKM header). */
    public static int getEncodedDataSize(int width, int height) {
        // etc1_get_encoded_data_size works in etc1_uint32: the product wraps
        // mod 2^32 BEFORE the shift, and the jint return is those 32 bits.
        long w = (width + 3L) & 0xfffffffcL;
        long h = (height + 3L) & 0xfffffffcL;
        return (int) (((w * h) & 0xffffffffL) >>> 1);
    }

    /**
     * Encode an entire image.
     *
     * @param in a native order direct buffer of the image data: pixel (x,y) is at
     *        pIn + pixelSize * x + stride * y.
     * @param pixelSize must be 2 or 3. 2 is a GL_UNSIGNED_SHORT_5_6_5 image, 3 is
     *        a GL_BYTE RGB image.
     * @param out a native order direct buffer large enough for the encoded image.
     */
    public static void encodeImage(Buffer in, int width, int height,
            int pixelSize, int stride, Buffer out) {
        if (pixelSize < 2 || pixelSize > 3) {
            throw new IllegalArgumentException("pixelSize must be 2 or 3");
        }
        checkPointer(in, "in");
        checkPointer(out, "out");
        int imageSize = stride * height;
        int encodedImageSize = getEncodedDataSize(width, height);
        if (remainingBytes(in) < imageSize) {
            throw new IllegalArgumentException("in's remaining data < image size");
        } else if (remainingBytes(out) < encodedImageSize) {
            throw new IllegalArgumentException("out's remaining data < encoded image size");
        }
        int[] src = toUnsigned(readRaw(in, remainingBytes(in)));
        byte[] dst = new byte[encodedImageSize];
        encodeImageImpl(src, width, height, pixelSize, stride, dst);
        writeRaw(out, dst, encodedImageSize);
    }

    /**
     * Decode an entire image.
     *
     * @param in native order direct buffer of the encoded data.
     * @param out native order direct buffer of the image data: pixel (x,y) is at
     *        pOut + pixelSize * x + stride * y.
     * @param pixelSize must be 2 or 3.
     */
    public static void decodeImage(Buffer in, Buffer out,
            int width, int height, int pixelSize, int stride) {
        if (pixelSize < 2 || pixelSize > 3) {
            throw new IllegalArgumentException("pixelSize must be 2 or 3");
        }
        checkPointer(in, "in");
        checkPointer(out, "out");
        int imageSize = stride * height;
        int encodedImageSize = getEncodedDataSize(width, height);
        if (remainingBytes(in) < encodedImageSize) {
            throw new IllegalArgumentException("in's remaining data < encoded image size");
        } else if (remainingBytes(out) < imageSize) {
            throw new IllegalArgumentException("out's remaining data < image size");
        }
        byte[] src = readRaw(in, encodedImageSize);
        // Start from the buffer's CURRENT bytes: etc1_decode_image writes only
        // the pixels of the image, so padding bytes in each stride row (and any
        // row tail beyond width * pixelSize) must survive untouched.
        byte[] dst = readRaw(out, imageSize);
        decodeImageImpl(src, dst, width, height, pixelSize, stride);
        writeRaw(out, dst, imageSize);
    }

    /** Format a PKM header. */
    public static void formatHeader(Buffer header, int width, int height) {
        checkPointer(header, "header");
        if (remainingBytes(header) < ETC_PKM_HEADER_SIZE) {
            throw new IllegalArgumentException("header's remaining data < ETC_PKM_HEADER_SIZE");
        }
        byte[] h = new byte[ETC_PKM_HEADER_SIZE];
        System.arraycopy(PKM_MAGIC, 0, h, 0, PKM_MAGIC.length);
        writeBEUint16(h, PKM_FORMAT_OFFSET, ETC1_RGB_NO_MIPMAPS);
        writeBEUint16(h, PKM_ENCODED_WIDTH_OFFSET, (width + 3) & ~3);
        writeBEUint16(h, PKM_ENCODED_HEIGHT_OFFSET, (height + 3) & ~3);
        writeBEUint16(h, PKM_WIDTH_OFFSET, width);
        writeBEUint16(h, PKM_HEIGHT_OFFSET, height);
        writeRaw(header, h, ETC_PKM_HEADER_SIZE);
    }

    /** Check if a PKM header is correctly formatted. */
    public static boolean isValid(Buffer header) {
        byte[] h = readHeader(header);
        for (int i = 0; i < PKM_MAGIC.length; i++) {
            if (h[i] != PKM_MAGIC[i]) return false;
        }
        int format = readBEUint16(h, PKM_FORMAT_OFFSET);
        int encodedWidth = readBEUint16(h, PKM_ENCODED_WIDTH_OFFSET);
        int encodedHeight = readBEUint16(h, PKM_ENCODED_HEIGHT_OFFSET);
        int width = readBEUint16(h, PKM_WIDTH_OFFSET);
        int height = readBEUint16(h, PKM_HEIGHT_OFFSET);
        return format == ETC1_RGB_NO_MIPMAPS
                && encodedWidth >= width && encodedWidth - width < 4
                && encodedHeight >= height && encodedHeight - height < 4;
    }

    /** Read the image width from a PKM header. */
    public static int getWidth(Buffer header) {
        return readBEUint16(readHeader(header), PKM_WIDTH_OFFSET);
    }

    /** Read the image height from a PKM header. */
    public static int getHeight(Buffer header) {
        return readBEUint16(readHeader(header), PKM_HEIGHT_OFFSET);
    }

    // ------------------------------------------------------------------
    // util.cpp's BufferHelper / getPointer, over java.nio instead of an
    // address: the byte at raw offset i (from position) of a buffer whose
    // elements are 1 << shift bytes is byte (i & mask) of element
    // position + (i >> shift), laid out in the buffer's own order.
    // ------------------------------------------------------------------

    private static void checkPointer(Buffer b, String errorMessage) {
        if (b == null || !b.isDirect()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private static int elementSizeShift(Buffer b) {
        if (b instanceof ByteBuffer) return 0;
        if (b instanceof ShortBuffer || b instanceof CharBuffer) return 1;
        if (b instanceof IntBuffer || b instanceof FloatBuffer) return 2;
        if (b instanceof LongBuffer || b instanceof DoubleBuffer) return 3;
        throw new IllegalArgumentException("unsupported Buffer " + b.getClass().getName());
    }

    private static int remainingBytes(Buffer b) {
        return (b.limit() - b.position()) << elementSizeShift(b);
    }

    private static ByteOrder order(Buffer b) {
        if (b instanceof ByteBuffer) return ((ByteBuffer) b).order();
        if (b instanceof ShortBuffer) return ((ShortBuffer) b).order();
        if (b instanceof CharBuffer) return ((CharBuffer) b).order();
        if (b instanceof IntBuffer) return ((IntBuffer) b).order();
        if (b instanceof FloatBuffer) return ((FloatBuffer) b).order();
        if (b instanceof LongBuffer) return ((LongBuffer) b).order();
        return ((DoubleBuffer) b).order();
    }

    /** The element at absolute index {@code idx} as raw bits. */
    private static long getBits(Buffer b, int idx) {
        if (b instanceof ShortBuffer) return ((ShortBuffer) b).get(idx) & 0xffffL;
        if (b instanceof CharBuffer) return ((CharBuffer) b).get(idx);
        if (b instanceof IntBuffer) return ((IntBuffer) b).get(idx) & 0xffffffffL;
        if (b instanceof FloatBuffer) {
            return Float.floatToRawIntBits(((FloatBuffer) b).get(idx)) & 0xffffffffL;
        }
        if (b instanceof LongBuffer) return ((LongBuffer) b).get(idx);
        return Double.doubleToRawLongBits(((DoubleBuffer) b).get(idx));
    }

    private static void putBits(Buffer b, int idx, long v) {
        if (b instanceof ShortBuffer) ((ShortBuffer) b).put(idx, (short) v);
        else if (b instanceof CharBuffer) ((CharBuffer) b).put(idx, (char) v);
        else if (b instanceof IntBuffer) ((IntBuffer) b).put(idx, (int) v);
        else if (b instanceof FloatBuffer) ((FloatBuffer) b).put(idx, Float.intBitsToFloat((int) v));
        else if (b instanceof LongBuffer) ((LongBuffer) b).put(idx, v);
        else ((DoubleBuffer) b).put(idx, Double.longBitsToDouble(v));
    }

    /** Copy {@code count} raw bytes starting at position(); position is not moved. */
    private static byte[] readRaw(Buffer b, int count) {
        byte[] out = new byte[count];
        int pos = b.position();
        if (b instanceof ByteBuffer) {
            ByteBuffer bb = (ByteBuffer) b;
            for (int i = 0; i < count; i++) out[i] = bb.get(pos + i);
            return out;
        }
        int shift = elementSizeShift(b);
        int size = 1 << shift;
        boolean little = order(b) == ByteOrder.LITTLE_ENDIAN;
        for (int i = 0; i < count; i += size) {
            long v = getBits(b, pos + (i >> shift));
            for (int k = 0; k < size && i + k < count; k++) {
                int byteInElem = little ? k : (size - 1 - k);
                out[i + k] = (byte) (v >>> (8 * byteInElem));
            }
        }
        return out;
    }

    /** Write {@code count} raw bytes starting at position(); position is not moved. */
    private static void writeRaw(Buffer b, byte[] src, int count) {
        int pos = b.position();
        if (b instanceof ByteBuffer) {
            ByteBuffer bb = (ByteBuffer) b;
            for (int i = 0; i < count; i++) bb.put(pos + i, src[i]);
            return;
        }
        int shift = elementSizeShift(b);
        int size = 1 << shift;
        boolean little = order(b) == ByteOrder.LITTLE_ENDIAN;
        for (int i = 0; i < count; i += size) {
            int idx = pos + (i >> shift);
            long v = getBits(b, idx);              // keep bytes past `count`
            for (int k = 0; k < size && i + k < count; k++) {
                int sh = 8 * (little ? k : (size - 1 - k));
                v = (v & ~(0xffL << sh)) | ((src[i + k] & 0xffL) << sh);
            }
            putBits(b, idx, v);
        }
    }

    private static byte[] readHeader(Buffer header) {
        checkPointer(header, "header");
        if (remainingBytes(header) < ETC_PKM_HEADER_SIZE) {
            throw new IllegalArgumentException("header's remaining data < ETC_PKM_HEADER_SIZE");
        }
        return readRaw(header, ETC_PKM_HEADER_SIZE);
    }

    private static int[] toUnsigned(byte[] b) {
        int[] out = new int[b.length];
        for (int i = 0; i < b.length; i++) out[i] = b[i] & 0xff;
        return out;
    }

    // ------------------------------------------------------------------
    // etc1.cpp. etc1_uint32 is a Java int used as 32 raw bits (>>> for
    // shifts); scores, which C compares UNSIGNED against ~0, are longs.
    // ------------------------------------------------------------------

    private static final int[] K_MODIFIER_TABLE = {
        /* 0 */ 2, 8, -2, -8,
        /* 1 */ 5, 17, -5, -17,
        /* 2 */ 9, 29, -9, -29,
        /* 3 */ 13, 42, -13, -42,
        /* 4 */ 18, 60, -18, -60,
        /* 5 */ 24, 80, -24, -80,
        /* 6 */ 33, 106, -33, -106,
        /* 7 */ 47, 183, -47, -183 };

    private static final int[] K_LOOKUP = { 0, 1, 2, 3, -4, -3, -2, -1 };

    private static int clamp(int x) {
        return x >= 0 ? (x < 255 ? x : 255) : 0;
    }

    private static int convert4To8(int b) {
        int c = b & 0xf;
        return (c << 4) | c;
    }

    private static int convert5To8(int b) {
        int c = b & 0x1f;
        return (c << 3) | (c >> 2);
    }

    private static int convert6To8(int b) {
        int c = b & 0x3f;
        return (c << 2) | (c >> 4);
    }

    private static int divideBy255(int d) {
        return (d + 128 + (d >> 8)) >> 8;
    }

    private static int convert8To4(int b) {
        return divideBy255((b & 0xff) * 15);
    }

    private static int convert8To5(int b) {
        return divideBy255((b & 0xff) * 31);
    }

    private static int convertDiff(int base, int diff) {
        return convert5To8((0x1f & base) + K_LOOKUP[0x7 & diff]);
    }

    private static void decodeSubblock(byte[] out, int outOff, int r, int g, int b,
            int tableOff, int low, boolean second, boolean flipped) {
        int baseX = 0;
        int baseY = 0;
        if (second) {
            if (flipped) {
                baseY = 2;
            } else {
                baseX = 2;
            }
        }
        for (int i = 0; i < 8; i++) {
            int x, y;
            if (flipped) {
                x = baseX + (i >> 1);
                y = baseY + (i & 1);
            } else {
                x = baseX + (i >> 2);
                y = baseY + (i & 3);
            }
            int k = y + (x * 4);
            int offset = ((low >>> k) & 1) | ((low >>> (k + 15)) & 2);
            int delta = K_MODIFIER_TABLE[tableOff + offset];
            int q = outOff + 3 * (x + 4 * y);
            out[q] = (byte) clamp(r + delta);
            out[q + 1] = (byte) clamp(g + delta);
            out[q + 2] = (byte) clamp(b + delta);
        }
    }

    /** etc1_decode_block: 8 encoded bytes -> 4x4 RGB888 (48 bytes). */
    private static void decodeBlockImpl(byte[] in, int inOff, byte[] out, int outOff) {
        int high = ((in[inOff] & 0xff) << 24) | ((in[inOff + 1] & 0xff) << 16)
                | ((in[inOff + 2] & 0xff) << 8) | (in[inOff + 3] & 0xff);
        int low = ((in[inOff + 4] & 0xff) << 24) | ((in[inOff + 5] & 0xff) << 16)
                | ((in[inOff + 6] & 0xff) << 8) | (in[inOff + 7] & 0xff);
        int r1, r2, g1, g2, b1, b2;
        if ((high & 2) != 0) {
            // differential
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
            // not differential
            r1 = convert4To8(high >>> 28);
            r2 = convert4To8(high >>> 24);
            g1 = convert4To8(high >>> 20);
            g2 = convert4To8(high >>> 16);
            b1 = convert4To8(high >>> 12);
            b2 = convert4To8(high >>> 8);
        }
        int tableIndexA = 7 & (high >>> 5);
        int tableIndexB = 7 & (high >>> 2);
        boolean flipped = (high & 1) != 0;
        decodeSubblock(out, outOff, r1, g1, b1, tableIndexA * 4, low, false, flipped);
        decodeSubblock(out, outOff, r2, g2, b2, tableIndexB * 4, low, true, flipped);
    }

    /** etc_compressed: high/low are raw 32-bit words, score is C's etc1_uint32. */
    private static final class Compressed {
        int high;
        int low;
        long score;

        void set(Compressed o) {
            high = o.high;
            low = o.low;
            score = o.score;
        }
    }

    private static void takeBest(Compressed a, Compressed b) {
        if (a.score > b.score) {
            a.set(b);
        }
    }

    private static void averageColorsSubblock(int[] in, int inOff, int inMask,
            int[] colors, int colorsOff, boolean flipped, boolean second) {
        int r = 0;
        int g = 0;
        int b = 0;
        if (flipped) {
            int by = second ? 2 : 0;
            for (int y = 0; y < 2; y++) {
                int yy = by + y;
                for (int x = 0; x < 4; x++) {
                    int i = x + 4 * yy;
                    if ((inMask & (1 << i)) != 0) {
                        int p = inOff + i * 3;
                        r += in[p];
                        g += in[p + 1];
                        b += in[p + 2];
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
                        int p = inOff + i * 3;
                        r += in[p];
                        g += in[p + 1];
                        b += in[p + 2];
                    }
                }
            }
        }
        // etc1_byte stores: the low 8 bits.
        colors[colorsOff] = ((r + 4) >> 3) & 0xff;
        colors[colorsOff + 1] = ((g + 4) >> 3) & 0xff;
        colors[colorsOff + 2] = ((b + 4) >> 3) & 0xff;
    }

    private static int square(int x) {
        return x * x;
    }

    /** chooseModifier: returns the score and ORs the pixel's index bits into c.low. */
    private static long chooseModifier(int[] baseColors, int baseOff, int[] in, int inOff,
            Compressed c, int bitIndex, int tableOff) {
        long bestScore = 0xffffffffL;
        int bestIndex = 0;
        int pixelR = in[inOff];
        int pixelG = in[inOff + 1];
        int pixelB = in[inOff + 2];
        int r = baseColors[baseOff];
        int g = baseColors[baseOff + 1];
        int b = baseColors[baseOff + 2];
        for (int i = 0; i < 4; i++) {
            int modifier = K_MODIFIER_TABLE[tableOff + i];
            int decodedG = clamp(g + modifier);
            long score = 6L * square(decodedG - pixelG);
            if (score >= bestScore) {
                continue;
            }
            int decodedR = clamp(r + modifier);
            score += 3L * square(decodedR - pixelR);
            if (score >= bestScore) {
                continue;
            }
            int decodedB = clamp(b + modifier);
            score += square(decodedB - pixelB);
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        int lowMask = (((bestIndex >> 1) << 16) | (bestIndex & 1)) << bitIndex;
        c.low |= lowMask;
        return bestScore;
    }

    private static void encodeSubblockHelper(int[] in, int inOff, int inMask, Compressed c,
            boolean flipped, boolean second, int[] baseColors, int baseOff, int tableOff) {
        // C: `int score = pCompressed->score;` then adds and stores back into an
        // etc1_uint32. Scores here never approach 2^31, so the int round trip
        // is value-preserving; long keeps the unsigned compare in takeBest exact.
        long score = c.score;
        if (flipped) {
            int by = second ? 2 : 0;
            for (int y = 0; y < 2; y++) {
                int yy = by + y;
                for (int x = 0; x < 4; x++) {
                    int i = x + 4 * yy;
                    if ((inMask & (1 << i)) != 0) {
                        score += chooseModifier(baseColors, baseOff, in, inOff + i * 3,
                                c, yy + x * 4, tableOff);
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
                        score += chooseModifier(baseColors, baseOff, in, inOff + i * 3,
                                c, y + xx * 4, tableOff);
                    }
                }
            }
        }
        c.score = score & 0xffffffffL;
    }

    private static boolean inRange4bitSigned(int color) {
        return color >= -4 && color <= 3;
    }

    private static void encodeBaseColors(int[] baseColors, int[] colors, int colorsOff,
            Compressed c) {
        int r1, g1, b1, r2, g2, b2; // 8 bit base colors for sub-blocks
        boolean differential;
        int r51 = convert8To5(colors[colorsOff]);
        int g51 = convert8To5(colors[colorsOff + 1]);
        int b51 = convert8To5(colors[colorsOff + 2]);
        int r52 = convert8To5(colors[colorsOff + 3]);
        int g52 = convert8To5(colors[colorsOff + 4]);
        int b52 = convert8To5(colors[colorsOff + 5]);

        r1 = convert5To8(r51);
        g1 = convert5To8(g51);
        b1 = convert5To8(b51);

        int dr = r52 - r51;
        int dg = g52 - g51;
        int db = b52 - b51;

        differential = inRange4bitSigned(dr) && inRange4bitSigned(dg)
                && inRange4bitSigned(db);
        if (differential) {
            r2 = convert5To8(r51 + dr);
            g2 = convert5To8(g51 + dg);
            b2 = convert5To8(b51 + db);
            c.high |= (r51 << 27) | ((7 & dr) << 24) | (g51 << 19)
                    | ((7 & dg) << 16) | (b51 << 11) | ((7 & db) << 8) | 2;
        } else {
            int r41 = convert8To4(colors[colorsOff]);
            int g41 = convert8To4(colors[colorsOff + 1]);
            int b41 = convert8To4(colors[colorsOff + 2]);
            int r42 = convert8To4(colors[colorsOff + 3]);
            int g42 = convert8To4(colors[colorsOff + 4]);
            int b42 = convert8To4(colors[colorsOff + 5]);
            r1 = convert4To8(r41);
            g1 = convert4To8(g41);
            b1 = convert4To8(b41);
            r2 = convert4To8(r42);
            g2 = convert4To8(g42);
            b2 = convert4To8(b42);
            c.high |= (r41 << 28) | (r42 << 24) | (g41 << 20) | (g42 << 16)
                    | (b41 << 12) | (b42 << 8);
        }
        baseColors[0] = r1 & 0xff;
        baseColors[1] = g1 & 0xff;
        baseColors[2] = b1 & 0xff;
        baseColors[3] = r2 & 0xff;
        baseColors[4] = g2 & 0xff;
        baseColors[5] = b2 & 0xff;
    }

    private static void encodeBlockHelper(int[] in, int inOff, int inMask, int[] colors,
            int colorsOff, Compressed c, boolean flipped) {
        c.score = 0xffffffffL;
        c.high = flipped ? 1 : 0;
        c.low = 0;

        int[] baseColors = new int[6];

        encodeBaseColors(baseColors, colors, colorsOff, c);

        int originalHigh = c.high;

        for (int i = 0; i < 8; i++) {
            Compressed temp = new Compressed();
            temp.score = 0;
            temp.high = originalHigh | (i << 5);
            temp.low = 0;
            encodeSubblockHelper(in, inOff, inMask, temp, flipped, false,
                    baseColors, 0, i * 4);
            takeBest(c, temp);
        }
        Compressed firstHalf = new Compressed();
        firstHalf.set(c);
        for (int i = 0; i < 8; i++) {
            Compressed temp = new Compressed();
            temp.score = firstHalf.score;
            temp.high = firstHalf.high | (i << 2);
            temp.low = firstHalf.low;
            encodeSubblockHelper(in, inOff, inMask, temp, flipped, true,
                    baseColors, 3, i * 4);
            if (i == 0) {
                c.set(temp);
            } else {
                takeBest(c, temp);
            }
        }
    }

    private static void writeBigEndian(byte[] out, int off, int d) {
        out[off] = (byte) (d >>> 24);
        out[off + 1] = (byte) (d >>> 16);
        out[off + 2] = (byte) (d >>> 8);
        out[off + 3] = (byte) d;
    }

    /** etc1_encode_block: 4x4 RGB888 (unsigned ints) -> 8 encoded bytes. */
    private static void encodeBlockImpl(int[] in, int inOff, int inMask, byte[] out, int outOff) {
        int[] colors = new int[6];
        int[] flippedColors = new int[6];
        averageColorsSubblock(in, inOff, inMask, colors, 0, false, false);
        averageColorsSubblock(in, inOff, inMask, colors, 3, false, true);
        averageColorsSubblock(in, inOff, inMask, flippedColors, 0, true, false);
        averageColorsSubblock(in, inOff, inMask, flippedColors, 3, true, true);

        Compressed a = new Compressed();
        Compressed b = new Compressed();
        encodeBlockHelper(in, inOff, inMask, colors, 0, a, false);
        encodeBlockHelper(in, inOff, inMask, flippedColors, 0, b, true);
        takeBest(a, b);
        writeBigEndian(out, outOff, a.high);
        writeBigEndian(out, outOff + 4, a.low);
    }

    private static final int[] K_Y_MASK = { 0x0, 0xf, 0xff, 0xfff, 0xffff };
    private static final int[] K_X_MASK = { 0x0, 0x1111, 0x3333, 0x7777, 0xffff };

    /** etc1_encode_image (pixelSize already validated by the caller, as in util.cpp). */
    private static void encodeImageImpl(int[] in, int width, int height, int pixelSize,
            int stride, byte[] out) {
        // `block` persists across iterations exactly as the C stack array does:
        // a partial edge block copies only its valid pixels and the mask keeps
        // the stale remainder out of every computation.
        int[] block = new int[DECODED_BLOCK_SIZE];
        int outOff = 0;
        int encodedWidth = (width + 3) & ~3;
        int encodedHeight = (height + 3) & ~3;

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
                            int pixel = (in[p + 1] << 8) | in[p];
                            block[q++] = convert5To8(pixel >> 11);
                            block[q++] = convert6To8(pixel >> 5);
                            block[q++] = convert5To8(pixel);
                            p += pixelSize;
                        }
                    }
                }
                encodeBlockImpl(block, 0, mask, out, outOff);
                outOff += ENCODED_BLOCK_SIZE;
            }
        }
    }

    /** etc1_decode_image (pixelSize already validated by the caller). */
    private static void decodeImageImpl(byte[] in, byte[] out, int width, int height,
            int pixelSize, int stride) {
        byte[] block = new byte[DECODED_BLOCK_SIZE];
        int inOff = 0;
        int encodedWidth = (width + 3) & ~3;
        int encodedHeight = (height + 3) & ~3;

        for (int y = 0; y < encodedHeight; y += 4) {
            int yEnd = Math.min(height - y, 4);
            for (int x = 0; x < encodedWidth; x += 4) {
                int xEnd = Math.min(width - x, 4);
                decodeBlockImpl(in, inOff, block, 0);
                inOff += ENCODED_BLOCK_SIZE;
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

    // PKM header (etc1.cpp kMagic + *_OFFSET; big-endian uint16 fields).
    private static final byte[] PKM_MAGIC = { 'P', 'K', 'M', ' ', '1', '0' };
    private static final int PKM_FORMAT_OFFSET = 6;
    private static final int PKM_ENCODED_WIDTH_OFFSET = 8;
    private static final int PKM_ENCODED_HEIGHT_OFFSET = 10;
    private static final int PKM_WIDTH_OFFSET = 12;
    private static final int PKM_HEIGHT_OFFSET = 14;
    private static final int ETC1_RGB_NO_MIPMAPS = 0;

    private static void writeBEUint16(byte[] p, int off, int data) {
        p[off] = (byte) (data >> 8);
        p[off + 1] = (byte) data;
    }

    private static int readBEUint16(byte[] p, int off) {
        return ((p[off] & 0xff) << 8) | (p[off + 1] & 0xff);
    }
}
