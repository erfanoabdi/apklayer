// android.util.JsonWriter -- mirrors AOSP libcore/json
// (frameworks: android.util.JsonWriter, API 11+). Public surface per
// developer.android.com/reference/android/util/JsonWriter: ctor(Writer),
// setIndent, setLenient/isLenient, beginArray/endArray,
// beginObject/endObject, name, value(String/boolean/Boolean/double/
// long/Number), nullValue, flush, close.
//
// Why this exists (v1.0.345): Firebase's datatransport backend
// (com.google.android.datatransport.cct.CctTransportBackend.send)
// serializes every telemetry upload through android.util.JsonWriter.
// Missing, each upload thread died in ClassNotFoundException -- the
// same "background framework gap an exception-escalating crash
// reporter can turn fatal" class as the NetworkInfo.getSubtype hole
// fixed in v1.0.338 (Crossy's Crashlytics halted the process on it).
package android.util;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;

public class JsonWriter implements java.io.Closeable {
    // Scope states, modeled after AOSP's JsonScope.
    private static final int EMPTY_DOCUMENT    = 0;  // before any value
    private static final int NONEMPTY_DOCUMENT = 1;  // top-level value written
    private static final int EMPTY_ARRAY       = 2;  // [ with no values
    private static final int NONEMPTY_ARRAY    = 3;  // [ with at least one value
    private static final int EMPTY_OBJECT      = 4;  // { with no name/values
    private static final int DANGLING_NAME     = 5;  // name written, value pending
    private static final int NONEMPTY_OBJECT   = 6;  // { with at least one pair

    private final Writer out;
    private final Deque<Integer> stack = new ArrayDeque<>();
    private String indent;       // null = compact
    private String separator = ":";
    private boolean lenient;

    public JsonWriter(Writer out) {
        if (out == null) throw new NullPointerException("out == null");
        this.out = out;
        stack.push(EMPTY_DOCUMENT);
    }

    /** Empty string disables pretty printing (the default). */
    public final void setIndent(String indent) {
        if (indent == null || indent.isEmpty()) {
            this.indent = null;
            this.separator = ":";
        } else {
            this.indent = indent;
            this.separator = ": ";
        }
    }

    public final void setLenient(boolean lenient) { this.lenient = lenient; }
    public final boolean isLenient()              { return lenient; }

    public JsonWriter beginArray() throws IOException {
        return open(EMPTY_ARRAY, "[");
    }
    public JsonWriter endArray() throws IOException {
        return close(EMPTY_ARRAY, NONEMPTY_ARRAY, "]");
    }
    public JsonWriter beginObject() throws IOException {
        return open(EMPTY_OBJECT, "{");
    }
    public JsonWriter endObject() throws IOException {
        return close(EMPTY_OBJECT, NONEMPTY_OBJECT, "}");
    }

    private JsonWriter open(int empty, String openBracket) throws IOException {
        beforeValue(true);
        stack.push(empty);
        out.write(openBracket);
        return this;
    }

    private JsonWriter close(int empty, int nonempty, String closeBracket)
            throws IOException {
        int context = peek();
        if (context != nonempty && context != empty) {
            throw new IllegalStateException("Nesting problem: " + stack);
        }
        stack.pop();
        if (context == nonempty) newline();
        out.write(closeBracket);
        return this;
    }

    public JsonWriter name(String name) throws IOException {
        if (name == null) throw new NullPointerException("name == null");
        beforeName();
        string(name);
        return this;
    }

    public JsonWriter value(String value) throws IOException {
        if (value == null) return nullValue();
        beforeValue(false);
        string(value);
        return this;
    }

    public JsonWriter nullValue() throws IOException {
        beforeValue(false);
        out.write("null");
        return this;
    }

    public JsonWriter value(boolean value) throws IOException {
        beforeValue(false);
        out.write(value ? "true" : "false");
        return this;
    }

    public JsonWriter value(Boolean value) throws IOException {
        if (value == null) return nullValue();
        return value(value.booleanValue());
    }

    public JsonWriter value(double value) throws IOException {
        if (!lenient && (Double.isNaN(value) || Double.isInfinite(value))) {
            throw new IllegalArgumentException(
                "Numeric values must be finite, but was " + value);
        }
        beforeValue(false);
        out.write(Double.toString(value));
        return this;
    }

    public JsonWriter value(long value) throws IOException {
        beforeValue(false);
        out.write(Long.toString(value));
        return this;
    }

    public JsonWriter value(Number value) throws IOException {
        if (value == null) return nullValue();
        String string = value.toString();
        if (!lenient && (string.equals("-Infinity") || string.equals("Infinity")
                         || string.equals("NaN"))) {
            throw new IllegalArgumentException(
                "Numeric values must be finite, but was " + value);
        }
        beforeValue(false);
        out.write(string);
        return this;
    }

    public void flush() throws IOException { out.flush(); }

    /** Closes the underlying writer; throws if the document is
     *  incomplete (per AOSP). */
    public void close() throws IOException {
        out.close();
        if (peek() != NONEMPTY_DOCUMENT) {
            throw new IOException("Incomplete document");
        }
    }

    private int peek() {
        Integer top = stack.peek();
        if (top == null) throw new IllegalStateException("JsonWriter is closed");
        return top;
    }

    private void replaceTop(int topOfStack) {
        stack.pop();
        stack.push(topOfStack);
    }

    private void string(String value) throws IOException {
        out.write('"');
        for (int i = 0, n = value.length(); i < n; i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':  out.write("\\\""); break;
                case '\\': out.write("\\\\"); break;
                case '\t': out.write("\\t");  break;
                case '\b': out.write("\\b");  break;
                case '\n': out.write("\\n");  break;
                case '\r': out.write("\\r");  break;
                case '\f': out.write("\\f");  break;
                // Both supported by JavaScript source but not JSON-safe
                // inside string literals everywhere; AOSP escapes them.
                case ' ': out.write("\\u2028"); break;
                case ' ': out.write("\\u2029"); break;
                default:
                    if (c <= 0x1F) {
                        out.write(String.format("\\u%04x", (int) c));
                    } else {
                        out.write(c);
                    }
            }
        }
        out.write('"');
    }

    private void newline() throws IOException {
        if (indent == null) return;
        out.write("\n");
        for (int i = 1, size = stack.size(); i < size; i++) {
            out.write(indent);
        }
    }

    /** Before a name: require an object context, write the pair
     *  separator if needed. */
    private void beforeName() throws IOException {
        int context = peek();
        if (context == NONEMPTY_OBJECT) {
            out.write(',');
        } else if (context != EMPTY_OBJECT) {
            throw new IllegalStateException("Nesting problem: " + stack);
        }
        newline();
        replaceTop(DANGLING_NAME);
    }

    /** Before a value: adjust scope state, write commas/colons/
     *  newlines as the context demands. */
    private void beforeValue(boolean root) throws IOException {
        switch (peek()) {
            case EMPTY_DOCUMENT:  // first top-level value
                if (!lenient && !root) {
                    throw new IllegalStateException(
                        "JSON must start with an array or an object.");
                }
                replaceTop(NONEMPTY_DOCUMENT);
                break;
            case EMPTY_ARRAY:     // first element
                replaceTop(NONEMPTY_ARRAY);
                newline();
                break;
            case NONEMPTY_ARRAY:  // another element
                out.write(',');
                newline();
                break;
            case DANGLING_NAME:   // value for a name
                out.write(separator);
                replaceTop(NONEMPTY_OBJECT);
                break;
            case NONEMPTY_DOCUMENT:
                throw new IllegalStateException(
                    "JSON must have only one top-level value.");
            default:
                throw new IllegalStateException("Nesting problem: " + stack);
        }
    }
}
