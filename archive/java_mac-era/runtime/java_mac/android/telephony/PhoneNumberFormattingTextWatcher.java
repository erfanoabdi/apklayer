package android.telephony;

import android.text.Editable;
import android.text.TextWatcher;

import java.util.Locale;

/**
 * A TextWatcher that formats a phone number as the user types.
 *
 * ⚠ AOSP drives this with libphonenumber's AsYouTypeFormatter, which Mac.apk
 * does not vendor (see PhoneNumberUtils's note on the same subject). So:
 *
 *   - For NANP countries the formatting IS real: it runs
 *     PhoneNumberUtils.formatNanpNumber, which is AOSP's own dash-placement
 *     algorithm reproduced exactly in that class.
 *   - For every other country the watcher installs itself and leaves the text
 *     ALONE. That is the same visible-but-harmless degradation as
 *     PhoneNumberUtils.formatNumber: the number shows unformatted rather than
 *     showing WRONG grouping, and the EditText keeps working normally.
 *
 * AOSP also stops formatting for the rest of an edit session once the user
 * types something the formatter cannot handle (its mStopFormatting flag);
 * that behaviour is reproduced.
 *
 * Source: frameworks/base/telephony/java/android/telephony/PhoneNumberFormattingTextWatcher.java
 */
public class PhoneNumberFormattingTextWatcher implements TextWatcher {

    private final boolean mFormatNanp;

    /** Indicates the change was caused by ourselves, so ignore it. */
    private boolean mSelfChange = false;

    /** AOSP: once the text stops being formattable, stop for this session. */
    private boolean mStopFormatting = false;

    /** @deprecated AOSP: the country is guessed from the default locale. */
    @Deprecated
    public PhoneNumberFormattingTextWatcher() {
        this(Locale.getDefault().getCountry());
    }

    public PhoneNumberFormattingTextWatcher(String countryCode) {
        if (countryCode == null) throw new IllegalArgumentException();
        mFormatNanp = PhoneNumberUtils.getFormatTypeForLocale(new Locale("", countryCode))
                == PhoneNumberUtils.FORMAT_NANP;
    }

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (mSelfChange || mStopFormatting) return;
        // AOSP stops formatting when the user deletes a formatting character.
        if (count > 0 && hasSeparator(s, start, count)) {
            stopFormatting();
        }
    }

    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mSelfChange || mStopFormatting) return;
        // AOSP stops formatting when a separator is inserted by the user.
        if (count > 0 && hasSeparator(s, start, count)) {
            stopFormatting();
        }
    }

    @Override public void afterTextChanged(Editable s) {
        if (mStopFormatting) {
            // AOSP restarts once the field is emptied.
            mStopFormatting = !(s.length() == 0);
            return;
        }
        if (mSelfChange) return;
        if (!mFormatNanp) return;

        mSelfChange = true;
        try {
            PhoneNumberUtils.formatNanpNumber(s);
        } finally {
            mSelfChange = false;
        }
    }

    private void stopFormatting() { mStopFormatting = true; }

    /** AOSP's helper: does the changed span contain a non-dialable character? */
    private static boolean hasSeparator(final CharSequence s, final int start, final int count) {
        for (int i = start; i < start + count; i++) {
            char c = s.charAt(i);
            if (!PhoneNumberUtils.isNonSeparator(c)) return true;
        }
        return false;
    }
}
