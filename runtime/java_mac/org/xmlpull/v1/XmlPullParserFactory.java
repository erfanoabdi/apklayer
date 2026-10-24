// org.xmlpull.v1.XmlPullParserFactory -- builds XmlPullParser
// instances. Apps that need an XPP for their own parsing call
// `XmlPullParserFactory.newInstance().newPullParser()`. We back this
// with the DOM-walking impl from com.macapk.loader so the factory
// produces a working parser when given a raw XML stream.
package org.xmlpull.v1;

public class XmlPullParserFactory {
    protected boolean namespaceAware;

    protected XmlPullParserFactory() {}

    public static XmlPullParserFactory newInstance() throws XmlPullParserException {
        return new XmlPullParserFactory();
    }

    public static XmlPullParserFactory newInstance(String classNames, Class<?> context)
        throws XmlPullParserException {
        return newInstance();
    }

    public XmlPullParser newPullParser() throws XmlPullParserException {
        try {
            // Indirect through reflection so this framework class doesn't
            // pull com.macapk.loader into android-framework.jar's
            // dependency graph (the loader jar depends on framework, so
            // framework cannot link to loader at compile time).
            Class<?> impl = Class.forName("com.macapk.loader.DomXmlResourceParser");
            XmlPullParser p = (XmlPullParser) impl.getDeclaredConstructor().newInstance();
            // AOSP's factory returns KXmlParser, whose FIRST next() is the first
            // START_TAG; the same class read as a resource parser reports
            // START_DOCUMENT there (XmlBlock). See DomXmlResourceParser's
            // "two event contracts" note. =0 keeps the resource contract.
            if (!"0".equals(System.getenv("MACAPK_XMLPULL_KXML_EVENTS"))) {
                impl.getMethod("macapkPullParserSemantics").invoke(p);
            }
            return p;
        } catch (Throwable t) {
            throw new XmlPullParserException("could not instantiate parser: " + t);
        }
    }

    /** AOSP XmlPullParserFactory.newSerializer() (libcore xml/src/main/java/
     *  org/xmlpull/v1/XmlPullParserFactory.java, android11-release): the
     *  protected constructor seeds serializerClasses with exactly one entry,
     *  Class.forName("com.android.org.kxml2.io.KXmlSerializer"), so
     *  newSerializer() always resolves to that one concrete class. That
     *  class already exists in THIS framework jar (unlike newPullParser's
     *  target, it is not in the separate loader jar), and
     *  android.util.Xml.newSerializer() already instantiates it directly --
     *  see that method's comment. Mirror it here directly rather than
     *  reflectively: no cross-jar dependency-graph concern applies. */
    public XmlSerializer newSerializer() throws XmlPullParserException {
        return new com.android.org.kxml2.io.KXmlSerializer();
    }

    public void setNamespaceAware(boolean awareness)  { this.namespaceAware = awareness; }
    public boolean getNamespaceAware()                { return namespaceAware; }
    public void setValidating(boolean validating)     { /* not supported */ }
    public boolean isValidating()                     { return false; }
    public void setFeature(String name, boolean state) throws XmlPullParserException {}
    public boolean getFeature(String name)            { return false; }
}
