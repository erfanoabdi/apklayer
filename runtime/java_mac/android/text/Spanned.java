// android.text.Spanned -- read-only CharSequence with attached span
// markers (style, click handlers, etc.). Mac.apk never builds a real
// span pipeline so the interface is satisfied by treating any input as
// a plain CharSequence with no spans. See frameworks/base/core/java/
// android/text/Spanned.java for the AOSP definition.
package android.text;

public interface Spanned extends CharSequence {
    // AOSP span flag values (frameworks/base android/text/Spanned.java). These
    // are bit-masked and compared throughout Compose text (AndroidParagraph /
    // AndroidTextPaint span application, EmojiCompat), AndroidX SpannableString
    // interop, Linkify, and IME composing-region logic. They were WRONG
    // (II=0x33, EE=0x00, IE/EI swapped) -> every flag mask/compare computed the
    // wrong span-extension behavior. Correct per AOSP: the low nibble is the
    // END kind, the high nibble the START kind (MARK=01, POINT=10):
    //   MARK_MARK=0x11 MARK_POINT=0x12 POINT_MARK=0x21 POINT_POINT=0x22. v1.0.422.
    int SPAN_MARK_MARK           = 0x00000011;
    int SPAN_MARK_POINT          = 0x00000012;
    int SPAN_POINT_MARK          = 0x00000021;
    int SPAN_POINT_POINT         = 0x00000022;
    /** AOSP Spanned.SPAN_INTERMEDIATE (0x00000200): "this span change is part
     *  of a group; do not send a change notification yet". Selection.java ORs
     *  it into the SELECTION_START setSpan so a two-span selection update
     *  produces ONE notification rather than two. */
    int SPAN_INTERMEDIATE        = 0x00000200;
    int SPAN_INCLUSIVE_EXCLUSIVE = SPAN_MARK_MARK;    // 0x11
    int SPAN_INCLUSIVE_INCLUSIVE = SPAN_MARK_POINT;   // 0x12
    int SPAN_EXCLUSIVE_EXCLUSIVE = SPAN_POINT_MARK;   // 0x21
    int SPAN_EXCLUSIVE_INCLUSIVE = SPAN_POINT_POINT;  // 0x22
    int SPAN_PARAGRAPH           = 0x00000033;
    int SPAN_COMPOSING           = 0x00000100;
    int SPAN_POINT_MARK_MASK     = 0x00000033;
    int SPAN_PRIORITY_SHIFT      = 16;
    int SPAN_PRIORITY            = 0x00ff0000;
    int SPAN_USER_SHIFT          = 24;
    int SPAN_USER                = 0xff000000;

    <T> T[] getSpans(int start, int end, Class<T> type);
    int getSpanStart(Object tag);
    int getSpanEnd(Object tag);
    int getSpanFlags(Object tag);
    int nextSpanTransition(int start, int limit, Class<?> type);
}
