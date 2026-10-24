// android.sax.ElementListener -- the convenience union of StartElementListener
// and EndElementListener, so a single object can be passed to
// Element.setElementListener(). AOSP:
// frameworks/base/core/java/android/sax/ElementListener.java
//
// Carries no members of its own; the whole point is the combined type
// (TextElementListener is the same pattern for the text case).
package android.sax;

public interface ElementListener extends StartElementListener, EndElementListener {
}
