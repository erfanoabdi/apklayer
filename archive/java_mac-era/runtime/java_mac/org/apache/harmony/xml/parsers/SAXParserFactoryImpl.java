/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// ---------------------------------------------------------------------------
// Mac.apk note (added with this file, 2026-09-25)
// ---------------------------------------------------------------------------
// Port of AOSP android14-release libcore
// luni/src/main/java/org/apache/harmony/xml/parsers/SAXParserFactoryImpl.java,
// kept under AOSP's class name so getClass().getName() answers what a device
// answers. The feature map, the "http://xml.org/sax/features/" prefix rule and
// the deferred feature check in newSAXParser() are AOSP's, line for line.
//
// WHY IT EXISTS. On Android, SAXParserFactory.newInstance() is
// `return new SAXParserFactoryImpl();` (libcore SAXParserFactory.java:64-66).
// That factory REPORTS isNamespaceAware()==false (its map is empty), but the
// reader it builds is an ExpatReader whose processNamespaces defaults to TRUE
// (ExpatReader.java:53-54), so every startElement carries the real localName.
// Mac.apk used to serve the host JDK's Xerces factory under the JAXP default
// instead: namespace processing OFF, so localName is "" for every element
// (Xerces AbstractSAXParser.java:516). An Android SAX handler that dispatches
// on localName then silently matches nothing.
// MEASURED VICTIM: Telegram's SvgHelper$SVGHandler switches on localName; with
// "" it never sees <svg>, never creates its bitmap, and the chat-wallpaper
// doodle pattern is missing (docs/telegram-chat-wallpaper-pattern_2026-09-25.md).
// Offline, the app's own handler on res/VSz.svg returns null under the host
// default and a 1636x3362 bitmap (10.71% inked) under Android's reader.
//
// HOW IT IS WIRED. This class is the JAXP provider named in
// META-INF/services/javax.xml.parsers.SAXParserFactory (runtime/net_spi/,
// packaged into macapk-loader.jar). JDK 21 FactoryFinder.find() consults the
// system property, then jaxp.properties, then that ServiceLoader entry, and
// only then its Xerces fallback, so every SAXParserFactory.newInstance() in
// the process -- the app's and an offline gate's alike -- lands here with no
// Boot code. Parsing itself is delegated to Xerces through MacApkExpatReader,
// which gives it Expat's feature semantics and never fetches an external DTD.
//
// BISECT GATE: MACAPK_SAX_ANDROID_DEFAULTS=0 turns this factory into a plain
// forwarder to SAXParserFactory.newDefaultInstance(), i.e. exactly the host
// JAXP behaviour Mac.apk had before (namespace-unaware, Xerces features).
// Gate: debug/saxns_gate.sh.

package org.apache.harmony.xml.parsers;

import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.Schema;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * Provides a straightforward SAXParserFactory implementation based on
 * Expat. The class is used internally only, thus only notable members
 * that are not already in the abstract superclass are documented.
 */
public class SAXParserFactoryImpl extends SAXParserFactory {

    private static final String NAMESPACES
            = "http://xml.org/sax/features/namespaces";

    private static final String VALIDATION
            = "http://xml.org/sax/features/validation";

    private Map<String, Boolean> features = new HashMap<String, Boolean>();

    /** Non-null only under MACAPK_SAX_ANDROID_DEFAULTS=0: the JDK's own
     *  factory, which every method below then forwards to unchanged. */
    private final SAXParserFactory hostDefault =
            MacApkExpatReader.ANDROID_DEFAULTS ? null : SAXParserFactory.newDefaultInstance();

    @Override
    public boolean getFeature(String name) throws ParserConfigurationException,
            SAXNotRecognizedException, SAXNotSupportedException {
        if (hostDefault != null) {
            return hostDefault.getFeature(name);
        }
        if (name == null) {
            throw new NullPointerException("name == null");
        }

        if (!name.startsWith("http://xml.org/sax/features/")) {
            throw new SAXNotRecognizedException(name);
        }

        return Boolean.TRUE.equals(features.get(name));
    }

    @Override
    public boolean isNamespaceAware() {
        if (hostDefault != null) {
            return hostDefault.isNamespaceAware();
        }
        return Boolean.TRUE.equals(features.get(NAMESPACES));
    }

    @Override
    public boolean isValidating() {
        if (hostDefault != null) {
            return hostDefault.isValidating();
        }
        return Boolean.TRUE.equals(features.get(VALIDATION));
    }

    @Override
    public SAXParser newSAXParser() throws ParserConfigurationException, SAXException {
        if (hostDefault != null) {
            return hostDefault.newSAXParser();
        }
        if (isValidating()) {
            throw new ParserConfigurationException(
                    "No validating SAXParser implementation available");
        }

        // An unknown feature, or one Expat cannot honour (validation or an
        // external-entity feature set true, string-interning set false), throws
        // from SAXParserImpl's replay onto the reader and surfaces HERE, exactly
        // as AOSP defers the check to newSAXParser().
        try {
            return new SAXParserImpl(features);
        } catch (Exception ex) {
            throw new ParserConfigurationException(ex.toString());
        }
    }

    @Override
    public void setFeature(String name, boolean value) throws ParserConfigurationException,
            SAXNotRecognizedException, SAXNotSupportedException {
        if (hostDefault != null) {
            hostDefault.setFeature(name, value);
            return;
        }
        if (name == null) {
            throw new NullPointerException("name == null");
        }

        if (!name.startsWith("http://xml.org/sax/features/")) {
            throw new SAXNotRecognizedException(name);
        }

        if (value) {
            features.put(name, Boolean.TRUE);
        } else {
            // This is needed to disable features that are enabled by default.
            features.put(name, Boolean.FALSE);
        }
    }

    @Override
    public void setNamespaceAware(boolean value) {
        if (hostDefault != null) {
            hostDefault.setNamespaceAware(value);
            return;
        }
        features.put(NAMESPACES, value);
    }

    @Override
    public void setValidating(boolean value) {
        if (hostDefault != null) {
            hostDefault.setValidating(value);
            return;
        }
        features.put(VALIDATION, value);
    }

    // AOSP inherits these four from its abstract base
    // (libcore javax/xml/parsers/SAXParserFactory.java), which throws
    // UnsupportedOperationException from every one of them -- including
    // setXIncludeAware(false), which the JDK base accepts. So they are spelled
    // out: the AOSP answer when the gate is on, the JDK factory's when it is off.

    @Override
    public Schema getSchema() {
        if (hostDefault != null) {
            return hostDefault.getSchema();
        }
        throw unsupported();
    }

    @Override
    public void setSchema(Schema schema) {
        if (hostDefault != null) {
            hostDefault.setSchema(schema);
            return;
        }
        throw unsupported();
    }

    @Override
    public boolean isXIncludeAware() {
        if (hostDefault != null) {
            return hostDefault.isXIncludeAware();
        }
        throw unsupported();
    }

    @Override
    public void setXIncludeAware(boolean state) {
        if (hostDefault != null) {
            hostDefault.setXIncludeAware(state);
            return;
        }
        throw unsupported();
    }

    /** The AOSP base class's message, built the same way. */
    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(
            "This parser does not support specification \""
            + this.getClass().getPackage().getSpecificationTitle()
            + "\" version \""
            + this.getClass().getPackage().getSpecificationVersion()
            + "\""
            );
    }
}
