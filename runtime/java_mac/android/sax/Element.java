package android.sax;
import java.util.HashMap;
import java.util.Map;
/** A node in an android.sax parse tree. Children are looked up by (uri,localName)
 *  and listeners fire as the SAX handler walks. */
public class Element {
    final String uri, localName;
    final Map<String, Element> children = new HashMap<>();
    StartElementListener startListener;
    EndElementListener endListener;
    EndTextElementListener endTextListener;
    StartTextElementListener startTextListener;
    Element(String uri, String localName) { this.uri = uri; this.localName = localName; }
    public Element getChild(String localName) { return getChild("", localName); }
    public Element getChild(String uri, String localName) {
        String key = uri + ":" + localName;
        Element c = children.get(key);
        if (c == null) { c = new Element(uri, localName); children.put(key, c); }
        return c;
    }
    public Element requireChild(String localName) { return getChild(localName); }
    public Element requireChild(String uri, String localName) { return getChild(uri, localName); }
    public void setStartElementListener(StartElementListener listener) { startListener = listener; }
    public void setEndElementListener(EndElementListener listener) { endListener = listener; }
    public void setEndTextElementListener(EndTextElementListener listener) { endTextListener = listener; }
    public void setTextElementListener(TextElementListener listener) { startTextListener = listener; endTextListener = listener; }
    public void setElementListener(ElementListener listener) { startListener = listener; endListener = listener; }
    public interface ElementListener extends StartElementListener, EndElementListener {}
}
