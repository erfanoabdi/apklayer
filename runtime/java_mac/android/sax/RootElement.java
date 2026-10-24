package android.sax;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;
/** The root of an android.sax parse tree. getContentHandler() returns a SAX
 *  handler that walks the declared Element tree, firing the registered listeners
 *  (a real, working implementation). */
public class RootElement extends Element {
    public RootElement(String uri, String localName) { super(uri, localName); }
    public RootElement(String localName) { super("", localName); }
    public ContentHandler getContentHandler() {
        return new DefaultHandler() {
            private final java.util.ArrayDeque<Element> stack = new java.util.ArrayDeque<>();
            private final StringBuilder text = new StringBuilder();
            @Override public void startDocument() { stack.clear(); stack.push(RootElement.this); }
            @Override public void startElement(String uri, String localName, String qName, Attributes attributes) {
                flushText();
                Element cur = stack.peek();
                Element child = cur != null ? cur.children.get((uri == null ? "" : uri) + ":" + localName) : null;
                if (child == null && cur != null) child = cur.children.get(":" + localName);
                if (child == null) child = new Element(uri, localName);
                stack.push(child);
                if (child.startListener != null) child.startListener.start(attributes);
                if (child.startTextListener != null) child.startTextListener.start(attributes);
                text.setLength(0);
            }
            @Override public void characters(char[] ch, int start, int length) { text.append(ch, start, length); }
            @Override public void endElement(String uri, String localName, String qName) {
                Element cur = stack.peek();
                if (cur != null && cur.endTextListener != null) cur.endTextListener.end(text.toString());
                if (cur != null && cur.endListener != null) cur.endListener.end();
                text.setLength(0);
                if (!stack.isEmpty()) stack.pop();
            }
            private void flushText() { text.setLength(0); }
        };
    }
}
