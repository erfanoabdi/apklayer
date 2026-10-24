// com.macapk.loader.axml.AxmlParser -- the XmlResourceParser over a compiled
// XML document (an AxmlBlock): AOSP's XmlBlock.Parser, event for event.
//
// SOURCE OF TRUTH: frameworks/base core/java/android/content/res/XmlBlock.java
// (aosp main, class Parser) and the natives it calls in core/jni/
// android_util_XmlBlock.cpp over libs/androidfw ResXMLParser. Mirrored here:
//   - next(): the FIRST call returns START_DOCUMENT (mStarted), and
//     getEventType() already answers START_DOCUMENT before it; namespace
//     chunks are never reported; depth rises on START_TAG and falls on the
//     event AFTER an END_TAG (mDecNextDepth).
//   - getAttributeNameResource(i): the resource-map id of the attribute's
//     name string (ResXMLParser::getAttributeNameResID), 0 when unmapped.
//   - getAttributeName(i) / getAttributeNamespace(i) / getAttributeValue(i):
//     IndexOutOfBoundsException for an index that is not an attribute of the
//     current START_TAG; getAttributeNamespace answers "" for no namespace.
//   - getAttributeResourceValue: data only for TYPE_REFERENCE; getAttributeIntValue
//     / getAttributeUnsignedIntValue / getAttributeBooleanValue: data only for
//     TYPE_FIRST_INT..TYPE_LAST_INT; getAttributeFloatValue: TYPE_FLOAT or
//     RuntimeException("not a float!"); getAttributeListValue: a TYPE_STRING is
//     matched against the options (XmlUtils.convertValueToList), anything else
//     answers its data. "don't attempt to convert any other types, because we
//     want to count on aapt doing the conversion for us."
//   - the (namespace, name) lookups match EXACTLY as ResXMLParser::
//     indexOfAttribute does: a null namespace matches only an attribute with no
//     namespace, a non-null one only that URI.
//   - getIdAttribute / getClassAttribute: the RAW string of the idIndex /
//     classIndex attribute (null for a compiled reference, which has none);
//     getStyleAttribute: the data of the styleIndex attribute when it is a
//     TYPE_REFERENCE or TYPE_ATTRIBUTE, else 0 (nativeGetStyleAttribute).
//   - getPositionDescription "Binary XML file line #N"; getColumnNumber -1;
//     isWhitespace false ("whitespace was stripped by aapt"); getPrefix /
//     getAttributePrefix / getNamespace(String) throw RuntimeException and the
//     namespace-stack queries throw XmlPullParserException, as on a device.
//
// ONE DELIBERATE DIVERGENCE, in getAttributeValue(int) for a TYPED value with no
// raw string. AOSP answers TypedValue.coerceToString(type, data) ("@17104896",
// "?16843601", "-1"), because on a device nothing reads that string: TypedArray
// takes the (type, data) pair straight from the block. Mac.apk's TypedArray and
// LayoutInflater resolve by TEXT, and every one of them was verified on the
// decoded-source spelling apktool writes ("@android:dimen/x",
// "?android:textAppearanceListItemSmall", "match_parent", "16.0dp") -- which is
// exactly what ResourceIndex already serves for every APP layout
// (BinaryXmlParser.parse(bytes, names) + toText, v1.0.1590). So a typed value is
// rendered in THAT spelling here (spell(), mirroring BinaryXmlParser.setAttr
// value for value; debug/platlayout_gate.sh holds the two identical over every
// layout in framework-res). The AOSP string stays one call away as
// macapkCoerceToString(i), and every typed getter above is AOSP-exact, so code
// that reads the typed surface sees what a device shows it.
package com.macapk.loader.axml;

import android.content.res.XmlResourceParser;
import com.macapk.loader.BinaryXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.InputStream;
import java.io.Reader;

public final class AxmlParser implements XmlResourceParser {
    // Res_value dataType (ResourceTypes.h), which android.util.TypedValue shares.
    private static final int TYPE_NULL = 0x00, TYPE_REFERENCE = 0x01, TYPE_ATTRIBUTE = 0x02,
        TYPE_STRING = 0x03, TYPE_FLOAT = 0x04, TYPE_DIMENSION = 0x05, TYPE_FRACTION = 0x06,
        TYPE_INT_DEC = 0x10, TYPE_INT_HEX = 0x11, TYPE_INT_BOOLEAN = 0x12,
        TYPE_FIRST_INT = 0x10, TYPE_LAST_INT = 0x1f, TYPE_FIRST_COLOR = 0x1c, TYPE_LAST_COLOR = 0x1f;

    private final AxmlBlock b;
    private final int sourceResId;
    private final BinaryXmlParser.Names names;

    // XmlBlock.Parser's state fields, same names and initial values.
    private boolean mStarted = false;
    private boolean mDecNextDepth = false;
    private int mDepth = 0;
    private int mEventType = START_DOCUMENT;
    /** Index of the current event in the block, -1 before the first real one. */
    private int cur = -1;
    private boolean closed = false;

    /** @param names the id/enum names typed values are spelled with (see the
     *  header); null spells them in the raw form BinaryXmlParser.parse(bytes)
     *  uses ("@0x01020014", "0x10"). */
    public AxmlParser(AxmlBlock block, int sourceResId, BinaryXmlParser.Names names) {
        if (block == null) throw new NullPointerException("block");
        this.b = block;
        this.sourceResId = sourceResId;
        this.names = names;
    }

    /** XmlBlock.Parser.getSourceResId: the id this parser was opened for. */
    public int getSourceResId() { return sourceResId; }

    // ------------------------------------------------------------ events
    @Override public int next() throws XmlPullParserException {
        if (!mStarted) {
            mStarted = true;
            return START_DOCUMENT;
        }
        // A closed parser (XmlBlock.Parser: mParseState == 0) answers
        // END_DOCUMENT without moving; it has closed itself on reaching it.
        if (closed) return END_DOCUMENT;
        cur++;
        final int ev = cur < b.eventCount() ? b.evType[cur] : END_DOCUMENT;
        if (mDecNextDepth) {
            mDepth--;
            mDecNextDepth = false;
        }
        switch (ev) {
            case START_TAG: mDepth++; break;
            case END_TAG:   mDecNextDepth = true; break;
            default: break;
        }
        mEventType = ev;
        if (ev == END_DOCUMENT) close();   // XmlBlock.Parser closes itself here too
        return ev;
    }
    @Override public int nextToken() throws XmlPullParserException { return next(); }
    @Override public int getEventType() { return mEventType; }
    @Override public int getDepth() { return mDepth; }

    @Override public void require(int type, String namespace, String name) throws XmlPullParserException {
        if (type != getEventType()
            || (namespace != null && !namespace.equals(getNamespace()))
            || (name != null && !name.equals(getName()))) {
            throw new XmlPullParserException("expected " + TYPES[type] + getPositionDescription());
        }
    }
    @Override public String nextText() throws XmlPullParserException {
        if (getEventType() != START_TAG) {
            throw new XmlPullParserException(getPositionDescription()
                + ": parser must be on START_TAG to read next text", this, null);
        }
        int eventType = next();
        if (eventType == TEXT) {
            String result = getText();
            eventType = next();
            if (eventType != END_TAG) {
                throw new XmlPullParserException(getPositionDescription()
                    + ": event TEXT it must be immediately followed by END_TAG", this, null);
            }
            return result;
        } else if (eventType == END_TAG) {
            return "";
        }
        throw new XmlPullParserException(getPositionDescription()
            + ": parser must be on START_TAG or TEXT to read text", this, null);
    }
    @Override public int nextTag() throws XmlPullParserException {
        int eventType = next();
        if (eventType == TEXT && isWhitespace()) eventType = next();
        if (eventType != START_TAG && eventType != END_TAG) {
            throw new XmlPullParserException(getPositionDescription()
                + ": expected start or end tag", this, null);
        }
        return eventType;
    }

    private boolean onEvent() { return cur >= 0 && cur < b.eventCount() && mEventType != END_DOCUMENT; }
    private boolean onStartTag() { return onEvent() && b.evType[cur] == START_TAG; }

    @Override public int getLineNumber() { return onEvent() ? b.evLine[cur] : -1; }
    @Override public int getColumnNumber() { return -1; }
    @Override public String getPositionDescription() { return "Binary XML file line #" + getLineNumber(); }
    @Override public boolean isWhitespace() { return false; }   // "whitespace was stripped by aapt."
    @Override public boolean isEmptyElementTag() { return false; }

    @Override public String getText() {
        if (!onEvent() || b.evType[cur] != TEXT) return null;
        return b.str(b.evText[cur]);
    }
    @Override public char[] getTextCharacters(int[] holderForStartAndLength) {
        String txt = getText();
        if (txt == null) return null;
        holderForStartAndLength[0] = 0;
        holderForStartAndLength[1] = txt.length();
        char[] chars = new char[txt.length()];
        txt.getChars(0, txt.length(), chars, 0);
        return chars;
    }
    /** The element namespace ("" when it has none, or on a non-tag event). */
    @Override public String getNamespace() {
        if (!onEvent() || b.evType[cur] == TEXT) return "";
        String s = b.str(b.evNs[cur]);
        return s != null ? s : "";
    }
    @Override public String getName() {
        if (!onEvent() || b.evType[cur] == TEXT) return null;
        return b.str(b.evName[cur]);
    }

    // ------------------------------------------------------ unsupported surface
    @Override public String getPrefix() { throw new RuntimeException("getPrefix not supported"); }
    @Override public String getAttributePrefix(int index) {
        throw new RuntimeException("getAttributePrefix not supported");
    }
    @Override public String getNamespace(String prefix) {
        throw new RuntimeException("getNamespace() not supported");
    }
    @Override public int getNamespaceCount(int depth) throws XmlPullParserException {
        throw new XmlPullParserException("getNamespaceCount() not supported");
    }
    @Override public String getNamespacePrefix(int pos) throws XmlPullParserException {
        throw new XmlPullParserException("getNamespacePrefix() not supported");
    }
    @Override public String getNamespaceUri(int pos) throws XmlPullParserException {
        throw new XmlPullParserException("getNamespaceUri() not supported");
    }
    @Override public void setFeature(String name, boolean state) throws XmlPullParserException {
        if (FEATURE_PROCESS_NAMESPACES.equals(name) && state) return;
        if (FEATURE_REPORT_NAMESPACE_ATTRIBUTES.equals(name) && state) return;
        throw new XmlPullParserException("Unsupported feature: " + name);
    }
    @Override public boolean getFeature(String name) {
        return FEATURE_PROCESS_NAMESPACES.equals(name)
            || FEATURE_REPORT_NAMESPACE_ATTRIBUTES.equals(name);
    }
    @Override public void setProperty(String name, Object value) throws XmlPullParserException {
        throw new XmlPullParserException("setProperty() not supported");
    }
    @Override public Object getProperty(String name) { return null; }
    @Override public void setInput(Reader in) throws XmlPullParserException {
        throw new XmlPullParserException("setInput() not supported");
    }
    @Override public void setInput(InputStream inputStream, String inputEncoding) throws XmlPullParserException {
        throw new XmlPullParserException("setInput() not supported");
    }
    @Override public void defineEntityReplacementText(String entityName, String replacementText)
            throws XmlPullParserException {
        throw new XmlPullParserException("defineEntityReplacementText() not supported");
    }
    @Override public String getInputEncoding() { return null; }
    @Override public void close() { closed = true; }

    // ------------------------------------------------------------ attributes
    /** Global attribute row of index `i` on the current START_TAG, or -1. */
    private int row(int i) {
        if (!onStartTag() || i < 0 || i >= b.evAttrCount[cur]) return -1;
        return b.evAttrFirst[cur] + i;
    }
    private int typeOf(int i) { int r = row(i); return r < 0 ? TYPE_NULL : b.atType[r]; }
    private int dataOf(int i) { int r = row(i); return r < 0 ? 0 : b.atData[r]; }

    @Override public int getAttributeCount() { return onStartTag() ? b.evAttrCount[cur] : -1; }
    /** XmlBlock.Parser.getAttributeNamespace: the URI, "" for an attribute
     *  with no namespace (ns.index == -1), and IndexOutOfBoundsException for an
     *  index that is not an attribute of the current START_TAG (or when the
     *  parser is not on one) -- ResXMLParser::getAttributeNamespaceID answers
     *  -2 there, and the Java side throws for anything that is neither >= 0
     *  nor -1 (XmlBlock.java / libs/androidfw ResourceTypes.cpp, aosp main). */
    @Override public String getAttributeNamespace(int index) {
        int r = row(index);
        if (r < 0) throw new IndexOutOfBoundsException(String.valueOf(index));
        String s = b.str(b.atNs[r]);
        return s != null ? s : "";
    }
    @Override public String getAttributeName(int index) {
        int r = row(index);
        String s = r < 0 ? null : b.str(b.atName[r]);
        if (s == null) throw new IndexOutOfBoundsException(String.valueOf(index));
        return s;
    }
    @Override public int getAttributeNameResource(int index) {
        int r = row(index);
        if (r < 0) return 0;
        int n = b.atName[r];
        return (n >= 0 && n < b.resIds.length) ? b.resIds[n] : 0;
    }
    @Override public String getAttributeType(int index) { return "CDATA"; }
    @Override public boolean isAttributeDefault(int index) { return false; }

    /** See the header: the raw string when the document kept one (AOSP),
     *  otherwise the decoded-source spelling of the typed value. */
    @Override public String getAttributeValue(int index) {
        int r = row(index);
        if (r < 0) throw new IndexOutOfBoundsException(String.valueOf(index));
        String raw = b.str(b.atRaw[r]);
        if (raw != null) return raw;
        return spell(r);
    }

    /** What AOSP's XmlBlock.Parser.getAttributeValue(int) answers: the raw
     *  string when present, else TypedValue.coerceToString(type, data), and an
     *  IndexOutOfBoundsException for a TYPE_NULL value with no string. That
     *  includes a TYPE_STRING value with no raw string: coerceToString has no
     *  case for it and answers null, and so does this (aapt always keeps the
     *  raw string of a string value, so it does not arise in practice). */
    public String macapkCoerceToString(int index) {
        int r = row(index);
        String raw = r < 0 ? null : b.str(b.atRaw[r]);
        if (raw != null) return raw;
        int t = r < 0 ? TYPE_NULL : b.atType[r];
        if (t == TYPE_NULL) throw new IndexOutOfBoundsException(String.valueOf(index));
        return android.util.TypedValue.coerceToString(t, b.atData[r]);
    }

    /** The raw Res_value of attribute `index`: {dataType, data}. Mac.apk
     *  accessor, used by the gate to hold the typed surface to AOSP. */
    public int[] macapkTypedValue(int index) {
        int r = row(index);
        return r < 0 ? new int[] { TYPE_NULL, 0 } : new int[] { b.atType[r], b.atData[r] };
    }

    /** ResXMLParser::indexOfAttribute, exactly: the name must match, and a
     *  null namespace matches only an attribute that has none. */
    public int getAttributeIndex(String namespace, String name) {
        if (!onStartTag() || name == null) return -1;
        final int first = b.evAttrFirst[cur], n = b.evAttrCount[cur];
        for (int i = 0; i < n; i++) {
            int r = first + i;
            if (!name.equals(b.str(b.atName[r]))) continue;
            String ns = b.str(b.atNs[r]);
            if (namespace == null) {
                if (ns == null) return i;
            } else if (ns != null && namespace.equals(ns)) {
                return i;
            }
        }
        return -1;
    }

    @Override public String getAttributeValue(String namespace, String name) {
        int idx = getAttributeIndex(namespace, name);
        return idx >= 0 ? getAttributeValue(idx) : null;
    }

    @Override public int getAttributeListValue(String namespace, String attribute, String[] options, int defaultValue) {
        int idx = getAttributeIndex(namespace, attribute);
        return idx >= 0 ? getAttributeListValue(idx, options, defaultValue) : defaultValue;
    }
    @Override public boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue) {
        int idx = getAttributeIndex(namespace, attribute);
        return idx >= 0 ? getAttributeBooleanValue(idx, defaultValue) : defaultValue;
    }
    @Override public int getAttributeResourceValue(String namespace, String attribute, int defaultValue) {
        int idx = getAttributeIndex(namespace, attribute);
        return idx >= 0 ? getAttributeResourceValue(idx, defaultValue) : defaultValue;
    }
    @Override public int getAttributeIntValue(String namespace, String attribute, int defaultValue) {
        int idx = getAttributeIndex(namespace, attribute);
        return idx >= 0 ? getAttributeIntValue(idx, defaultValue) : defaultValue;
    }
    @Override public int getAttributeUnsignedIntValue(String namespace, String attribute, int defaultValue) {
        int idx = getAttributeIndex(namespace, attribute);
        return idx >= 0 ? getAttributeUnsignedIntValue(idx, defaultValue) : defaultValue;
    }
    @Override public float getAttributeFloatValue(String namespace, String attribute, float defaultValue) {
        int idx = getAttributeIndex(namespace, attribute);
        return idx >= 0 ? getAttributeFloatValue(idx, defaultValue) : defaultValue;
    }

    @Override public int getAttributeListValue(int idx, String[] options, int defaultValue) {
        final int t = typeOf(idx);
        final int v = dataOf(idx);
        if (t == TYPE_STRING) {
            // XmlUtils.convertValueToList(value, options, defaultValue).
            String s = b.str(v);
            if (s != null && options != null) {
                for (int i = 0; i < options.length; i++) if (s.equals(options[i])) return i;
            }
            return defaultValue;
        }
        return v;
    }
    @Override public boolean getAttributeBooleanValue(int idx, boolean defaultValue) {
        final int t = typeOf(idx);
        if (t >= TYPE_FIRST_INT && t <= TYPE_LAST_INT) return dataOf(idx) != 0;
        return defaultValue;
    }
    @Override public int getAttributeResourceValue(int idx, int defaultValue) {
        return typeOf(idx) == TYPE_REFERENCE ? dataOf(idx) : defaultValue;
    }
    @Override public int getAttributeIntValue(int idx, int defaultValue) {
        final int t = typeOf(idx);
        return (t >= TYPE_FIRST_INT && t <= TYPE_LAST_INT) ? dataOf(idx) : defaultValue;
    }
    @Override public int getAttributeUnsignedIntValue(int idx, int defaultValue) {
        final int t = typeOf(idx);
        return (t >= TYPE_FIRST_INT && t <= TYPE_LAST_INT) ? dataOf(idx) : defaultValue;
    }
    @Override public float getAttributeFloatValue(int idx, float defaultValue) {
        if (typeOf(idx) == TYPE_FLOAT) return Float.intBitsToFloat(dataOf(idx));
        throw new RuntimeException("not a float!");
    }

    /** 0-based index of the attribute a 1-based attrExt index names, or -1. */
    private int special(int[] oneBased) {
        if (!onStartTag()) return -1;
        int v = oneBased[cur];
        return (v > 0 && v <= b.evAttrCount[cur]) ? v - 1 : -1;
    }
    @Override public String getIdAttribute() {
        int i = special(b.evIdIndex);
        return i < 0 ? null : b.str(b.atRaw[b.evAttrFirst[cur] + i]);
    }
    @Override public String getClassAttribute() {
        int i = special(b.evClassIndex);
        return i < 0 ? null : b.str(b.atRaw[b.evAttrFirst[cur] + i]);
    }
    @Override public int getIdAttributeResourceValue(int defaultValue) {
        return getAttributeResourceValue(null, "id", defaultValue);
    }
    @Override public int getStyleAttribute() {
        int i = special(b.evStyleIndex);
        if (i < 0) return 0;
        int t = typeOf(i);
        return (t == TYPE_REFERENCE || t == TYPE_ATTRIBUTE) ? dataOf(i) : 0;
    }

    // ------------------------------------------------------------ spelling
    /** The decoded-source spelling of attribute row `r`, value for value what
     *  BinaryXmlParser.setAttr renders (names mode when `names` is set). */
    private String spell(int r) {
        final int t = b.atType[r];
        final int data = b.atData[r];
        final int nameIdx = b.atName[r];
        final int attrId = (nameIdx >= 0 && nameIdx < b.resIds.length) ? b.resIds[nameIdx] : 0;
        switch (t) {
            case TYPE_STRING: {
                String s = b.str(data);
                return s != null ? s : "";
            }
            case TYPE_INT_BOOLEAN:
                return data != 0 ? "true" : "false";
            case TYPE_INT_DEC:
            case TYPE_INT_HEX: {
                String sym = (names != null && attrId != 0) ? names.enumText(attrId, t, data) : null;
                if (sym != null) return sym;
                return t == TYPE_INT_DEC ? Integer.toString(data) : "0x" + Integer.toHexString(data);
            }
            case TYPE_FLOAT:
                return Float.toString(Float.intBitsToFloat(data));
            case TYPE_DIMENSION: {
                String v = complexLiteral(data, false);
                if (names != null && v.endsWith("dip")) v = v.substring(0, v.length() - 3) + "dp";
                return v;
            }
            case TYPE_FRACTION:
                return complexLiteral(data, true);
            case TYPE_REFERENCE:
                return refText(data, false);
            case TYPE_ATTRIBUTE:
                return refText(data, true);
            case TYPE_NULL:
                // Res_value DATA_NULL_EMPTY (1) is the compiled "@empty".
                return names != null ? (data == 1 ? "@empty" : "@null") : "";
            default:
                if (t >= TYPE_FIRST_COLOR && t <= TYPE_LAST_COLOR) {
                    return names != null ? colorText(t, data) : String.format("#%08x", data);
                }
                return Integer.toString(data);
        }
    }

    /** "@type/name" / "@android:type/name" / "?android:name", or the raw hex
     *  form for an id no table names (BinaryXmlParser.refText). */
    private String refText(int resId, boolean attr) {
        if (resId == 0) return attr ? "?0x00000000" : "@null";
        String tn = names != null ? names.typeName(resId) : null;
        if (tn == null) return String.format(attr ? "?0x%08x" : "@0x%08x", resId);
        String prefix = ((resId >>> 24) == 0x01) ? "android:" : "";
        if (attr && tn.startsWith("attr/")) tn = tn.substring(5);
        return (attr ? "?" : "@") + prefix + tn;
    }

    /** A TYPE_DIMENSION / TYPE_FRACTION as its source literal
     *  (BinaryXmlParser.complexLiteral; ResourceTypes.h complex layout:
     *  mantissa bits 8..31, radix bits 4..5, unit bits 0..3). */
    private static String complexLiteral(int data, boolean fraction) {
        int mantissa = data & 0xffffff00;
        int radix = (data >> 4) & 0x3;
        float f;
        switch (radix) {
            case 0:  f = (mantissa >> 8); break;
            case 1:  f = mantissa * (1.0f / (1 << 15)); break;
            case 2:  f = mantissa * (1.0f / (1 << 23)); break;
            default: f = mantissa * (1.0f / (1L << 31)); break;
        }
        int unit = data & 0xf;
        if (fraction) return (f * 100f) + (unit == 1 ? "%p" : "%");
        String suffix;
        switch (unit) {
            case 0:  suffix = "px";  break;
            case 1:  suffix = "dip"; break;
            case 2:  suffix = "sp";  break;
            case 3:  suffix = "pt";  break;
            case 4:  suffix = "in";  break;
            case 5:  suffix = "mm";  break;
            default: suffix = "px";  break;
        }
        return f + suffix;
    }

    /** RGB8 "#rrggbb", ARGB8 "#aarrggbb", the 4-bit flavours one nibble per
     *  channel (BinaryXmlParser.colorText). */
    private static String colorText(int dataType, int data) {
        switch (dataType) {
            case 0x1c: return String.format("#%08x", data);
            case 0x1d: return String.format("#%06x", data & 0xffffff);
            case 0x1e: return String.format("#%x%x%x%x", (data >>> 28) & 0xf,
                (data >>> 20) & 0xf, (data >>> 12) & 0xf, (data >>> 4) & 0xf);
            case 0x1f: return String.format("#%x%x%x", (data >>> 20) & 0xf,
                (data >>> 12) & 0xf, (data >>> 4) & 0xf);
            default:   return String.format("#%08x", data);
        }
    }
}
