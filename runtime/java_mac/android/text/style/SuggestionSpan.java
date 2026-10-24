// android.text.style.SuggestionSpan -- AOSP span carrying spell-check /
// autocorrect suggestions and the misspelled/easy-correct underline. AOSP:
//   frameworks/base/core/java/android/text/style/SuggestionSpan.java
//   public class SuggestionSpan extends CharacterStyle implements ParcelableSpan
//
// Editors and IME spell-check set these on a span'd word. We carry the
// suggestion data + flags faithfully and draw the standard misspelled/grammar
// underline color; Mac.apk hosts no suggestion-pick popup, so the action
// constants exist for callers that read them but drive no UI.
package android.text.style;

import android.content.Context;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;

import java.util.Locale;

public class SuggestionSpan extends CharacterStyle implements ParcelableSpan {
    public static final int FLAG_EASY_CORRECT = 0x0001;
    public static final int FLAG_MISSPELLED = 0x0002;
    public static final int FLAG_AUTO_CORRECTION = 0x0004;
    public static final int FLAG_GRAMMAR_ERROR = 0x0008;

    public static final int SUGGESTIONS_MAX_SIZE = 5;

    @Deprecated
    public static final String ACTION_SUGGESTION_PICKED =
        "android.text.style.SUGGESTION_PICKED";
    @Deprecated
    public static final String SUGGESTION_SPAN_PICKED_AFTER = "after";
    @Deprecated
    public static final String SUGGESTION_SPAN_PICKED_BEFORE = "before";
    @Deprecated
    public static final String SUGGESTION_SPAN_PICKED_HASHCODE = "hashcode";

    private final String[] mSuggestions;
    private int mFlags;
    private final String mLocaleStringForCompatibility;
    private final Locale mLocale;

    public SuggestionSpan(Context context, String[] suggestions, int flags) {
        this((Locale) null, suggestions, flags);
    }

    public SuggestionSpan(Locale locale, String[] suggestions, int flags) {
        int N = Math.min(SUGGESTIONS_MAX_SIZE,
            suggestions != null ? suggestions.length : 0);
        mSuggestions = new String[N];
        if (suggestions != null) System.arraycopy(suggestions, 0, mSuggestions, 0, N);
        mFlags = flags;
        mLocale = locale;
        mLocaleStringForCompatibility = (locale == null) ? "" : locale.toString();
    }

    public SuggestionSpan(Context context, Locale locale, String[] suggestions,
                          int flags, Class<?> notificationTargetClass) {
        this(locale, suggestions, flags);
    }

    /**
     * AOSP SuggestionSpan(Parcel src) -- the public un-parcelling constructor.
     *
     * ⚠ AOSP's wire format carries more than this class models: alongside the
     * suggestions, flags and locale it writes a language tag, a hash code and
     * four resolved underline colours/thicknesses (SuggestionSpan.java's own
     * mEasyCorrectUnderlineColor / mMisspelledUnderlineColor / ... fields).
     * Those are derived from a theme we do not resolve, so the payload here is
     * the four fields this class actually has, and writeToParcelInternal below
     * is its exact inverse. In-process the round trip is lossless; the bytes
     * are not interchangeable with a device's, which nothing in Mac.apk can
     * observe (there is no IPC).
     */
    public SuggestionSpan(Parcel src) {
        mSuggestions = src.createStringArray();
        mFlags = src.readInt();
        mLocaleStringForCompatibility = src.readString();
        String tag = src.readString();
        mLocale = (tag == null || tag.isEmpty()) ? null : Locale.forLanguageTag(tag);
    }

    public String[] getSuggestions() { return mSuggestions; }

    public int getFlags() { return mFlags; }
    public void setFlags(int flags) { mFlags = flags; }

    @Deprecated
    public String getLocale() { return mLocaleStringForCompatibility; }
    public Locale getLocaleObject() { return mLocale; }

    public int getUnderlineColor() {
        // AOSP picks a themed color per flag; we use sensible defaults so the
        // misspelled/grammar underline is visible without a theme resolve.
        if ((mFlags & FLAG_GRAMMAR_ERROR) != 0) return 0xFF1A73E8; // blue grammar
        if ((mFlags & FLAG_MISSPELLED) != 0) return 0xFFD32F2F;    // red misspelled
        if ((mFlags & FLAG_AUTO_CORRECTION) != 0) return 0xFF1A73E8;
        return 0x88C0C0C0;
    }

    @Override
    public void updateDrawState(TextPaint tp) {
        final boolean misspelled = (mFlags & FLAG_MISSPELLED) != 0;
        final boolean easy = (mFlags & FLAG_EASY_CORRECT) != 0;
        final boolean autoCorrection = (mFlags & FLAG_AUTO_CORRECTION) != 0;
        final boolean grammarError = (mFlags & FLAG_GRAMMAR_ERROR) != 0;
        if (easy || misspelled || autoCorrection || grammarError) {
            tp.setUnderlineText(true);
        }
    }

    // ---- ParcelableSpan -- AOSP TextUtils.SUGGESTION_SPAN == 19 -----------
    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return 19; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }
    /** The exact inverse of SuggestionSpan(Parcel) above. It used to be an
     *  empty body, so a parcelled SuggestionSpan came back with no suggestions
     *  and no flags -- silently inert -- and there was no CREATOR at all. */
    @Override public void writeToParcelInternal(Parcel dest, int flags) {
        dest.writeStringArray(mSuggestions);
        dest.writeInt(mFlags);
        dest.writeString(mLocaleStringForCompatibility);
        dest.writeString(mLocale == null ? "" : mLocale.toLanguageTag());
    }

    /** AOSP SuggestionSpan.CREATOR. It is declared in the public API surface
     *  (`field public static final android.os.Parcelable.Creator<...> CREATOR`),
     *  so its absence was a NoSuchFieldError for any caller that unparcels one. */
    public static final android.os.Parcelable.Creator<SuggestionSpan> CREATOR =
            new android.os.Parcelable.Creator<SuggestionSpan>() {
        @Override public SuggestionSpan createFromParcel(Parcel in) {
            return new SuggestionSpan(in);
        }
        @Override public SuggestionSpan[] newArray(int size) {
            return new SuggestionSpan[size];
        }
    };
}
