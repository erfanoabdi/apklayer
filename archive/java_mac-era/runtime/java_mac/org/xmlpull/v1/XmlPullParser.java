// org.xmlpull.v1.XmlPullParser -- the streaming-XML-cursor interface
// that Android's XmlResourceParser extends. Apps (including
// androidx.preference.PreferenceInflater that F-Droid uses) call
// `next()` / `getName()` / `getDepth()` / `getEventType()` directly
// on an XmlResourceParser via this interface.
//
// OpenJDK does not ship org.xmlpull.v1 (it was the API of choice for
// Java ME / early Android). The xpp3 reference implementation lives
// at http://www.xmlpull.org/. We provide just the interface
// definitions here and back them with a DOM-based concrete impl in
// com.macapk.loader.DomXmlResourceParser.
//
// Method surface mirrors xpp3 1.1.4c (the public API that's been
// stable since 2002) so that .class files compiled against the
// real interface link cleanly here. Constants taken verbatim from
// the xpp3 source for binary compatibility.
package org.xmlpull.v1;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

public interface XmlPullParser {
    // Event types -- the integer constants are part of the public ABI
    // and must match xpp3's values exactly.
    int START_DOCUMENT      = 0;
    int END_DOCUMENT        = 1;
    int START_TAG           = 2;
    int END_TAG             = 3;
    int TEXT                = 4;
    int CDSECT              = 5;
    int ENTITY_REF          = 6;
    int IGNORABLE_WHITESPACE = 7;
    int PROCESSING_INSTRUCTION = 8;
    int COMMENT             = 9;
    int DOCDECL             = 10;

    // Feature constants -- apps rarely set these via XmlResourceParser;
    // included for ABI completeness.
    String NO_NAMESPACE = "";
    String FEATURE_PROCESS_NAMESPACES =
        "http://xmlpull.org/v1/doc/features.html#process-namespaces";
    String FEATURE_REPORT_NAMESPACE_ATTRIBUTES =
        "http://xmlpull.org/v1/doc/features.html#report-namespace-prefixes";
    String FEATURE_PROCESS_DOCDECL =
        "http://xmlpull.org/v1/doc/features.html#process-docdecl";
    String FEATURE_VALIDATION =
        "http://xmlpull.org/v1/doc/features.html#validation";

    String[] TYPES = {
        "START_DOCUMENT", "END_DOCUMENT", "START_TAG", "END_TAG",
        "TEXT", "CDSECT", "ENTITY_REF", "IGNORABLE_WHITESPACE",
        "PROCESSING_INSTRUCTION", "COMMENT", "DOCDECL"
    };

    // ----- Configuration -----
    void setFeature(String name, boolean state) throws XmlPullParserException;
    boolean getFeature(String name);
    void setProperty(String name, Object value) throws XmlPullParserException;
    Object getProperty(String name);

    // ----- Input -----
    void setInput(Reader in) throws XmlPullParserException;
    void setInput(InputStream inputStream, String inputEncoding)
        throws XmlPullParserException;
    String getInputEncoding();
    void defineEntityReplacementText(String entityName, String replacementText)
        throws XmlPullParserException;

    // ----- Namespace stack -----
    int getNamespaceCount(int depth) throws XmlPullParserException;
    String getNamespacePrefix(int pos) throws XmlPullParserException;
    String getNamespaceUri(int pos) throws XmlPullParserException;
    String getNamespace(String prefix);
    String getNamespace();

    // ----- Position / event state -----
    int getDepth();
    String getPositionDescription();
    int getLineNumber();
    int getColumnNumber();
    boolean isWhitespace() throws XmlPullParserException;
    String getText();
    char[] getTextCharacters(int[] holderForStartAndLength);

    // ----- Element data (for START_TAG / END_TAG events) -----
    String getName();
    String getPrefix();
    boolean isEmptyElementTag() throws XmlPullParserException;
    int getAttributeCount();
    String getAttributeNamespace(int index);
    String getAttributeName(int index);
    String getAttributePrefix(int index);
    String getAttributeType(int index);
    boolean isAttributeDefault(int index);
    String getAttributeValue(int index);
    String getAttributeValue(String namespace, String name);

    // ----- Cursor advance -----
    int getEventType() throws XmlPullParserException;
    int next() throws XmlPullParserException, IOException;
    int nextToken() throws XmlPullParserException, IOException;
    void require(int type, String namespace, String name)
        throws XmlPullParserException, IOException;
    String nextText() throws XmlPullParserException, IOException;
    int nextTag() throws XmlPullParserException, IOException;
}
