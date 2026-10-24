package android.view;

/**
 * The single entry point for content insertion (paste, drag-drop, autofill, IME
 * rich content). A listener consumes what it understands and RETURNS the rest,
 * so the platform can fall back for the remainder.
 *
 * AOSP declares this as an interface; declaring it any other way would break
 * every lambda registration at class-load.
 *
 * Source: frameworks/base/core/java/android/view/OnReceiveContentListener.java
 */
public interface OnReceiveContentListener {

    /**
     * Return null if all content was consumed, otherwise the unconsumed part.
     * Returning the payload unchanged means "not handled".
     */
    ContentInfo onReceiveContent(View view, ContentInfo payload);
}
