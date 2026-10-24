// org.json.JSONTokener -- a real recursive-descent JSON parser, mirroring
// AOSP libcore's org.json implementation (Apache-2.0).
//
// The previous version was a stub: nextValue() returned null, so
// `new JSONObject(jsonString)` and `new JSONArray(jsonString)` produced
// EMPTY containers -- silently, without throwing. That broke every app
// that parses a JSON config blob via org.json. The motivating case was
// Vector Pinball (com.dozingcatsoftware.bouncy): it loads each pinball
// table layout from assets/tables/tableN.json via
// `new JSONObject(utf8FromStream(...))`. The empty parse meant the field
// had zero elements, so the GL renderer cleared to black and drew
// nothing -- a black screen with NO error logged (the empty map looked
// like a valid-but-empty table).
//
// This implementation matches Android's lenient parse: it accepts
// //, /* */, and # comments, single- or double-quoted strings,
// unquoted literals, and ; or , separators -- the same surface AOSP's
// JSONTokener exposes -- so APK bytecode that targeted Android's org.json
// gets identical results here. Numbers parse to Integer/Long/Double the
// way AOSP does (decimal-or-exponent -> Double; otherwise the smallest
// of int/long that fits; hex 0x.. and octal 0.. honored).
package org.json;

public class JSONTokener {
    /** The input JSON, with a leading UTF-8 BOM (if present) stripped. */
    private final String in;
    /** The next character index to read. */
    private int pos;

    public JSONTokener(String in) {
        // Consume an optional byte order mark (BOM) at the start of input.
        if (in != null && in.startsWith("﻿")) {
            in = in.substring(1);
        }
        this.in = (in == null) ? "" : in;
    }

    /** Parse and return the next value: a JSONObject, JSONArray, String,
     *  Boolean, Integer, Long, Double, or JSONObject.NULL. */
    public Object nextValue() throws JSONException {
        int c = nextCleanInternal();
        switch (c) {
            case -1:
                throw syntaxError("End of input");
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '\'':
            case '"':
                return nextString((char) c);
            default:
                pos--;
                return readLiteral();
        }
    }

    /** Return the next non-whitespace, non-comment character, or -1 at EOF. */
    private int nextCleanInternal() throws JSONException {
        while (pos < in.length()) {
            int c = in.charAt(pos++);
            switch (c) {
                case '\t':
                case ' ':
                case '\n':
                case '\r':
                    continue;
                case '/':
                    if (pos == in.length()) {
                        return c;
                    }
                    char peek = in.charAt(pos);
                    switch (peek) {
                        case '*': {
                            // C-style block comment.
                            pos++;
                            int commentEnd = in.indexOf("*/", pos);
                            if (commentEnd == -1) {
                                throw syntaxError("Unterminated comment");
                            }
                            pos = commentEnd + 2;
                            continue;
                        }
                        case '/':
                            // C++-style line comment.
                            pos++;
                            skipToEndOfLine();
                            continue;
                        default:
                            return c;
                    }
                case '#':
                    // Shell-style line comment (Android leniency).
                    skipToEndOfLine();
                    continue;
                default:
                    return c;
            }
        }
        return -1;
    }

    private void skipToEndOfLine() {
        for (; pos < in.length(); pos++) {
            char c = in.charAt(pos);
            if (c == '\r' || c == '\n') {
                pos++;
                break;
            }
        }
    }

    /** Read a quoted string whose opening quote has already been consumed. */
    public String nextString(char quote) throws JSONException {
        StringBuilder builder = null;
        int start = pos;
        while (pos < in.length()) {
            int c = in.charAt(pos++);
            if (c == quote) {
                if (builder == null) {
                    return in.substring(start, pos - 1);
                }
                builder.append(in, start, pos - 1);
                return builder.toString();
            }
            if (c == '\\') {
                if (pos == in.length()) {
                    throw syntaxError("Unterminated escape sequence");
                }
                if (builder == null) {
                    builder = new StringBuilder();
                }
                builder.append(in, start, pos - 1);
                builder.append(readEscapeCharacter());
                start = pos;
            }
        }
        throw syntaxError("Unterminated string");
    }

    private char readEscapeCharacter() throws JSONException {
        char escaped = in.charAt(pos++);
        switch (escaped) {
            case 'u': {
                if (pos + 4 > in.length()) {
                    throw syntaxError("Unterminated escape sequence");
                }
                String hex = in.substring(pos, pos + 4);
                pos += 4;
                try {
                    return (char) Integer.parseInt(hex, 16);
                } catch (NumberFormatException nfe) {
                    throw syntaxError("Invalid escape sequence: " + hex);
                }
            }
            case 't': return '\t';
            case 'b': return '\b';
            case 'n': return '\n';
            case 'r': return '\r';
            case 'f': return '\f';
            case '\'':
            case '"':
            case '\\':
            default:
                return escaped;
        }
    }

    /** Read an unquoted literal: true / false / null, or a number, or a
     *  bare (unquoted) string. */
    private Object readLiteral() throws JSONException {
        String literal = nextToInternal("{}[]/\\:,=;# \t\f");
        if (literal.length() == 0) {
            throw syntaxError("Expected literal value");
        }
        if ("null".equalsIgnoreCase(literal)) {
            return JSONObject.NULL;
        }
        if ("true".equalsIgnoreCase(literal)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(literal)) {
            return Boolean.FALSE;
        }
        // Try to parse as a number. Integers without a fraction/exponent
        // prefer the narrowest of int/long; everything else is a double.
        if (literal.indexOf('.') == -1) {
            int base = 10;
            String number = literal;
            if (number.startsWith("0x") || number.startsWith("0X")) {
                number = number.substring(2);
                base = 16;
            } else if (number.startsWith("0") && number.length() > 1) {
                number = number.substring(1);
                base = 8;
            }
            try {
                long longValue = Long.parseLong(number, base);
                if (longValue <= Integer.MAX_VALUE && longValue >= Integer.MIN_VALUE) {
                    return Integer.valueOf((int) longValue);
                }
                return Long.valueOf(longValue);
            } catch (NumberFormatException e) {
                // Not a base-N integer; fall through to the double path.
            }
        }
        try {
            return Double.valueOf(literal);
        } catch (NumberFormatException ignored) {
            // Not a number at all; treat as a bare string (AOSP leniency).
        }
        return literal;
    }

    /** Return the substring from the cursor up to (not including) the next
     *  excluded character or newline. Advances the cursor past it. */
    private String nextToInternal(String excluded) {
        int start = pos;
        for (; pos < in.length(); pos++) {
            char c = in.charAt(pos);
            if (c == '\r' || c == '\n' || excluded.indexOf(c) != -1) {
                return in.substring(start, pos);
            }
        }
        return in.substring(start);
    }

    private JSONObject readObject() throws JSONException {
        JSONObject result = new JSONObject();
        // Peek to handle the empty object {}.
        int first = nextCleanInternal();
        if (first == '}') {
            return result;
        }
        if (first != -1) {
            pos--;
        }
        while (true) {
            Object name = nextValue();
            if (!(name instanceof String)) {
                if (name == null) {
                    throw syntaxError("Names cannot be null");
                }
                throw syntaxError("Names must be strings, but " + name
                        + " is of type " + name.getClass().getName());
            }
            // Expect ':' (or '=' / '=>' under Android leniency).
            int separator = nextCleanInternal();
            if (separator != ':' && separator != '=') {
                throw syntaxError("Expected ':' after " + name);
            }
            if (pos < in.length() && in.charAt(pos) == '>') {
                pos++;
            }
            result.put((String) name, nextValue());
            switch (nextCleanInternal()) {
                case '}':
                    return result;
                case ';':
                case ',':
                    continue;
                default:
                    throw syntaxError("Unterminated object");
            }
        }
    }

    private JSONArray readArray() throws JSONException {
        JSONArray result = new JSONArray();
        boolean hasTrailingSeparator = false;
        while (true) {
            switch (nextCleanInternal()) {
                case -1:
                    throw syntaxError("Unterminated array");
                case ']':
                    if (hasTrailingSeparator) {
                        result.put(JSONObject.NULL);
                    }
                    return result;
                case ',':
                case ';':
                    // A separator without a value means an implicit null.
                    result.put(JSONObject.NULL);
                    hasTrailingSeparator = true;
                    continue;
                default:
                    pos--;
            }
            result.put(nextValue());
            switch (nextCleanInternal()) {
                case ']':
                    return result;
                case ',':
                case ';':
                    hasTrailingSeparator = true;
                    continue;
                default:
                    throw syntaxError("Unterminated array");
            }
        }
    }

    public JSONException syntaxError(String message) {
        return new JSONException(message + this);
    }

    @Override public String toString() {
        return " at character " + pos + " of " + in;
    }

    // ---- Legacy helpers retained for source compatibility ----
    public char nextClean() throws JSONException {
        int c = nextCleanInternal();
        return c == -1 ? '\0' : (char) c;
    }
    public boolean more() {
        return pos < in.length();
    }
    /** Back up one character so the next read re-reads it. */
    public void back() {
        if (pos > 0) pos--;
    }
}
