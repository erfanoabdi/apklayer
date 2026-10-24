// MacApkExpatReader -- Android's ExpatReader, as an XMLReader over the host
// JDK's Xerces parser.
//
// On a device every framework SAX path ends in org.apache.harmony.xml.ExpatReader
// (AOSP android14-release libcore luni/src/main/java/org/apache/harmony/xml/
// ExpatReader.java): SAXParserFactory.newInstance().newSAXParser() builds one
// (SAXParserImpl.java:48-53), and so does android.util.Xml.parse through
// libcore.util.XmlObjectFactory.newXMLReader() (XmlObjectFactory.java:74-76).
// There is no Expat in this process, so the actual scanning is Xerces'. This
// class supplies everything an app can OBSERVE of the reader:
//
//  * The feature surface, copied from ExpatReader.getFeature/setFeature:
//    namespaces ON and namespace-prefixes OFF by default (ExpatReader.java:
//    53-54); validation and both external-entity features read false and
//    refuse to be enabled (SAXNotSupportedException "Cannot enable ...");
//    string-interning reads true and refuses to be disabled; any other name
//    is SAXNotRecognizedException. The only property is lexical-handler.
//  * Handler dispatch read PER EVENT from this reader's fields, the way
//    ExpatParser reads xmlReader.contentHandler at each callback ("The user
//    should be able to safely change them during parsing").
//  * What Expat does NOT report is not reported: skippedEntity (the native
//    bridge sets no skipped-entity handler), LexicalHandler startEntity /
//    endEntity (ExpatReader's own javadoc says it supports neither),
//    ignorableWhitespace (Expat has no content models, so all text is
//    characters), and the ErrorHandler (ExpatParser throws its ParseException
//    directly; a Xerces fatal error is rethrown the same way, recoverable
//    errors and warnings are dropped).
//  * External resources the way ExpatParser handles them. Expat is created
//    without XML_SetParamEntityParsing, so the external DTD subset and
//    external parameter entities are never read at all; an external GENERAL
//    entity goes to the app's EntityResolver, and when there is none or it
//    returns null the entity is silently empty (ExpatParser.java:258-263 and
//    the "we just do nothing instead" comment below it). Xerces is set up to
//    match: load-external-dtd and external-parameter-entities OFF, and every
//    external general entity routed through resolveEntity below, which hands
//    Xerces an EMPTY source unless the app's resolver answered. So a document
//    with a remote DOCTYPE (every SVG exported with the W3C SVG 1.1 DTD) never
//    touches the network here, as on a device; under the JDK default it made a
//    synchronous HTTP fetch of the DTD, and the whole parse failed when that
//    fetch did.
//
//    The systemId the app's resolver sees follows ExpatParser too: a relative
//    one is resolved against the DOCUMENT's systemId when there is one, and
//    otherwise passed through as written. (Xerces' plain EntityResolver path
//    would absolutize it against the process cwd -- "file:///<host dir>/x" --
//    so the resolver is reached through EntityResolver2, which carries the
//    literal systemId.)
//  * Every OTHER systemId an app can read is the literal one, never a host
//    path. DTDHandler.notationDecl / unparsedEntityDecl get Expat's systemId
//    as written (org_apache_harmony_xml_ExpatParser.cpp:929-968 drop Expat's
//    `base`), so Xerces' resolve-dtd-uris is switched off; the Locator is
//    ExpatParser.ExpatLocator's shape (ExpatParser.java:687-710): the
//    InputSource's own publicId / systemId, a plain Locator (not Locator2).
//
// Named MacApk* rather than org.apache.harmony.xml.ExpatReader because it is
// not the native-backed AOSP class. An app that constructs ExpatReader BY NAME
// (it is @UnsupportedAppUsage, so a few do, reflectively) is not covered by
// this class; a thin org.apache.harmony.xml.ExpatReader subclass would be.
package org.apache.harmony.xml.parsers;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.EntityResolver2;
import org.xml.sax.ext.LexicalHandler;

public class MacApkExpatReader implements XMLReader {

    /** The ONE read of the bisect gate. MACAPK_SAX_ANDROID_DEFAULTS=0 restores
     *  the host JAXP SAX behaviour everywhere this class family is wired in:
     *  SAXParserFactoryImpl forwards to the JDK factory and android.util.Xml
     *  goes back to a namespace-aware Xerces reader with Xerces' own features. */
    public static final boolean ANDROID_DEFAULTS =
            !"0".equals(System.getenv("MACAPK_SAX_ANDROID_DEFAULTS"));

    /*
     * Read at every event by the Dispatch below, so they can be changed during
     * a parse, as ExpatParser allows.
     */
    ContentHandler contentHandler;
    DTDHandler dtdHandler;
    EntityResolver entityResolver;
    ErrorHandler errorHandler;
    LexicalHandler lexicalHandler;

    private boolean processNamespaces = true;
    private boolean processNamespacePrefixes = false;

    private static final String LEXICAL_HANDLER_PROPERTY
            = "http://xml.org/sax/properties/lexical-handler";

    /** The Xerces-only switch for the external DTD subset (Expat never reads it). */
    private static final String LOAD_EXTERNAL_DTD
            = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    /** SAX 2.0.2: hand an EntityResolver2 the LITERAL systemId (Xerces' default). */
    private static final String USE_ENTITY_RESOLVER2
            = "http://xml.org/sax/features/use-entity-resolver2";

    /** SAX 2.0.2: OFF reports notation / unparsed-entity systemIds as written.
     *  Xerces defaults it ON, which absolutizes them against the host cwd. */
    private static final String RESOLVE_DTD_URIS
            = "http://xml.org/sax/features/resolve-dtd-uris";

    private static class Feature {
        private static final String BASE_URI = "http://xml.org/sax/features/";
        private static final String VALIDATION = BASE_URI + "validation";
        private static final String NAMESPACES = BASE_URI + "namespaces";
        private static final String NAMESPACE_PREFIXES = BASE_URI + "namespace-prefixes";
        private static final String STRING_INTERNING = BASE_URI + "string-interning";
        private static final String EXTERNAL_GENERAL_ENTITIES
                = BASE_URI + "external-general-entities";
        private static final String EXTERNAL_PARAMETER_ENTITIES
                = BASE_URI + "external-parameter-entities";
    }

    public MacApkExpatReader() {
    }

    // ---- ExpatReader's feature / property / handler surface, verbatim ----

    public boolean getFeature(String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }

        if (name.equals(Feature.VALIDATION)
                || name.equals(Feature.EXTERNAL_GENERAL_ENTITIES)
                || name.equals(Feature.EXTERNAL_PARAMETER_ENTITIES)) {
            return false;
        }

        if (name.equals(Feature.NAMESPACES)) {
            return processNamespaces;
        }

        if (name.equals(Feature.NAMESPACE_PREFIXES)) {
            return processNamespacePrefixes;
        }

        if (name.equals(Feature.STRING_INTERNING)) {
            return true;
        }

        throw new SAXNotRecognizedException(name);
    }

    public void setFeature(String name, boolean value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }

        if (name.equals(Feature.VALIDATION)
                || name.equals(Feature.EXTERNAL_GENERAL_ENTITIES)
                || name.equals(Feature.EXTERNAL_PARAMETER_ENTITIES)) {
            if (value) {
                throw new SAXNotSupportedException("Cannot enable " + name);
            } else {
                // Default.
                return;
            }
        }

        if (name.equals(Feature.NAMESPACES)) {
            processNamespaces = value;
            return;
        }

        if (name.equals(Feature.NAMESPACE_PREFIXES)) {
            processNamespacePrefixes = value;
            return;
        }

        if (name.equals(Feature.STRING_INTERNING)) {
            if (value) {
                // Default.
                return;
            } else {
                throw new SAXNotSupportedException("Cannot disable " + name);
            }
        }

        throw new SAXNotRecognizedException(name);
    }

    public Object getProperty(String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }

        if (name.equals(LEXICAL_HANDLER_PROPERTY)) {
            return lexicalHandler;
        }

        throw new SAXNotRecognizedException(name);
    }

    public void setProperty(String name, Object value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }

        if (name.equals(LEXICAL_HANDLER_PROPERTY)) {
            // The object must implement LexicalHandler
            if (value instanceof LexicalHandler || value == null) {
                this.lexicalHandler = (LexicalHandler) value;
                return;
            }
            throw new SAXNotSupportedException("value doesn't implement " +
                    "org.xml.sax.ext.LexicalHandler");
        }

        throw new SAXNotRecognizedException(name);
    }

    public void setEntityResolver(EntityResolver resolver) {
        this.entityResolver = resolver;
    }

    public EntityResolver getEntityResolver() {
        return entityResolver;
    }

    public void setDTDHandler(DTDHandler dtdHandler) {
        this.dtdHandler = dtdHandler;
    }

    public DTDHandler getDTDHandler() {
        return dtdHandler;
    }

    public void setContentHandler(ContentHandler handler) {
        this.contentHandler = handler;
    }

    public ContentHandler getContentHandler() {
        return this.contentHandler;
    }

    public void setErrorHandler(ErrorHandler handler) {
        this.errorHandler = handler;
    }

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public LexicalHandler getLexicalHandler() {
        return lexicalHandler;
    }

    public void setLexicalHandler(LexicalHandler lexicalHandler) {
        this.lexicalHandler = lexicalHandler;
    }

    public boolean isNamespaceProcessingEnabled() {
        return processNamespaces;
    }

    public void setNamespaceProcessingEnabled(boolean processNamespaces) {
        this.processNamespaces = processNamespaces;
    }

    // ---- Parsing: ExpatReader's input rules, Xerces' scanner ----

    public void parse(InputSource input) throws IOException, SAXException {
        if (processNamespacePrefixes && processNamespaces) {
            // ExpatReader.parse: Expat cannot report xmlns attributes while it
            // is processing namespaces, so AOSP refuses the combination.
            throw new SAXNotSupportedException("The 'namespace-prefix' " +
                    "feature is not supported while the 'namespaces' " +
                    "feature is enabled.");
        }

        // Same precedence and same closing as ExpatReader.parse: the character
        // stream wins, then the byte stream, then the systemId (which the
        // parser opens and closes itself).
        Reader chars = input.getCharacterStream();
        InputStream bytes = chars == null ? input.getByteStream() : null;
        if (chars == null && bytes == null && input.getSystemId() == null) {
            throw new SAXException("No input specified.");
        }

        // A fresh Xerces reader per parse, as ExpatReader makes a fresh
        // ExpatParser per parse; a nested parse on this reader from inside a
        // handler therefore works here as it does there.
        XMLReader host = newHostReader();
        host.setFeature(Feature.NAMESPACES, processNamespaces);
        host.setFeature(Feature.NAMESPACE_PREFIXES, processNamespacePrefixes);
        host.setFeature(LOAD_EXTERNAL_DTD, false);
        host.setFeature(Feature.EXTERNAL_PARAMETER_ENTITIES, false);
        // ON on purpose: it is what makes Xerces ASK resolveEntity below about
        // an external general entity, which is where Expat's rule lives. With
        // it off Xerces would skip the entity even when the app's resolver
        // supplies it, which ExpatParser would have parsed.
        host.setFeature(Feature.EXTERNAL_GENERAL_ENTITIES, true);
        host.setFeature(USE_ENTITY_RESOLVER2, true);
        // Expat reports DTDHandler systemIds literally (ExpatParser.cpp:929-968).
        host.setFeature(RESOLVE_DTD_URIS, false);
        Dispatch dispatch = new Dispatch(input.getPublicId(), input.getSystemId());
        host.setContentHandler(dispatch);
        host.setDTDHandler(dispatch);
        host.setErrorHandler(dispatch);
        host.setEntityResolver(dispatch);
        host.setProperty(LEXICAL_HANDLER_PROPERTY, dispatch);
        try {
            host.parse(input);
        } finally {
            closeQuietly(chars != null ? chars : bytes);
        }
    }

    public void parse(String systemId) throws IOException, SAXException {
        parse(new InputSource(systemId));
    }

    private static XMLReader newHostReader() throws SAXException {
        try {
            return SAXParserFactory.newDefaultInstance().newSAXParser().getXMLReader();
        } catch (ParserConfigurationException e) {
            // The JDK's own parser failing to configure is an environment
            // fault, not a document error; surface it as the SAXException every
            // SAX caller already handles rather than an Error they do not.
            throw new SAXException(e);
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException | RuntimeException ignored) {
            // IoUtils.closeQuietly: a close failure after a parse is not news.
        }
    }

    /** Everything Xerces calls back into during ONE parse, forwarded to this
     *  reader's CURRENT handlers with Expat's reporting rules applied (see the
     *  class comment). */
    private final class Dispatch implements ContentHandler, DTDHandler, ErrorHandler,
            EntityResolver2, LexicalHandler {

        /** ExpatParser's `publicId` / `systemId`: the InputSource's, as given.
         *  The systemId is also the base for a relative external-entity one. */
        private final String documentPublicId;
        private final String documentSystemId;

        Dispatch(String documentPublicId, String documentSystemId) {
            this.documentPublicId = documentPublicId;
            this.documentSystemId = documentSystemId;
        }

        // ContentHandler

        @Override public void setDocumentLocator(Locator locator) {
            // ExpatParser.startDocument hands over its ExpatLocator, not the
            // scanner's: see ExpatLocator below.
            ContentHandler h = contentHandler;
            if (h != null) {
                h.setDocumentLocator(new ExpatLocator(locator, documentPublicId, documentSystemId));
            }
        }

        @Override public void startDocument() throws SAXException {
            ContentHandler h = contentHandler;
            if (h != null) h.startDocument();
        }

        @Override public void endDocument() throws SAXException {
            ContentHandler h = contentHandler;
            if (h != null) h.endDocument();
        }

        @Override public void startPrefixMapping(String prefix, String uri) throws SAXException {
            ContentHandler h = contentHandler;
            if (h != null) h.startPrefixMapping(prefix, uri);
        }

        @Override public void endPrefixMapping(String prefix) throws SAXException {
            ContentHandler h = contentHandler;
            if (h != null) h.endPrefixMapping(prefix);
        }

        @Override public void startElement(String uri, String localName, String qName,
                Attributes atts) throws SAXException {
            ContentHandler h = contentHandler;
            if (h != null) h.startElement(uri, localName, qName, atts);
        }

        @Override public void endElement(String uri, String localName, String qName)
                throws SAXException {
            ContentHandler h = contentHandler;
            if (h != null) h.endElement(uri, localName, qName);
        }

        @Override public void characters(char[] ch, int start, int length) throws SAXException {
            ContentHandler h = contentHandler;
            if (h != null) h.characters(ch, start, length);
        }

        @Override public void ignorableWhitespace(char[] ch, int start, int length)
                throws SAXException {
            // Expat never classifies whitespace; it is all character data.
            ContentHandler h = contentHandler;
            if (h != null) h.characters(ch, start, length);
        }

        @Override public void processingInstruction(String target, String data)
                throws SAXException {
            ContentHandler h = contentHandler;
            if (h != null) h.processingInstruction(target, data);
        }

        @Override public void skippedEntity(String name) {
            // Not reported by ExpatParser.
        }

        // DTDHandler

        @Override public void notationDecl(String name, String publicId, String systemId)
                throws SAXException {
            DTDHandler h = dtdHandler;
            if (h != null) h.notationDecl(name, publicId, systemId);
        }

        @Override public void unparsedEntityDecl(String name, String publicId, String systemId,
                String notationName) throws SAXException {
            DTDHandler h = dtdHandler;
            if (h != null) h.unparsedEntityDecl(name, publicId, systemId, notationName);
        }

        // ErrorHandler: ExpatParser never calls the app's; it throws.

        @Override public void warning(SAXParseException e) {
        }

        @Override public void error(SAXParseException e) {
        }

        @Override public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }

        // EntityResolver2: only external GENERAL entities reach resolveEntity
        // (the DTD subset and parameter entities are switched off above).

        @Override public InputSource resolveEntity(String name, String publicId, String baseURI,
                String systemId) throws SAXException, IOException {
            // ExpatParser.handleExternalEntity: resolve a relative systemId
            // against the document's; anything that goes wrong keeps the original.
            if (documentSystemId != null && systemId != null) {
                try {
                    URI systemUri = new URI(systemId);
                    if (!systemUri.isAbsolute() && !systemUri.isOpaque()) {
                        systemId = new URI(documentSystemId).resolve(systemUri).toString();
                    }
                } catch (Exception e) {
                    // AOSP logs and goes on with the original systemId.
                }
            }
            EntityResolver r = entityResolver;
            InputSource source = r != null ? r.resolveEntity(publicId, systemId) : null;
            // ExpatParser returns without parsing when there is no resolver or it
            // answers null. An empty source is how Xerces is told "this entity
            // is empty"; returning null would make Xerces open the URL itself.
            return source != null ? source : new InputSource(new StringReader(""));
        }

        @Override public InputSource resolveEntity(String publicId, String systemId)
                throws SAXException, IOException {
            // Unused while use-entity-resolver2 is on; kept equivalent.
            return resolveEntity(null, publicId, null, systemId);
        }

        @Override public InputSource getExternalSubset(String name, String baseURI) {
            // Expat never supplies an external subset a document did not declare.
            return null;
        }

        // LexicalHandler

        @Override public void startDTD(String name, String publicId, String systemId)
                throws SAXException {
            LexicalHandler h = lexicalHandler;
            if (h != null) h.startDTD(name, publicId, systemId);
        }

        @Override public void endDTD() throws SAXException {
            LexicalHandler h = lexicalHandler;
            if (h != null) h.endDTD();
        }

        @Override public void startEntity(String name) {
            // ExpatReader supports neither startEntity nor endEntity.
        }

        @Override public void endEntity(String name) {
        }

        @Override public void startCDATA() throws SAXException {
            LexicalHandler h = lexicalHandler;
            if (h != null) h.startCDATA();
        }

        @Override public void endCDATA() throws SAXException {
            LexicalHandler h = lexicalHandler;
            if (h != null) h.endCDATA();
        }

        @Override public void comment(char[] ch, int start, int length) throws SAXException {
            LexicalHandler h = lexicalHandler;
            if (h != null) h.comment(ch, start, length);
        }
    }

    /** ExpatParser.ExpatLocator (ExpatParser.java:687-710): the publicId and
     *  systemId are the ones the parse was started with, never re-derived, so
     *  a relative "doc.xml" stays "doc.xml" (Xerces' own locator reports
     *  "file:///<host cwd>/doc.xml"). A plain Locator, as on a device: Xerces'
     *  implements Locator2, and `instanceof Locator2` must answer as Expat's
     *  does. Line and column still come from the scanner doing the parsing. */
    private static final class ExpatLocator implements Locator {
        private final Locator scanner;
        private final String publicId;
        private final String systemId;

        ExpatLocator(Locator scanner, String publicId, String systemId) {
            this.scanner = scanner;
            this.publicId = publicId;
            this.systemId = systemId;
        }

        @Override public String getPublicId() {
            return publicId;
        }

        @Override public String getSystemId() {
            return systemId;
        }

        @Override public int getLineNumber() {
            return scanner != null ? scanner.getLineNumber() : -1;
        }

        @Override public int getColumnNumber() {
            return scanner != null ? scanner.getColumnNumber() : -1;
        }

        @Override public String toString() {
            return "Locator[publicId: " + publicId + ", systemId: " + systemId
                + ", line: " + getLineNumber()
                + ", column: " + getColumnNumber() + "]";
        }
    }
}
