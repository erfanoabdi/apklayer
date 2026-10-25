// com.android.org.kxml2.io.KXmlSerializer -- the concrete XmlSerializer that
// Android's android.util.Xml.newSerializer() hands back. The class lives at
// this exact package/name because that is where the platform puts it: stack
// traces, getClass().getName() checks and any (rare) instanceof against the
// platform serializer then read the same as on a device.
//
// This is an independent implementation of the documented XmlSerializer
// contract, written to reproduce the platform serializer's OBSERVABLE
// behavior rather than its internals. The behaviors that callers actually
// depend on, and that are therefore load-bearing here:
//
//   - startTag leaves the tag "pending" so attribute() can still append to it
//     and an element with no children closes as "<a />" rather than "<a></a>".
//   - attribute() outside a pending start tag is an IllegalStateException
//     ("illegal position for attribute"), not silently dropped output.
//   - endTag validates the namespace AND the name against the open element and
//     throws IllegalArgumentException on a mismatch, so a serializer bug
//     surfaces at the mismatched call instead of producing malformed XML.
//   - endDocument auto-closes every still-open tag, then flushes.
//   - Escaping: '&', '<' and '>' always become entities; the active quote
//     character is escaped inside attribute values; tab/CR/LF are escaped
//     numerically inside attribute values but written literally in text
//     (a raw CR in an attribute would be normalized away by any reader).
//   - Characters that XML 1.0 forbids outright are rejected with
//     IllegalArgumentException rather than written, because the alternative is
//     emitting a document no reader can parse. Surrogate pairs are written as
//     a single numeric character reference for the same reason.
//   - Non-ASCII is written as numeric character references unless the output
//     encoding is a UTF flavor, which is the only case where the bytes are
//     guaranteed to survive.
//
// Source of the contract: libcore/xml/src/main/java/org/xmlpull/v1/
// XmlSerializer.java and the platform's KXmlSerializer.
package com.android.org.kxml2.io;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Locale;

import org.xmlpull.v1.XmlSerializer;

public class KXmlSerializer implements XmlSerializer {

    private static final String FEATURE_INDENT_OUTPUT =
        "http://xmlpull.org/v1/doc/features.html#indent-output";
    private static final String XML_NAMESPACE =
        "http://www.w3.org/XML/1998/namespace";

    private Writer writer;

    /** True between startTag and the point where the tag has to be closed off
     *  (by '>' or ' />'). Attributes and namespace declarations may only be
     *  appended while this holds. */
    private boolean pending;
    /** Counter behind the generated "n0", "n1", ... prefixes. */
    private int auto;
    private int depth;

    /** Flat (namespace, prefix, name) triples, one per open element. */
    private String[] elementStack = new String[12];
    /** Number of namespace bindings visible at each depth. Index depth+1 is
     *  the "being built" scope that startTag/setPrefix append into. */
    private int[] nspCounts = new int[8];
    /** Flat (prefix, namespace) pairs. */
    private String[] nspStack = new String[16];
    private boolean[] indent = new boolean[8];

    private boolean unicode;
    private String encoding;

    // ---- output helpers ----------------------------------------------

    private void append(char c) throws IOException { writer.write(c); }
    private void append(String s) throws IOException { writer.write(s); }
    private void appendSpace(int n) throws IOException {
        for (int i = 0; i < n; i++) writer.write(' ');
    }

    private void ensureIndent(int d) {
        if (indent.length <= d) {
            boolean[] grown = new boolean[d + 8];
            System.arraycopy(indent, 0, grown, 0, indent.length);
            indent = grown;
        }
    }

    private void ensureNspCounts(int d) {
        if (nspCounts.length <= d) {
            int[] grown = new int[d + 8];
            System.arraycopy(nspCounts, 0, grown, 0, nspCounts.length);
            nspCounts = grown;
        }
    }

    private void ensureNspStack(int slots) {
        if (nspStack.length < slots) {
            String[] grown = new String[Math.max(slots, nspStack.length + 16)];
            System.arraycopy(nspStack, 0, grown, 0, nspStack.length);
            nspStack = grown;
        }
    }

    private void ensureElementStack(int slots) {
        if (elementStack.length < slots) {
            String[] grown = new String[Math.max(slots, elementStack.length + 12)];
            System.arraycopy(elementStack, 0, grown, 0, elementStack.length);
            elementStack = grown;
        }
    }

    /** Close off a pending start tag. `close` selects the self-closing form,
     *  which is what endTag uses for a childless element. Also the point where
     *  the namespace declarations collected since startTag get written. */
    private void check(boolean close) throws IOException {
        if (!pending) return;

        depth++;
        pending = false;

        ensureIndent(depth + 1);
        indent[depth] = indent[depth - 1];

        ensureNspCounts(depth + 2);
        for (int i = nspCounts[depth - 1]; i < nspCounts[depth]; i++) {
            append(" xmlns");
            if (!nspStack[i * 2].isEmpty()) {
                append(':');
                append(nspStack[i * 2]);
            } else if (getNamespace() != null && getNamespace().isEmpty()
                       && !nspStack[i * 2 + 1].isEmpty()) {
                throw new IllegalStateException(
                    "Cannot set default namespace for elements in no namespace");
            }
            append("=\"");
            writeEscaped(nspStack[i * 2 + 1], '"');
            append('"');
        }
        nspCounts[depth + 1] = nspCounts[depth];

        append(close ? " />" : ">");
    }

    /** quot == -1 means "character data", where tab/CR/LF stay literal;
     *  otherwise quot is the quote character delimiting an attribute value. */
    private void writeEscaped(String s, int quot) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\n':
                case '\r':
                case '\t':
                    if (quot == -1) append(c);
                    else append("&#" + ((int) c) + ";");
                    break;
                case '&': append("&amp;"); break;
                case '>': append("&gt;");  break;
                case '<': append("&lt;");  break;
                default:
                    if (c == quot) {
                        append(c == '"' ? "&quot;" : "&apos;");
                        break;
                    }
                    // XML 1.0 legal character range (www.w3.org/TR/REC-xml/#charsets);
                    // tab/CR/LF were handled above.
                    boolean allowedInXml = (c >= 0x20 && c <= 0xd7ff)
                                        || (c >= 0xe000 && c <= 0xfffd);
                    if (allowedInXml) {
                        if (unicode || c < 127) append(c);
                        else append("&#" + ((int) c) + ";");
                    } else if (Character.isHighSurrogate(c) && i < s.length() - 1) {
                        writeSurrogate(c, s.charAt(i + 1));
                        ++i;
                    } else {
                        reportInvalidCharacter(c);
                    }
            }
        }
    }

    /** A Java surrogate PAIR is not a legal XML character on its own, so it is
     *  written as the single code point it encodes. */
    private void writeSurrogate(char high, char low) throws IOException {
        if (!Character.isLowSurrogate(low)) {
            throw new IllegalArgumentException("Bad surrogate pair (U+"
                + Integer.toHexString((int) high) + " U+"
                + Integer.toHexString((int) low) + ")");
        }
        append("&#" + Character.toCodePoint(high, low) + ";");
    }

    private static void reportInvalidCharacter(char ch) {
        throw new IllegalArgumentException(
            "Illegal character (U+" + Integer.toHexString((int) ch) + ")");
    }

    // ---- output binding ----------------------------------------------

    @Override public void setOutput(Writer writer) {
        this.writer = writer;
        ensureNspCounts(4);
        ensureNspStack(4);
        // Two bindings are always in scope: the empty prefix bound to no
        // namespace, and the reserved "xml" prefix.
        nspCounts[0] = 2;
        nspCounts[1] = 2;
        nspStack[0] = "";
        nspStack[1] = "";
        nspStack[2] = "xml";
        nspStack[3] = XML_NAMESPACE;
        pending = false;
        auto = 0;
        depth = 0;
        unicode = false;
    }

    @Override public void setOutput(OutputStream os, String encoding) throws IOException {
        if (os == null) throw new IllegalArgumentException("os == null");
        setOutput(encoding == null
            ? new OutputStreamWriter(os)
            : new OutputStreamWriter(os, encoding));
        this.encoding = encoding;
        if (encoding != null && encoding.toLowerCase(Locale.US).startsWith("utf")) {
            unicode = true;
        }
    }

    // ---- document / element structure --------------------------------

    @Override public void startDocument(String encoding, Boolean standalone) throws IOException {
        append("<?xml version='1.0' ");
        if (encoding != null) {
            this.encoding = encoding;
            if (encoding.toLowerCase(Locale.US).startsWith("utf")) unicode = true;
        }
        if (this.encoding != null) {
            append("encoding='");
            append(this.encoding);
            append("' ");
        }
        if (standalone != null) {
            append("standalone='");
            append(standalone.booleanValue() ? "yes" : "no");
            append("' ");
        }
        append("?>");
    }

    @Override public void endDocument() throws IOException {
        while (depth > 0) {
            endTag(elementStack[depth * 3 - 3], elementStack[depth * 3 - 1]);
        }
        flush();
    }

    @Override public XmlSerializer startTag(String namespace, String name) throws IOException {
        check(false);

        ensureIndent(depth + 1);
        if (indent[depth]) {
            append('\r');
            append('\n');
            appendSpace(2 * depth);
        }

        ensureElementStack(depth * 3 + 3);
        ensureNspCounts(depth + 2);

        String prefix = (namespace == null) ? "" : getPrefix(namespace, true, true);

        // Writing an element in NO namespace while a default namespace is bound
        // would silently put the element in that namespace instead.
        if (namespace != null && namespace.isEmpty()) {
            for (int i = nspCounts[depth]; i < nspCounts[depth + 1]; i++) {
                if (nspStack[i * 2].isEmpty() && !nspStack[i * 2 + 1].isEmpty()) {
                    throw new IllegalStateException(
                        "Cannot set default namespace for elements in no namespace");
                }
            }
        }

        int esp = depth * 3;
        elementStack[esp++] = namespace;
        elementStack[esp++] = prefix;
        elementStack[esp] = name;

        append('<');
        if (!prefix.isEmpty()) {
            append(prefix);
            append(':');
        }
        append(name);

        pending = true;
        return this;
    }

    @Override public XmlSerializer attribute(String namespace, String name, String value)
            throws IOException {
        if (!pending) throw new IllegalStateException("illegal position for attribute");
        if (namespace == null) namespace = "";

        String prefix = namespace.isEmpty() ? "" : getPrefix(namespace, false, true);

        append(' ');
        if (!prefix.isEmpty()) {
            append(prefix);
            append(':');
        }
        append(name);
        append('=');
        // Prefer double quotes; fall back to single quotes when the value has a
        // double quote in it so the common case needs no escaping.
        char q = value.indexOf('"') == -1 ? '"' : '\'';
        append(q);
        writeEscaped(value, q);
        append(q);
        return this;
    }

    @Override public XmlSerializer endTag(String namespace, String name) throws IOException {
        if (!pending) depth--;

        if ((namespace == null && elementStack[depth * 3] != null)
            || (namespace != null && !namespace.equals(elementStack[depth * 3]))
            || !elementStack[depth * 3 + 2].equals(name)) {
            throw new IllegalArgumentException(
                "</{" + namespace + "}" + name + "> does not match start");
        }

        if (pending) {
            // No children were written, so collapse to the self-closing form.
            check(true);
            depth--;
        } else {
            ensureIndent(depth + 2);
            if (indent[depth + 1]) {
                append('\r');
                append('\n');
                appendSpace(2 * depth);
            }
            append("</");
            String prefix = elementStack[depth * 3 + 1];
            if (!prefix.isEmpty()) {
                append(prefix);
                append(':');
            }
            append(name);
            append('>');
        }

        ensureNspCounts(depth + 2);
        nspCounts[depth + 1] = nspCounts[depth];
        return this;
    }

    // ---- content ------------------------------------------------------

    @Override public XmlSerializer text(String text) throws IOException {
        check(false);
        ensureIndent(depth + 1);
        // Mixed content must not be re-indented or the whitespace would become
        // part of the value.
        indent[depth] = false;
        writeEscaped(text, -1);
        return this;
    }

    @Override public XmlSerializer text(char[] buf, int start, int len) throws IOException {
        text(new String(buf, start, len));
        return this;
    }

    @Override public void cdsect(String data) throws IOException {
        check(false);
        // "]]>" cannot appear inside a CDATA section, so split and reopen.
        data = data.replace("]]>", "]]]]><![CDATA[>");
        append("<![CDATA[");
        for (int i = 0; i < data.length(); ++i) {
            char ch = data.charAt(i);
            boolean allowedInCdata = (ch >= 0x20 && ch <= 0xd7ff)
                                  || (ch == '\t' || ch == '\n' || ch == '\r')
                                  || (ch >= 0xe000 && ch <= 0xfffd);
            if (allowedInCdata) {
                append(ch);
            } else if (Character.isHighSurrogate(ch) && i < data.length() - 1) {
                // Character references are not expanded inside CDATA, so the
                // section has to be closed around the surrogate.
                append("]]>");
                writeSurrogate(ch, data.charAt(++i));
                append("<![CDATA[");
            } else {
                reportInvalidCharacter(ch);
            }
        }
        append("]]>");
    }

    @Override public void entityRef(String name) throws IOException {
        check(false);
        append('&');
        append(name);
        append(';');
    }

    @Override public void comment(String comment) throws IOException {
        check(false);
        append("<!--");
        append(comment);
        append("-->");
    }

    @Override public void processingInstruction(String pi) throws IOException {
        check(false);
        append("<?");
        append(pi);
        append("?>");
    }

    @Override public void docdecl(String dd) throws IOException {
        append("<!DOCTYPE");
        append(dd);
        append('>');
    }

    @Override public void ignorableWhitespace(String s) throws IOException {
        text(s);
    }

    @Override public void flush() throws IOException {
        check(false);
        if (writer != null) writer.flush();
    }

    // ---- namespaces ---------------------------------------------------

    @Override public void setPrefix(String prefix, String namespace) throws IOException {
        check(false);
        if (prefix == null) prefix = "";
        if (namespace == null) namespace = "";

        String defined = getPrefix(namespace, true, false);
        if (prefix.equals(defined)) return;   // already bound, nothing to emit

        ensureNspCounts(depth + 2);
        int pos = (nspCounts[depth + 1]++) << 1;
        ensureNspStack(pos + 2);
        nspStack[pos++] = prefix;
        nspStack[pos] = namespace;
    }

    @Override public String getPrefix(String namespace, boolean generatePrefix) {
        try {
            return getPrefix(namespace, false, generatePrefix);
        } catch (IOException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /** Find the prefix currently bound to `namespace`, optionally inventing one
     *  ("n0", "n1", ...) and binding it for the element being built. A binding
     *  only counts if it has not been shadowed by a later one. */
    private String getPrefix(String namespace, boolean includeDefault, boolean create)
            throws IOException {
        ensureNspCounts(depth + 2);
        for (int i = nspCounts[depth + 1] * 2 - 2; i >= 0; i -= 2) {
            if (nspStack[i + 1].equals(namespace)
                    && (includeDefault || !nspStack[i].isEmpty())) {
                String cand = nspStack[i];
                for (int j = i + 2; j < nspCounts[depth + 1] * 2; j++) {
                    if (nspStack[j].equals(cand)) { cand = null; break; }
                }
                if (cand != null) return cand;
            }
        }

        if (!create) return null;

        String prefix;
        if (namespace.isEmpty()) {
            prefix = "";
        } else {
            do {
                prefix = "n" + (auto++);
                for (int i = nspCounts[depth + 1] * 2 - 2; i >= 0; i -= 2) {
                    if (prefix.equals(nspStack[i])) { prefix = null; break; }
                }
            } while (prefix == null);
        }

        // setPrefix would otherwise close the tag we are in the middle of.
        boolean p = pending;
        pending = false;
        setPrefix(prefix, namespace);
        pending = p;
        return prefix;
    }

    @Override public String getNamespace() {
        return getDepth() == 0 ? null : elementStack[getDepth() * 3 - 3];
    }

    @Override public String getName() {
        return getDepth() == 0 ? null : elementStack[getDepth() * 3 - 1];
    }

    /** A pending start tag already counts as one level deep. */
    @Override public int getDepth() {
        return pending ? depth + 1 : depth;
    }

    // ---- features / properties ---------------------------------------

    @Override public boolean getFeature(String name) {
        if (FEATURE_INDENT_OUTPUT.equals(name)) {
            ensureIndent(depth + 1);
            return indent[depth];
        }
        return false;
    }

    @Override public void setFeature(String name, boolean value) {
        if (FEATURE_INDENT_OUTPUT.equals(name)) {
            ensureIndent(depth + 1);
            indent[depth] = value;
        } else {
            throw new RuntimeException("Unsupported Feature");
        }
    }

    @Override public Object getProperty(String name) {
        throw new RuntimeException("Unsupported property");
    }

    @Override public void setProperty(String name, Object value) {
        throw new RuntimeException("Unsupported Property:" + value);
    }
}
