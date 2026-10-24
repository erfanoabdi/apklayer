package org.json;
import java.util.ArrayList;
import java.util.List;
/** Builds JSON text incrementally. Mirrors org.json's JSONStringer contract
 *  (Apache Harmony implementation shipped by Android). */
public class JSONStringer {
    final StringBuilder out = new StringBuilder();
    enum Scope { EMPTY_ARRAY, NONEMPTY_ARRAY, EMPTY_OBJECT, DANGLING_KEY, NONEMPTY_OBJECT, NULL }
    private final List<Scope> stack = new ArrayList<>();
    private final String indent;
    public JSONStringer() { indent = null; }
    JSONStringer(int indentSpaces) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indentSpaces; i++) sb.append(' ');
        indent = sb.toString();
    }
    public JSONStringer array() throws JSONException { return open(Scope.EMPTY_ARRAY, "["); }
    public JSONStringer endArray() throws JSONException { return close(Scope.EMPTY_ARRAY, Scope.NONEMPTY_ARRAY, "]"); }
    public JSONStringer object() throws JSONException { return open(Scope.EMPTY_OBJECT, "{"); }
    public JSONStringer endObject() throws JSONException { return close(Scope.EMPTY_OBJECT, Scope.NONEMPTY_OBJECT, "}"); }
    JSONStringer open(Scope empty, String openBracket) throws JSONException {
        if (stack.isEmpty() && out.length() > 0) throw new JSONException("Nesting problem: multiple top-level roots");
        beforeValue(); stack.add(empty); out.append(openBracket); return this;
    }
    JSONStringer close(Scope empty, Scope nonempty, String closeBracket) throws JSONException {
        Scope context = peek();
        if (context != nonempty && context != empty) throw new JSONException("Nesting problem");
        stack.remove(stack.size() - 1);
        if (context == nonempty) newline();
        out.append(closeBracket); return this;
    }
    private Scope peek() throws JSONException {
        if (stack.isEmpty()) throw new JSONException("Nesting problem");
        return stack.get(stack.size() - 1);
    }
    private void replaceTop(Scope topOfStack) { stack.set(stack.size() - 1, topOfStack); }
    public JSONStringer value(Object value) throws JSONException {
        if (stack.isEmpty()) throw new JSONException("Nesting problem");
        if (value instanceof JSONArray) { ((JSONArray) value).writeTo(this); return this; }
        if (value instanceof JSONObject) { ((JSONObject) value).writeTo(this); return this; }
        beforeValue();
        if (value == null || value instanceof Boolean || value == JSONObject.NULL) { out.append(value); }
        else if (value instanceof Number) { out.append(JSONObject.numberToString((Number) value)); }
        else { string(value.toString()); }
        return this;
    }
    public JSONStringer value(boolean value) throws JSONException {
        if (stack.isEmpty()) throw new JSONException("Nesting problem");
        beforeValue(); out.append(value); return this;
    }
    public JSONStringer value(double value) throws JSONException {
        if (stack.isEmpty()) throw new JSONException("Nesting problem");
        beforeValue(); out.append(JSONObject.numberToString(value)); return this;
    }
    public JSONStringer value(long value) throws JSONException {
        if (stack.isEmpty()) throw new JSONException("Nesting problem");
        beforeValue(); out.append(value); return this;
    }
    private void string(String value) {
        out.append('"');
        for (int i = 0, length = value.length(); i < length; i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': case '\\': case '/': out.append('\\').append(c); break;
                case '\t': out.append("\\t"); break;
                case '\b': out.append("\\b"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\f': out.append("\\f"); break;
                default:
                    if (c <= 0x1F) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        out.append('"');
    }
    private void newline() {
        if (indent == null) return;
        out.append('\n');
        for (int i = 0; i < stack.size(); i++) out.append(indent);
    }
    public JSONStringer key(String name) throws JSONException {
        if (name == null) throw new JSONException("Names must be non-null");
        beforeKey(); string(name); return this;
    }
    private void beforeKey() throws JSONException {
        Scope context = peek();
        if (context == Scope.NONEMPTY_OBJECT) out.append(',');
        else if (context != Scope.EMPTY_OBJECT) throw new JSONException("Nesting problem");
        newline(); replaceTop(Scope.DANGLING_KEY);
    }
    private void beforeValue() throws JSONException {
        if (stack.isEmpty()) return;
        Scope context = peek();
        if (context == Scope.EMPTY_ARRAY) { replaceTop(Scope.NONEMPTY_ARRAY); newline(); }
        else if (context == Scope.NONEMPTY_ARRAY) { out.append(','); newline(); }
        else if (context == Scope.DANGLING_KEY) { out.append(indent == null ? ":" : ": "); replaceTop(Scope.NONEMPTY_OBJECT); }
        else if (context != Scope.NULL) throw new JSONException("Nesting problem");
    }
    @Override public String toString() { return out.length() == 0 ? null : out.toString(); }
}
