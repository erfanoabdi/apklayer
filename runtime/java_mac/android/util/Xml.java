// android.util.Xml -- small static factory class. Apps and androidx
// libraries call `Xml.newPullParser()` for ad-hoc XML reading, and
// crucially `Xml.asAttributeSet(XmlPullParser)` which gives them an
// AttributeSet view over a parser positioned at a START_TAG. The
// asAttributeSet path is used by PreferenceInflater (and View
// inflaters in general) to read attributes off an XmlResourceParser
// while iterating with the pull-parser API.
//
// AOSP impl details: the AttributeSet returned by asAttributeSet
// reads attributes ONLY from the parser's current START_TAG. Each
// call to next() / nextTag() changes which attributes are visible.
// Our XmlResourceParser already implements AttributeSet directly
// (the interface union is by design), so when the input parser IS
// an XmlResourceParser we just return it as-is.
package android.util;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

public class Xml {
    /** XmlPullParser feature name apps pass to setFeature to opt into relaxed
     *  (non-validating, error-tolerant) parsing. AOSP declares it non-final. */
    public static String FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed";

    /** The XMLReader android.util.Xml.parse builds its SAX events with. AOSP
     *  calls libcore.util.XmlObjectFactory.newXMLReader(), which is
     *  `return new ExpatReader();` (android14-release libcore
     *  luni/src/main/java/libcore/util/XmlObjectFactory.java:74-76), and
     *  frameworks/base Xml.java:83/97/108 uses nothing else. MacApkExpatReader
     *  is that reader's observable behaviour over the host parser -- the same
     *  one SAXParserFactory.newInstance() now hands out -- so both SAX entry
     *  points agree, including Expat's refusal to fetch an external DTD.
     *
     *  MACAPK_SAX_ANDROID_DEFAULTS=0 (the bisect gate) keeps the previous
     *  reader: the host Xerces parser forced namespace-aware, with Xerces' own
     *  feature defaults (external DTD loading on). */
    private static XMLReader newXMLReader() {
        if (org.apache.harmony.xml.parsers.MacApkExpatReader.ANDROID_DEFAULTS) {
            return new org.apache.harmony.xml.parsers.MacApkExpatReader();
        }
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newSAXParser().getXMLReader();
        } catch (ParserConfigurationException | SAXException e) {
            // A JDK with no working SAX implementation is an environment
            // assertion, not a document error; AOSP's factory cannot fail here
            // either, so callers are not made to handle it.
            throw new AssertionError(e);
        }
    }

    /** Parses xml from the given string and fires events on the given SAX
     *  handler. AOSP swallows the (impossible, string-backed) IOException as an
     *  AssertionError so this overload only declares SAXException. */
    public static void parse(String xml, ContentHandler contentHandler) throws SAXException {
        try {
            XMLReader reader = newXMLReader();
            reader.setContentHandler(contentHandler);
            reader.parse(new InputSource(new StringReader(xml)));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public static void parse(Reader in, ContentHandler contentHandler)
            throws IOException, SAXException {
        XMLReader reader = newXMLReader();
        reader.setContentHandler(contentHandler);
        reader.parse(new InputSource(in));
    }

    public static void parse(InputStream in, Encoding encoding, ContentHandler contentHandler)
            throws IOException, SAXException {
        XMLReader reader = newXMLReader();
        reader.setContentHandler(contentHandler);
        InputSource source = new InputSource(in);
        source.setEncoding(encoding.expatName);
        reader.parse(source);
    }

    /** The app-facing factory for the XML *writing* API. AOSP returns
     *  com.android.org.kxml2.io.KXmlSerializer through XmlObjectFactory; we
     *  instantiate the same class directly (it is in the framework jar, so
     *  unlike newPullParser's loader-side parser no reflection is needed). */
    public static XmlSerializer newSerializer() {
        return new com.android.org.kxml2.io.KXmlSerializer();
    }

    /** Supported document encodings, named as the platform names them. */
    public enum Encoding {
        US_ASCII("US-ASCII"),
        UTF_8("UTF-8"),
        UTF_16("UTF-16"),
        ISO_8859_1("ISO-8859-1");

        final String expatName;

        Encoding(String expatName) {
            this.expatName = expatName;
        }
    }

    /** Finds an encoding by name. Returns UTF-8 for a null name. */
    public static Encoding findEncodingByName(String encodingName)
            throws UnsupportedEncodingException {
        if (encodingName == null) {
            return Encoding.UTF_8;
        }
        for (Encoding encoding : Encoding.values()) {
            if (encoding.expatName.equalsIgnoreCase(encodingName)) return encoding;
        }
        throw new UnsupportedEncodingException(encodingName);
    }

    public static XmlPullParser newPullParser() {
        try {
            XmlPullParserFactory f = XmlPullParserFactory.newInstance();
            return f.newPullParser();
        } catch (XmlPullParserException e) {
            // Match AOSP behavior: wrap in AssertionError on unrecoverable
            // factory failure. In practice we always succeed here because
            // newInstance is unconditional.
            throw new AssertionError(e);
        }
    }

    public static AttributeSet asAttributeSet(XmlPullParser parser) {
        // Cleanest path: the parser is already an AttributeSet
        // (XmlResourceParser is). Just return it. For non-AttributeSet
        // pull parsers we wrap with a lightweight adapter that reads
        // the parser's current attributes lazily on each lookup.
        if (parser instanceof AttributeSet) return (AttributeSet) parser;
        return new ParserAttributeSet(parser);
    }

    /** Lightweight AttributeSet adapter for a generic XmlPullParser. The
     *  parser must be positioned at a START_TAG event for getters to
     *  return data; the AOSP impl behaves the same way. */
    private static final class ParserAttributeSet implements AttributeSet {
        private final XmlPullParser p;
        ParserAttributeSet(XmlPullParser p) { this.p = p; }

        @Override public int getAttributeCount()                 { return p.getAttributeCount(); }
        @Override public String getAttributeName(int index)       { return p.getAttributeName(index); }
        @Override public String getAttributeValue(int index)      { return p.getAttributeValue(index); }
        @Override public String getAttributeValue(String ns, String n) { return p.getAttributeValue(ns, n); }
        @Override public String getPositionDescription()          { return p.getPositionDescription(); }
        @Override public int getAttributeNameResource(int index)  { return 0; }
        @Override public int getAttributeListValue(String ns, String a, String[] opts, int def) {
            String v = p.getAttributeValue(ns, a);
            if (v == null) return def;
            for (int i = 0; i < opts.length; ++i) if (v.equals(opts[i])) return i;
            return def;
        }
        @Override public boolean getAttributeBooleanValue(String ns, String a, boolean def) {
            String v = p.getAttributeValue(ns, a);
            if (v == null) return def;
            return Boolean.parseBoolean(v);
        }
        @Override public int getAttributeResourceValue(String ns, String a, int def) {
            return parseIntOrDef(p.getAttributeValue(ns, a), def);
        }
        @Override public int getAttributeIntValue(String ns, String a, int def) {
            return parseIntOrDef(p.getAttributeValue(ns, a), def);
        }
        @Override public int getAttributeUnsignedIntValue(String ns, String a, int def) {
            return parseIntOrDef(p.getAttributeValue(ns, a), def);
        }
        @Override public float getAttributeFloatValue(String ns, String a, float def) {
            String v = p.getAttributeValue(ns, a);
            if (v == null) return def;
            try { return Float.parseFloat(v); } catch (NumberFormatException e) { return def; }
        }
        @Override public int getAttributeListValue(int index, String[] opts, int def) {
            String v = p.getAttributeValue(index);
            if (v == null) return def;
            for (int i = 0; i < opts.length; ++i) if (v.equals(opts[i])) return i;
            return def;
        }
        @Override public boolean getAttributeBooleanValue(int index, boolean def) {
            String v = p.getAttributeValue(index);
            if (v == null) return def;
            return Boolean.parseBoolean(v);
        }
        @Override public int getAttributeResourceValue(int index, int def) {
            return parseIntOrDef(p.getAttributeValue(index), def);
        }
        @Override public int getAttributeIntValue(int index, int def) {
            return parseIntOrDef(p.getAttributeValue(index), def);
        }
        @Override public int getAttributeUnsignedIntValue(int index, int def) {
            return parseIntOrDef(p.getAttributeValue(index), def);
        }
        @Override public float getAttributeFloatValue(int index, float def) {
            String v = p.getAttributeValue(index);
            if (v == null) return def;
            try { return Float.parseFloat(v); } catch (NumberFormatException e) { return def; }
        }
        @Override public String getIdAttribute() {
            return p.getAttributeValue(null, "id");
        }
        @Override public String getClassAttribute() {
            return p.getAttributeValue(null, "class");
        }
        @Override public int getIdAttributeResourceValue(int def) {
            return parseIntOrDef(getIdAttribute(), def);
        }
        @Override public int getStyleAttribute() {
            return parseIntOrDef(p.getAttributeValue(null, "style"), 0);
        }

        private static int parseIntOrDef(String v, int def) {
            if (v == null || v.isEmpty()) return def;
            try {
                if (v.startsWith("0x") || v.startsWith("0X"))
                    return (int) Long.parseLong(v.substring(2), 16);
                return Integer.parseInt(v);
            } catch (NumberFormatException e) { return def; }
        }
    }
}
