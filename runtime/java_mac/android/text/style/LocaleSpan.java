// android.text.style.LocaleSpan -- marks a text range as being in a specific
// locale, which drives locale-aware font selection, hyphenation and
// text-to-speech pronunciation.
//
// Mac.apk renders every run with one system face and has no per-locale font
// fallback, so applying the locale to the paint is currently a no-op; the span
// still carries and reports its LocaleList faithfully, because that is what
// accessibility services and TTS read, and because dropping the value would
// make getLocale() lie.
// Source: frameworks/base/core/java/android/text/style/LocaleSpan.java
package android.text.style;

import android.os.LocaleList;
import android.text.TextPaint;

import java.util.Locale;

// 2026-08-23 conformance sweep: implements android.text.ParcelableSpan.
// AOSP's writeToParcelInternal is a single call, mLocales.writeToParcel(dest,
// flags) (LocaleSpan.java L93-95, android16-release), which in turn writes
// LocaleList's language-tag string. android.os.LocaleList in THIS tree does
// not implement Parcelable itself (a separate, not-yet-landed gap, and
// LocaleList.java is not a file this fix owns), so this writes the same
// underlying data directly via LocaleList's own
// toLanguageTags()/forLanguageTags(String) round trip instead of a
// writeTypedObject call -- same information, without requiring a change to
// LocaleList.java.
public class LocaleSpan extends MetricAffectingSpan implements android.text.ParcelableSpan {

    private final LocaleList mLocales;

    public LocaleSpan(Locale locale) {
        mLocales = locale == null ? LocaleList.getEmptyLocaleList() : new LocaleList(locale);
    }

    public LocaleSpan(LocaleList locales) {
        if (locales == null) throw new NullPointerException("locales cannot be null");
        mLocales = locales;
    }

    /** AOSP returns the first locale, or null when the list is empty. */
    public Locale getLocale() {
        return mLocales.isEmpty() ? null : mLocales.get(0);
    }

    public LocaleList getLocales() { return mLocales; }

    @Override public void updateDrawState(TextPaint tp) { apply(tp); }

    @Override public void updateMeasureState(TextPaint tp) { apply(tp); }

    private void apply(TextPaint p) {
        // No per-locale face selection exists in this text stack yet; recorded
        // here so the reason is visible rather than looking like an omission.
    }

    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return android.text.TextUtils.LOCALE_SPAN; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }
    @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) {
        dest.writeString(mLocales.toLanguageTags());
    }
    public static final android.os.Parcelable.Creator<LocaleSpan> CREATOR =
            new android.os.Parcelable.Creator<LocaleSpan>() {
        @Override public LocaleSpan createFromParcel(android.os.Parcel in) {
            return new LocaleSpan(LocaleList.forLanguageTags(in.readString()));
        }
        @Override public LocaleSpan[] newArray(int size) { return new LocaleSpan[size]; }
    };
}
