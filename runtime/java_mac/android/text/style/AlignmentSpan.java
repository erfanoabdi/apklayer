// android.text.style.AlignmentSpan -- paragraph alignment override.
// AOSP declares the interface plus a concrete Standard implementation; the
// Layout code asks each ParagraphStyle span for its alignment when laying out
// a line, so a missing Standard means HtmlCompat's <p align> / <center> and
// Compose's ParagraphStyle(textAlign=) interop silently lose alignment.
// Source: frameworks/base/core/java/android/text/style/AlignmentSpan.java
package android.text.style;

import android.text.Layout;
import android.text.ParcelableSpan;
import android.os.Parcel;

public interface AlignmentSpan extends ParagraphStyle {

    Layout.Alignment getAlignment();

    class Standard implements AlignmentSpan, ParcelableSpan {
        private final Layout.Alignment mAlignment;

        public Standard(Layout.Alignment align) { mAlignment = align; }

        public Standard(Parcel src) {
            mAlignment = Layout.Alignment.valueOf(src.readString());
        }

        @Override public Layout.Alignment getAlignment() { return mAlignment; }

        @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }

        @Override public int getSpanTypeIdInternal() { return 1; /* ALIGNMENT_SPAN */ }

        @Override public int describeContents() { return 0; }

        @Override public void writeToParcel(Parcel dest, int flags) {
            writeToParcelInternal(dest, flags);
        }

        @Override public void writeToParcelInternal(Parcel dest, int flags) {
            dest.writeString(mAlignment.name());
        }
    }
}
