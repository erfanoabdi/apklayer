// android.content.res.XmlResourceParser -- the type returned by
// Resources.getXml(int). On real Android this interface extends both
// org.xmlpull.v1.XmlPullParser (the streaming cursor) AND
// android.util.AttributeSet (the indexed-attribute lookup). Apps cast
// XmlResourceParser to XmlPullParser freely (PreferenceInflater
// passes it to rInflate(XmlPullParser, ...)), so the inheritance
// chain must match AOSP exactly.
//
// We provide the interface here; the concrete DOM-backed
// implementation lives in com.macapk.loader.DomXmlResourceParser
// (kept out of android-framework.jar because it needs apktool-decoded
// res files which are loaded by the runtime loader).
package android.content.res;

public interface XmlResourceParser
    extends org.xmlpull.v1.XmlPullParser,
            android.util.AttributeSet,
            AutoCloseable {
    /** REQUIRED re-abstraction, not redundancy. AttributeSet declares
     *  getAttributeNamespace(int) as a DEFAULT method (API 36, and AOSP's
     *  core/api/current.txt says "method public default"), while
     *  XmlPullParser declares the same signature ABSTRACT. An interface that
     *  inherits an abstract method from one superinterface and a default from
     *  another does not compile ("types XmlPullParser and AttributeSet are
     *  incompatible") unless it re-declares the method. AOSP's own
     *  XmlResourceParser.java carries this exact line for this exact reason:
     *      public interface XmlResourceParser extends XmlPullParser, AttributeSet, AutoCloseable {
     *          String getAttributeNamespace (int index);
     */
    String getAttributeNamespace(int index);

    /** AOSP signature: void close() (not throwing). The AutoCloseable
     *  base declares `throws Exception`; we widen the contract to
     *  match AOSP's narrower one. */
    @Override void close();
}
