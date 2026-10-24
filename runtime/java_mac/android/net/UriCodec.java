// android.net.UriCodec -- a faithful port of AOSP
// frameworks/base/core/java/android/net/UriCodec.java (android-11.0.0_r48).
//
// WHY this exists as its own class rather than living inside Uri.decode():
// AOSP's Uri has TWO different decoders and the difference is observable.
// Uri.decode(s) calls UriCodec.decode(s, convertPlus=FALSE, ...) so '+' stays a
// literal '+', but Uri.getQueryParameter(key) calls it with convertPlus=TRUE
// (Uri.java:1738) so "?q=a+b" reads back as "a b" -- while
// Uri.getQueryParameters(key) uses plain Uri.decode and reads back "a+b".
// Collapsing the two into one decoder silently corrupts one of those callers.
//
// The other load-bearing behaviour is the MALFORMED-escape path. Every AOSP
// caller passes throwOnFailure=false, which means a bad escape does NOT pass
// the '%' through: it emits U+FFFD. "100%" -- an everyday query value -- decodes
// to "100\ufffd" on a real device, not "100%". The exact garbage for the
// partially-valid forms falls out of the control flow below and is reproduced
// here rather than approximated:
//   "%"   -> "\ufffd"           (end of input during the first hex digit)
//   "%2"  -> "\ufffd"           (end of input during the second; the 0x2 nibble
//                                is DISCARDED because that arm returns)
//   "%zz" -> "\ufffd" + NUL + "z"   (the inner `break` only leaves the DIGIT
//                                loop, so the zero-valued hexValue is still put
//                                into the byte accumulator and flushed later)
// Do not "clean these up" -- they are what the emulator answers.
package android.net;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

/**
 * Decodes "application/x-www-form-urlencoded" content.
 *
 * @hide
 */
public final class UriCodec {

    private UriCodec() {}

    /**
     * Interprets a char as hex digits, returning a number from -1 (invalid char)
     * to 15 ('f').
     */
    private static int hexCharToValue(char c) {
        if ('0' <= c && c <= '9') {
            return c - '0';
        }
        if ('a' <= c && c <= 'f') {
            return 10 + c - 'a';
        }
        if ('A' <= c && c <= 'F') {
            return 10 + c - 'A';
        }
        return -1;
    }

    private static URISyntaxException unexpectedCharacterException(
            String uri, String name, char unexpected, int index) {
        String nameString = (name == null) ? "" : " in [" + name + "]";
        return new URISyntaxException(
                uri, "Unexpected character" + nameString + ": " + unexpected, index);
    }

    private static char getNextCharacter(String uri, int index, int end, String name)
            throws URISyntaxException {
        if (index >= end) {
            String nameString = (name == null) ? "" : " in [" + name + "]";
            throw new URISyntaxException(
                    uri, "Unexpected end of string" + nameString, index);
        }
        return uri.charAt(index);
    }

    /**
     * Decode a string according to the rules of this decoder.
     *
     * - if {@code convertPlus == true} all '+' chars in the decoded output are
     *   converted to ' ' (white space)
     * - if {@code throwOnFailure == true}, an {@link IllegalArgumentException} is
     *   thrown for invalid inputs. Else, U+FFFD is emitted to the output in place
     *   of invalid input octets.
     */
    public static String decode(
            String s, boolean convertPlus, Charset charset, boolean throwOnFailure) {
        StringBuilder builder = new StringBuilder(s.length());
        appendDecoded(builder, s, convertPlus, charset, throwOnFailure);
        return builder.toString();
    }

    /**
     * Character to be output when there's an error decoding an input.
     */
    private static final char INVALID_INPUT_CHARACTER = '\ufffd';

    private static void appendDecoded(
            StringBuilder builder,
            String s,
            boolean convertPlus,
            Charset charset,
            boolean throwOnFailure) {
        // REPLACE on malformed input is what turns a truncated multi-byte
        // sequence ("%C3") into U+FFFD instead of an exception.
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .replaceWith("\ufffd")
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        // Holds the bytes corresponding to the escaped chars being read (empty if
        // the last char wasn't an escaped char).
        ByteBuffer byteBuffer = ByteBuffer.allocate(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            i++;
            switch (c) {
                case '+':
                    flushDecodingByteAccumulator(
                            builder, decoder, byteBuffer, throwOnFailure);
                    builder.append(convertPlus ? ' ' : '+');
                    break;
                case '%':
                    // Expect two characters representing a number in hex.
                    byte hexValue = 0;
                    for (int j = 0; j < 2; j++) {
                        try {
                            c = getNextCharacter(s, i, s.length(), null /* name */);
                        } catch (URISyntaxException e) {
                            // Unexpected end of input.
                            if (throwOnFailure) {
                                throw new IllegalArgumentException(e);
                            } else {
                                flushDecodingByteAccumulator(
                                        builder, decoder, byteBuffer, throwOnFailure);
                                builder.append(INVALID_INPUT_CHARACTER);
                                return;
                            }
                        }
                        i++;
                        int newDigit = hexCharToValue(c);
                        if (newDigit < 0) {
                            if (throwOnFailure) {
                                throw new IllegalArgumentException(
                                        unexpectedCharacterException(s, null /* name */, c, i - 1));
                            } else {
                                flushDecodingByteAccumulator(
                                        builder, decoder, byteBuffer, throwOnFailure);
                                builder.append(INVALID_INPUT_CHARACTER);
                                break;
                            }
                        }
                        hexValue = (byte) (hexValue * 0x10 + newDigit);
                    }
                    // Reached both on the success path AND after the `break`
                    // above, which is why a bad hex digit still contributes a
                    // (partial) byte to the accumulator. AOSP does this; the
                    // emulator answers accordingly.
                    byteBuffer.put(hexValue);
                    break;
                default:
                    flushDecodingByteAccumulator(builder, decoder, byteBuffer, throwOnFailure);
                    builder.append(c);
            }
        }
        flushDecodingByteAccumulator(builder, decoder, byteBuffer, throwOnFailure);
    }

    private static void flushDecodingByteAccumulator(
            StringBuilder builder,
            CharsetDecoder decoder,
            ByteBuffer byteBuffer,
            boolean throwOnFailure) {
        if (byteBuffer.position() == 0) {
            return;
        }
        byteBuffer.flip();
        try {
            builder.append(decoder.decode(byteBuffer));
        } catch (CharacterCodingException e) {
            if (throwOnFailure) {
                throw new IllegalArgumentException(e);
            } else {
                builder.append(INVALID_INPUT_CHARACTER);
            }
        } finally {
            // Use the byte buffer to write again.
            byteBuffer.flip();
            byteBuffer.limit(byteBuffer.capacity());
        }
    }
}
