package org.apache.http.util;

import java.io.UnsupportedEncodingException;

/** Byte/String conversion helpers from Apache HttpClient 4.x, part of the
 *  {@code org.apache.http.legacy} optional shared library.
 *
 *  Upstream reads its two charset names from {@code protocol.HTTP}
 *  ({@code US_ASCII = "US-ASCII"}, {@code DEFAULT_CONTENT_CHARSET =
 *  "ISO-8859-1"}). That class is not in this tree, so the values are written
 *  out literally rather than adding a dependency on a class we do not ship.
 *
 *  The fall-back-to-platform-default behaviour on an unsupported charset is
 *  upstream's, not a convenience: callers rely on these never throwing
 *  UnsupportedEncodingException.
 */
public final class EncodingUtils {

    private static final String US_ASCII = "US-ASCII";

    public static String getString(final byte[] data, final int offset,
                                   final int length, final String charset) {
        if (data == null) {
            throw new IllegalArgumentException("Parameter may not be null");
        }
        if (charset == null || charset.length() == 0) {
            throw new IllegalArgumentException("charset may not be null or empty");
        }
        try {
            return new String(data, offset, length, charset);
        } catch (UnsupportedEncodingException e) {
            return new String(data, offset, length);
        }
    }

    public static String getString(final byte[] data, final String charset) {
        if (data == null) {
            throw new IllegalArgumentException("Parameter may not be null");
        }
        return getString(data, 0, data.length, charset);
    }

    public static byte[] getBytes(final String data, final String charset) {
        if (data == null) {
            throw new IllegalArgumentException("data may not be null");
        }
        if (charset == null || charset.length() == 0) {
            throw new IllegalArgumentException("charset may not be null or empty");
        }
        try {
            return data.getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            return data.getBytes();
        }
    }

    public static byte[] getAsciiBytes(final String data) {
        if (data == null) {
            throw new IllegalArgumentException("Parameter may not be null");
        }
        try {
            return data.getBytes(US_ASCII);
        } catch (UnsupportedEncodingException e) {
            throw new Error("HttpClient requires ASCII support");
        }
    }

    public static String getAsciiString(final byte[] data, final int offset,
                                        final int length) {
        if (data == null) {
            throw new IllegalArgumentException("Parameter may not be null");
        }
        try {
            return new String(data, offset, length, US_ASCII);
        } catch (UnsupportedEncodingException e) {
            throw new Error("HttpClient requires ASCII support");
        }
    }

    public static String getAsciiString(final byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Parameter may not be null");
        }
        return getAsciiString(data, 0, data.length);
    }

    private EncodingUtils() {}
}
