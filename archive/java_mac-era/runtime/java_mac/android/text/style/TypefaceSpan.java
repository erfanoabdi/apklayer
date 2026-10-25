// android.text.style.TypefaceSpan -- AOSP: swap the typeface of the covered
// range, either by font-family name or by a concrete Typeface.
//
// AOSP ref: frameworks/base/core/java/android/text/style/TypefaceSpan.java
//   public class TypefaceSpan extends MetricAffectingSpan implements ParcelableSpan
//   TypefaceSpan(String family) / TypefaceSpan(Typeface typeface) / getFamily()
//   / getTypeface() / updateDrawState + updateMeasureState -> updateTypeface().
//
// The two constructors differ in a load-bearing way (AOSP class javadoc):
// the Typeface form REPLACES the paint's style outright, while the family
// form KEEPS the paint's current style and only swaps the family, which is
// why applyFontFamily() reads paint.getTypeface().getStyle() first and
// re-applies the bits Typeface.create() could not satisfy as fake-bold /
// skew. Html.fromHtml's <tt> and <font face=...> handling both emit the
// family form, so getting that "keep the existing style" behavior right is
// what makes bold-inside-monospace still look bold.
//
// 2026-08-23 conformance sweep: still NOT implementing ParcelableSpan, but
// the reason has narrowed. TextUtils.TYPEFACE_SPAN now exists (id 13), and
// the sibling spans (StyleSpan, AbsoluteSizeSpan, ...) now implement
// ParcelableSpan too -- see their own file headers. What remains missing is
// android.graphics.LeakyTypefaceStorage, AOSP's static Typeface<->small-int
// identity registry (TypefaceSpan.java L87-121,
// LeakyTypefaceStorage.readTypefaceFromParcel/writeTypefaceToParcel). Our
// android.graphics.Typeface has no mFamily/path field to fall back to for
// the Typeface-constructed form (createFromAsset/createFromFile results
// would lose their source entirely), so a writeToParcel that only handles
// the family-name constructor and silently drops mTypeface for the other
// would be exactly the deceptive lossy case this sweep's brief calls out as
// worse than the current null -- a TypefaceSpan(Typeface) round-tripped
// through it would silently become a no-op span. Building
// LeakyTypefaceStorage is the real fix and is out of proportion for this
// pass; left unimplemented rather than faked.
package android.text.style;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;

public class TypefaceSpan extends MetricAffectingSpan implements android.text.ParcelableSpan {

    private final String mFamily;
    private final Typeface mTypeface;

    /** AOSP: family-name form. The paint's previous style is kept. A null
     *  family leaves the paint untouched. */
    public TypefaceSpan(String family) {
        this(family, null);
    }

    /** AOSP: Typeface form. The typeface's own style replaces the paint's. */
    public TypefaceSpan(Typeface typeface) {
        this(null, typeface);
    }

    private TypefaceSpan(String family, Typeface typeface) {
        mFamily = family;
        mTypeface = typeface;
    }

    /**
     * AOSP TypefaceSpan(Parcel src) (TypefaceSpan.java:86-89):
     *     mFamily = src.readString();
     *     mTypeface = LeakyTypefaceStorage.readTypefaceFromParcel(src);
     */
    public TypefaceSpan(android.os.Parcel src) {
        mFamily = src.readString();
        mTypeface = macapkReadTypeface(src);
    }

    // ---- ParcelableSpan. AOSP TextUtils.TYPEFACE_SPAN == 13. --------------
    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return android.text.TextUtils.TYPEFACE_SPAN; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }
    /** AOSP TypefaceSpan.writeToParcelInternal (TypefaceSpan.java:119-122):
     *  the family string, then the typeface through LeakyTypefaceStorage. */
    @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) {
        dest.writeString(mFamily);
        macapkWriteTypeface(mTypeface, dest);
    }

    public static final android.os.Parcelable.Creator<TypefaceSpan> CREATOR =
            new android.os.Parcelable.Creator<TypefaceSpan>() {
        @Override public TypefaceSpan createFromParcel(android.os.Parcel in) {
            return new TypefaceSpan(in);
        }
        @Override public TypefaceSpan[] newArray(int size) { return new TypefaceSpan[size]; }
    };

    // ---------------------------------------------------------------------
    // The stand-in for android.graphics.LeakyTypefaceStorage (@hide, and in a
    // package this lane does not own). AOSP's implementation is exactly this:
    // an in-process, never-pruned list of Typefaces plus an identity map, with
    // the PARCEL carrying only the index. It is called "leaky" in AOSP's own
    // class name because entries are never removed, and it is by construction
    // IN-PROCESS ONLY -- an index written by one process is meaningless in
    // another, which is a documented property of the real thing, not a
    // shortcut taken here. Mac.apk runs one process, so the round trip is
    // exact for every case that can actually occur.
    //
    // This lives on TypefaceSpan rather than in a new class so the package
    // gains no non-AOSP type; TextAppearanceSpan (same package) uses it too.
    // ---------------------------------------------------------------------
    private static final Object sTypefaceStoreLock = new Object();
    private static final java.util.ArrayList<Typeface> sTypefaceStore =
            new java.util.ArrayList<Typeface>();
    private static final java.util.HashMap<Typeface, Integer> sTypefaceIds =
            new java.util.HashMap<Typeface, Integer>();

    static void macapkWriteTypeface(Typeface typeface, android.os.Parcel parcel) {
        if (typeface == null) {
            parcel.writeInt(-1);
            return;
        }
        final int id;
        synchronized (sTypefaceStoreLock) {
            Integer i = sTypefaceIds.get(typeface);
            if (i != null) {
                id = i.intValue();
            } else {
                id = sTypefaceStore.size();
                sTypefaceStore.add(typeface);
                sTypefaceIds.put(typeface, Integer.valueOf(id));
            }
        }
        parcel.writeInt(id);
    }

    static Typeface macapkReadTypeface(android.os.Parcel parcel) {
        final int id = parcel.readInt();
        if (id < 0) return null;
        synchronized (sTypefaceStoreLock) {
            // Out of range means the index came from somewhere this store does
            // not know about; AOSP would throw, we answer null rather than
            // taking down the read.
            if (id >= sTypefaceStore.size()) return null;
            return sTypefaceStore.get(id);
        }
    }

    public String getFamily() { return mFamily; }

    public Typeface getTypeface() { return mTypeface; }

    @Override public void updateDrawState(TextPaint ds) { updateTypeface(ds); }

    @Override public void updateMeasureState(TextPaint paint) { updateTypeface(paint); }

    private void updateTypeface(Paint paint) {
        if (mTypeface != null) {
            paint.setTypeface(mTypeface);
        } else if (mFamily != null) {
            applyFontFamily(paint, mFamily);
        }
    }

    private void applyFontFamily(Paint paint, String family) {
        int style;
        Typeface old = paint.getTypeface();
        if (old == null) {
            style = Typeface.NORMAL;
        } else {
            style = old.getStyle();
        }
        final Typeface styledTypeface = Typeface.create(family, style);
        // Style bits the resolved family could not provide are synthesized,
        // exactly as AOSP does, so "bold monospace" stays visually bold even
        // when no bold face exists for that family.
        int fake = style & ~styledTypeface.getStyle();

        if ((fake & Typeface.BOLD) != 0) {
            paint.setFakeBoldText(true);
        }
        if ((fake & Typeface.ITALIC) != 0) {
            paint.setTextSkewX(-0.25f);
        }
        paint.setTypeface(styledTypeface);
    }

    @Override
    public String toString() {
        return "TypefaceSpan{"
                + "family='" + getFamily() + '\''
                + ", typeface=" + getTypeface()
                + '}';
    }
}
