// org.xmlpull.v1.XmlSerializer -- the XML *writing* half of the XmlPull API.
// Mac.apk shipped only the parser half, so every app that wrote XML (the
// AndroidX/Kotlin build dependency that reaches this is present in virtually
// any modern Android Studio project) hit NoClassDefFoundError on the type and
// NoSuchMethodError on android.util.Xml.newSerializer().
//
// Signatures are the libcore interface verbatim, including the detail that
// startTag/attribute/endTag/text return XmlSerializer (so callers can chain)
// while every other method returns void -- the JVM resolves invokeinterface on
// the full descriptor, so getting a return type wrong is as fatal as omitting
// the method.
//
// Source: libcore/xml/src/main/java/org/xmlpull/v1/XmlSerializer.java
// (the concrete implementation Android installs is
// com.android.org.kxml2.io.KXmlSerializer, which we mirror).
package org.xmlpull.v1;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

public interface XmlSerializer {

    void setFeature(String name, boolean state)
        throws IllegalArgumentException, IllegalStateException;

    boolean getFeature(String name);

    void setProperty(String name, Object value)
        throws IllegalArgumentException, IllegalStateException;

    Object getProperty(String name);

    /** Set the output; the encoding names the charset used to convert the
     *  written characters to bytes. A null encoding means the platform
     *  default. */
    void setOutput(OutputStream os, String encoding)
        throws IOException, IllegalArgumentException, IllegalStateException;

    void setOutput(Writer writer)
        throws IOException, IllegalArgumentException, IllegalStateException;

    /** Write the XML declaration. Can only be called just after setOutput. */
    void startDocument(String encoding, Boolean standalone)
        throws IOException, IllegalArgumentException, IllegalStateException;

    /** Close any open tags, then flush. */
    void endDocument()
        throws IOException, IllegalArgumentException, IllegalStateException;

    /** Bind a prefix to a namespace for the NEXT element written. */
    void setPrefix(String prefix, String namespace)
        throws IOException, IllegalArgumentException, IllegalStateException;

    String getPrefix(String namespace, boolean generatePrefix)
        throws IllegalArgumentException;

    int getDepth();

    String getNamespace();

    String getName();

    XmlSerializer startTag(String namespace, String name)
        throws IOException, IllegalArgumentException, IllegalStateException;

    XmlSerializer attribute(String namespace, String name, String value)
        throws IOException, IllegalArgumentException, IllegalStateException;

    XmlSerializer endTag(String namespace, String name)
        throws IOException, IllegalArgumentException, IllegalStateException;

    XmlSerializer text(String text)
        throws IOException, IllegalArgumentException, IllegalStateException;

    XmlSerializer text(char[] buf, int start, int len)
        throws IOException, IllegalArgumentException, IllegalStateException;

    void cdsect(String text)
        throws IOException, IllegalArgumentException, IllegalStateException;

    void entityRef(String text)
        throws IOException, IllegalArgumentException, IllegalStateException;

    void processingInstruction(String text)
        throws IOException, IllegalArgumentException, IllegalStateException;

    void comment(String text)
        throws IOException, IllegalArgumentException, IllegalStateException;

    void docdecl(String text)
        throws IOException, IllegalArgumentException, IllegalStateException;

    void ignorableWhitespace(String text)
        throws IOException, IllegalArgumentException, IllegalStateException;

    void flush()
        throws IOException;
}
