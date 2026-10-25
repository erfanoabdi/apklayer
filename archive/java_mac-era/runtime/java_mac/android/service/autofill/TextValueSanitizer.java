package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalises a text field by regex-replacing it before the platform decides
 * whether it changed (stripping the spaces out of a card number, say).
 *
 * ⚠ sanitize() IS implemented rather than stubbed. It needs nothing from the
 * platform -- it is a Matcher.replaceAll -- and a service that unit-tests its
 * own sanitizers calls it directly. AOSP returns null when the value is not a
 * text value or the regex does not match at all.
 *
 * Source: frameworks/base/core/java/android/service/autofill/TextValueSanitizer.java
 */
public final class TextValueSanitizer implements Parcelable, Sanitizer {

    private final Pattern mRegex;
    private final String mSubst;

    public TextValueSanitizer(Pattern regex, String subst) {
        if (regex == null) throw new NullPointerException("regex");
        if (subst == null) throw new NullPointerException("subst");
        mRegex = regex;
        mSubst = subst;
    }

    /** AOSP's @hide sanitize(AutofillValue); package-visible here, with the
     *  CharSequence half exposed so the logic is reachable and testable. */
    String sanitize(CharSequence text) {
        if (text == null) return null;
        try {
            final Matcher matcher = mRegex.matcher(text);
            if (!matcher.matches()) return null;
            return matcher.replaceAll(mSubst);
        } catch (Exception e) {
            // AOSP swallows a bad substitution and returns null rather than
            // letting a malformed template take the platform down.
            return null;
        }
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeSerializable(mRegex);
        parcel.writeString(mSubst);
    }

    @Override public String toString() { return "TextValueSanitizer[]"; }

    public static final Creator<TextValueSanitizer> CREATOR =
            new Creator<TextValueSanitizer>() {
        @Override public TextValueSanitizer createFromParcel(Parcel parcel) {
            Pattern regex = (Pattern) parcel.readSerializable();
            String subst = parcel.readString();
            return new TextValueSanitizer(regex, subst);
        }
        @Override public TextValueSanitizer[] newArray(int size) {
            return new TextValueSanitizer[size];
        }
    };
}
