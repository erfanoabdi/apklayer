// android.text.style.TabStopSpan -- a paragraph span declaring where a TAB in
// the covered paragraph advances to. Html.fromHtml and any hand-built tabular
// SpannableString use it; the class was absent entirely, so both the interface
// and its Standard implementation were NoClassDefFoundError.
//
// Mirrors frameworks/base/core/java/android/text/style/TabStopSpan.java, which
// is exactly this: a one-method paragraph-style interface plus a value-class
// implementation.
package android.text.style;

public interface TabStopSpan extends ParagraphStyle {

    /** The offset of the tab stop from the paragraph's leading margin, in
     *  pixels. */
    int getTabStop();

    /** AOSP TabStopSpan.Standard -- the value implementation. */
    class Standard implements TabStopSpan {
        private final int mTabOffset;

        /**
         * @param offset the tab stop offset in pixels, from the paragraph's
         *               leading margin. AOSP annotates it @IntRange(from = 0)
         *               but does not itself reject a negative value, so neither
         *               do we -- rejecting one here would fail where a device
         *               succeeds.
         */
        public Standard(int offset) {
            mTabOffset = offset;
        }

        @Override
        public int getTabStop() {
            return mTabOffset;
        }
    }
}
