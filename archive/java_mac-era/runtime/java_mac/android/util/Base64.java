// Mac.apk android.util.Base64 -- AOSP-faithful reimplementation.
//
// Previously an empty stub (v1.0.92, auto-generated for F-Droid). SafeDK's
// com.safedk.android.analytics.brandsafety.BrandSafetyUtils.<clinit> calls
// android.util.Base64.decode(String, int) during Application.onActivityCreated,
// and the missing method threw NoSuchMethodError -> ExceptionInInitializerError,
// poisoning the SafeDK/AppLovin init path. Base64 is a public AOSP API present
// on every Android device, so we implement the full public surface here, backed
// by the JDK 21 java.util.Base64 codecs (the macOS-native equivalent). We map
// AOSP's flag bits to the right JDK encoder/decoder and apply NO_WRAP / NO_PADDING
// / URL_SAFE / CRLF semantics ourselves so observable behavior matches Android.
//
// AOSP flag values (stable since API 8; see
// frameworks/base/core/java/android/util/Base64.java):
//   DEFAULT=0  NO_PADDING=1  NO_WRAP=2  CRLF=4  URL_SAFE=8  NO_CLOSE=16
// Default encode wraps output at 76 chars with '\n'; NO_WRAP disables that;
// CRLF uses "\r\n". URL_SAFE uses the '-'/'_' alphabet. The decoder accepts
// both standard and URL-safe alphabets and tolerates missing padding, matching
// Android's lenient decoder.
package android.util;

import java.nio.charset.StandardCharsets;

public class Base64 {
    public static final int DEFAULT    = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP    = 2;
    public static final int CRLF       = 4;
    public static final int URL_SAFE   = 8;
    public static final int NO_CLOSE   = 16;

    private Base64() {}

    // ----- decode -----

    public static byte[] decode(String str, int flags) {
        if (str == null) throw new NullPointerException("str == null");
        return decode(str.getBytes(StandardCharsets.US_ASCII), flags);
    }

    public static byte[] decode(byte[] input, int flags) {
        if (input == null) throw new NullPointerException("input == null");
        return decode(input, 0, input.length, flags);
    }

    // Ported verbatim from frameworks/base/core/java/android/util/Base64.java:
    //   :156-175. It drives the AOSP Decoder state machine that already lives at
    // the bottom of this file (it was written for Base64InputStream/
    // Base64OutputStream and then not used by the public entry points).
    //
    // This REPLACED a JDK-delegating implementation that normalized the input
    // first: it rewrote '-'/'_' to '+'/'/' unconditionally, dropped whitespace,
    // re-padded to a multiple of four, and handed the result to
    // java.util.Base64. That is wrong in both directions, and the errors are
    // opposite in sign, which is why neither showed up as "decoding is broken":
    //
    //   - TOO STRICT. AOSP's table maps EVERY non-alphabet byte to SKIP (-1),
    //     not to an error -- only a misplaced '=' (EQUALS, -2) or a truncated
    //     final tuple fails. So decode("!!!!") on Android is an EMPTY byte
    //     array, and the JDK decoder threw IllegalArgumentException.
    //   - TOO LENIENT, and this one is a correctness hole rather than a nuisance:
    //     translating '-'/'_' to '+'/'/' before decoding meant the STANDARD
    //     alphabet silently accepted URL-safe input and the URL-SAFE alphabet
    //     silently accepted standard input. On Android the two alphabets are
    //     separate tables and each one SKIPs the other's two characters, so
    //     decode("+/+/", URL_SAFE) yields zero bytes rather than 0xfb 0xff 0xbf.
    //     Any code that round-trips a token through the wrong flag got a
    //     plausible-looking answer here and a different one on a device.
    public static byte[] decode(byte[] input, int offset, int len, int flags) {
        if (input == null) throw new NullPointerException("input == null");

        // Allocate space for the most data the input could represent.
        // (It could contain less if it contains whitespace, etc.)
        Decoder decoder = new Decoder(flags, new byte[len * 3 / 4]);

        if (!decoder.process(input, offset, len, true)) {
            throw new IllegalArgumentException("bad base-64");
        }

        // Maybe we got lucky and allocated exactly enough output space.
        if (decoder.op == decoder.output.length) {
            return decoder.output;
        }

        byte[] temp = new byte[decoder.op];
        System.arraycopy(decoder.output, 0, temp, 0, decoder.op);
        return temp;
    }

    // ----- encode -----

    public static byte[] encode(byte[] input, int flags) {
        if (input == null) throw new NullPointerException("input == null");
        return encode(input, 0, input.length, flags);
    }

    public static byte[] encode(byte[] input, int offset, int len, int flags) {
        return encodeToString(input, offset, len, flags)
                .getBytes(StandardCharsets.US_ASCII);
    }

    public static String encodeToString(byte[] input, int flags) {
        if (input == null) throw new NullPointerException("input == null");
        return encodeToString(input, 0, input.length, flags);
    }

    public static String encodeToString(byte[] input, int offset, int len, int flags) {
        if (input == null) throw new NullPointerException("input == null");
        byte[] slice;
        if (offset == 0 && len == input.length) {
            slice = input;
        } else {
            slice = new byte[len];
            System.arraycopy(input, offset, slice, 0, len);
        }

        boolean urlSafe   = (flags & URL_SAFE)   != 0;
        boolean noPadding = (flags & NO_PADDING) != 0;
        boolean noWrap    = (flags & NO_WRAP)    != 0;
        boolean crlf      = (flags & CRLF)       != 0;

        java.util.Base64.Encoder enc = urlSafe
                ? java.util.Base64.getUrlEncoder()
                : java.util.Base64.getEncoder();
        if (noPadding) enc = enc.withoutPadding();

        String out = enc.encodeToString(slice);

        // Android wraps the output every 76 characters unless NO_WRAP is set,
        // and terminates the wrapped output with a trailing line separator.
        if (!noWrap) {
            String sep = crlf ? "\r\n" : "\n";
            StringBuilder sb = new StringBuilder(out.length() + out.length() / 76 * sep.length() + sep.length());
            int i = 0;
            while (i < out.length()) {
                int end = Math.min(i + 76, out.length());
                sb.append(out, i, end);
                sb.append(sep);
                i = end;
            }
            out = sb.toString();
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Streaming Coder (verbatim AOSP, frameworks/base android.util.Base64).
    //
    // The public static encode/decode above stay JDK-backed (swept + stable);
    // these inner classes exist because android.util.Base64OutputStream /
    // Base64InputStream are implemented exactly as AOSP and operate on a
    // Base64.Coder (incremental process() over a caller-supplied output
    // buffer). FirebaseCrashlytics references Base64OutputStream while
    // persisting crash reports; without these the stream classes could not
    // exist and the app hit NoClassDefFoundError. Both code paths emit the
    // same standard base64, so keeping the JDK-backed convenience methods
    // alongside the canonical Coder is behavior-equivalent and zero-risk.
    // ------------------------------------------------------------------

    /* package */ static abstract class Coder {
        public byte[] output;
        public int op;

        public abstract boolean process(byte[] input, int offset, int len, boolean finish);

        public abstract int maxOutputSize(int len);
    }

    /* package */ static class Decoder extends Coder {
        private static final int DECODE[] = {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, -1, 63,
            52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -2, -1, -1,
            -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14,
            15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, -1,
            -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
            41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        };

        private static final int DECODE_WEBSAFE[] = {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1,
            52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -2, -1, -1,
            -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14,
            15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, 63,
            -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
            41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        };

        private static final int SKIP = -1;
        private static final int EQUALS = -2;

        private int state;
        private int value;

        final private int[] alphabet;

        public Decoder(int flags, byte[] output) {
            this.output = output;
            alphabet = ((flags & URL_SAFE) == 0) ? DECODE : DECODE_WEBSAFE;
            state = 0;
            value = 0;
        }

        public int maxOutputSize(int len) {
            return len * 3/4 + 10;
        }

        public boolean process(byte[] input, int offset, int len, boolean finish) {
            if (this.state == 6) return false;

            int p = offset;
            len += offset;

            int state = this.state;
            int value = this.value;
            int op = 0;
            final byte[] output = this.output;
            final int[] alphabet = this.alphabet;

            while (p < len) {
                if (state == 0) {
                    while (p+4 <= len &&
                           (value = ((alphabet[input[p] & 0xff] << 18) |
                                     (alphabet[input[p+1] & 0xff] << 12) |
                                     (alphabet[input[p+2] & 0xff] << 6) |
                                     (alphabet[input[p+3] & 0xff]))) >= 0) {
                        output[op+2] = (byte) value;
                        output[op+1] = (byte) (value >> 8);
                        output[op] = (byte) (value >> 16);
                        op += 3;
                        p += 4;
                    }
                    if (p >= len) break;
                }

                int d = alphabet[input[p++] & 0xff];

                switch (state) {
                case 0:
                    if (d >= 0) {
                        value = d;
                        ++state;
                    } else if (d != SKIP) {
                        this.state = 6;
                        return false;
                    }
                    break;

                case 1:
                    if (d >= 0) {
                        value = (value << 6) | d;
                        ++state;
                    } else if (d != SKIP) {
                        this.state = 6;
                        return false;
                    }
                    break;

                case 2:
                    if (d >= 0) {
                        value = (value << 6) | d;
                        ++state;
                    } else if (d == EQUALS) {
                        output[op++] = (byte) (value >> 4);
                        state = 4;
                    } else if (d != SKIP) {
                        this.state = 6;
                        return false;
                    }
                    break;

                case 3:
                    if (d >= 0) {
                        value = (value << 6) | d;
                        output[op+2] = (byte) value;
                        output[op+1] = (byte) (value >> 8);
                        output[op] = (byte) (value >> 16);
                        op += 3;
                        state = 0;
                    } else if (d == EQUALS) {
                        output[op+1] = (byte) (value >> 2);
                        output[op] = (byte) (value >> 10);
                        op += 2;
                        state = 5;
                    } else if (d != SKIP) {
                        this.state = 6;
                        return false;
                    }
                    break;

                case 4:
                    if (d == EQUALS) {
                        ++state;
                    } else if (d != SKIP) {
                        this.state = 6;
                        return false;
                    }
                    break;

                case 5:
                    if (d != SKIP) {
                        this.state = 6;
                        return false;
                    }
                    break;
                }
            }

            if (!finish) {
                this.state = state;
                this.value = value;
                this.op = op;
                return true;
            }

            switch (state) {
            case 0:
                break;
            case 1:
                this.state = 6;
                return false;
            case 2:
                output[op++] = (byte) (value >> 4);
                break;
            case 3:
                output[op++] = (byte) (value >> 10);
                output[op++] = (byte) (value >> 2);
                break;
            case 4:
                this.state = 6;
                return false;
            case 5:
                break;
            }

            this.state = state;
            this.op = op;
            return true;
        }
    }

    /* package */ static class Encoder extends Coder {
        public static final int LINE_GROUPS = 19;

        private static final byte ENCODE[] = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
            'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
            'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
            'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/',
        };

        private static final byte ENCODE_WEBSAFE[] = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
            'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
            'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
            'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_',
        };

        final private byte[] tail;
        /* package */ int tailLen;
        private int count;

        final public boolean do_padding;
        final public boolean do_newline;
        final public boolean do_cr;
        final private byte[] alphabet;

        public Encoder(int flags, byte[] output) {
            this.output = output;

            do_padding = (flags & NO_PADDING) == 0;
            do_newline = (flags & NO_WRAP) == 0;
            do_cr = (flags & CRLF) != 0;
            alphabet = ((flags & URL_SAFE) == 0) ? ENCODE : ENCODE_WEBSAFE;

            tail = new byte[2];
            tailLen = 0;

            count = do_newline ? LINE_GROUPS : -1;
        }

        public int maxOutputSize(int len) {
            return len * 8/5 + 10;
        }

        public boolean process(byte[] input, int offset, int len, boolean finish) {
            final byte[] alphabet = this.alphabet;
            final byte[] output = this.output;
            int op = 0;
            int count = this.count;

            int p = offset;
            len += offset;
            int v = -1;

            switch (tailLen) {
                case 0:
                    break;

                case 1:
                    if (p+2 <= len) {
                        v = ((tail[0] & 0xff) << 16) |
                            ((input[p++] & 0xff) << 8) |
                            (input[p++] & 0xff);
                        tailLen = 0;
                    };
                    break;

                case 2:
                    if (p+1 <= len) {
                        v = ((tail[0] & 0xff) << 16) |
                            ((tail[1] & 0xff) << 8) |
                            (input[p++] & 0xff);
                        tailLen = 0;
                    }
                    break;
            }

            if (v != -1) {
                output[op++] = alphabet[(v >> 18) & 0x3f];
                output[op++] = alphabet[(v >> 12) & 0x3f];
                output[op++] = alphabet[(v >> 6) & 0x3f];
                output[op++] = alphabet[v & 0x3f];
                if (--count == 0) {
                    if (do_cr) output[op++] = '\r';
                    output[op++] = '\n';
                    count = LINE_GROUPS;
                }
            }

            while (p+3 <= len) {
                v = ((input[p] & 0xff) << 16) |
                    ((input[p+1] & 0xff) << 8) |
                    (input[p+2] & 0xff);
                output[op] = alphabet[(v >> 18) & 0x3f];
                output[op+1] = alphabet[(v >> 12) & 0x3f];
                output[op+2] = alphabet[(v >> 6) & 0x3f];
                output[op+3] = alphabet[v & 0x3f];
                p += 3;
                op += 4;
                if (--count == 0) {
                    if (do_cr) output[op++] = '\r';
                    output[op++] = '\n';
                    count = LINE_GROUPS;
                }
            }

            if (finish) {
                if (p-tailLen == len-1) {
                    int t = 0;
                    v = ((tailLen > 0 ? tail[t++] : input[p++]) & 0xff) << 4;
                    tailLen -= t;
                    output[op++] = alphabet[(v >> 6) & 0x3f];
                    output[op++] = alphabet[v & 0x3f];
                    if (do_padding) {
                        output[op++] = '=';
                        output[op++] = '=';
                    }
                    if (do_newline) {
                        if (do_cr) output[op++] = '\r';
                        output[op++] = '\n';
                    }
                } else if (p-tailLen == len-2) {
                    int t = 0;
                    v = (((tailLen > 1 ? tail[t++] : input[p++]) & 0xff) << 10) |
                        (((tailLen > 0 ? tail[t++] : input[p++]) & 0xff) << 2);
                    tailLen -= t;
                    output[op++] = alphabet[(v >> 12) & 0x3f];
                    output[op++] = alphabet[(v >> 6) & 0x3f];
                    output[op++] = alphabet[v & 0x3f];
                    if (do_padding) {
                        output[op++] = '=';
                    }
                    if (do_newline) {
                        if (do_cr) output[op++] = '\r';
                        output[op++] = '\n';
                    }
                } else if (do_newline && op > 0 && count != LINE_GROUPS) {
                    if (do_cr) output[op++] = '\r';
                    output[op++] = '\n';
                }

                assert tailLen == 0;
                assert p == len;
            } else {
                if (p == len-1) {
                    tail[tailLen++] = input[p];
                } else if (p == len-2) {
                    tail[tailLen++] = input[p];
                    tail[tailLen++] = input[p+1];
                }
            }

            this.op = op;
            this.count = count;

            return true;
        }
    }
}
