// android.text.method.DateKeyListener -- for typing a DATE into a text field.
// Was absent entirely (NoClassDefFoundError for any EditText configured with
// one, and for android.widget.DatePicker-adjacent code). Mirrors
// frameworks/base/core/java/android/text/method/DateKeyListener.java.
//
// The locale-aware constructor is the interesting part: AOSP widens the fixed
// CHARACTERS set with whatever SEPARATORS the locale's own yMd / yM / Md
// patterns use, and falls back to CHARACTERS wholesale if any of them cannot
// be handled. When the derived set is not a subset of CHARACTERS the listener
// reports a plain TEXT input type instead of a DATETIME one, because the
// datetime soft keyboard could not produce those characters -- that is the
// mNeedsAdvancedInput flag, and dropping it would leave the user unable to
// type a date on their own locale's keyboard.
package android.text.method;

import android.text.InputType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.TreeSet;

public class DateKeyListener extends NumberKeyListener {

    private static final String[] SKELETONS = { "yMd", "yM", "Md" };
    private static final String SYMBOLS_TO_IGNORE = "yMLd";

    /**
     * AOSP DateKeyListener.CHARACTERS -- the fixed set used when the listener
     * is created with a null locale. Deprecated upstream in favour of
     * getAcceptedChars().
     */
    @Deprecated
    public static final char[] CHARACTERS = new char[] {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '/', '-', '.'
        };

    private final char[] mCharacters;
    private final boolean mNeedsAdvancedInput;

    private static final Object sLock = new Object();
    private static final HashMap<Locale, DateKeyListener> sInstanceCache =
            new HashMap<Locale, DateKeyListener>();

    /** @deprecated Use {@link #DateKeyListener(Locale)} instead. */
    @Deprecated
    public DateKeyListener() {
        this(null);
    }

    public DateKeyListener(Locale locale) {
        final TreeSet<Character> chars = new TreeSet<Character>();
        final boolean success = NumberKeyListener.addDigits(chars, locale)
                && NumberKeyListener.addFormatCharsFromSkeletons(
                        chars, locale, SKELETONS, SYMBOLS_TO_IGNORE);
        if (success) {
            mCharacters = NumberKeyListener.collectionToArray(chars);
            mNeedsAdvancedInput = !containsAll(CHARACTERS, mCharacters);
        } else {
            mCharacters = CHARACTERS;
            mNeedsAdvancedInput = false;
        }
    }

    /** AOSP's ArrayUtils.containsAll(char[], char[]). */
    static boolean containsAll(char[] array, char[] check) {
        for (int i = 0; i < check.length; i++) {
            boolean found = false;
            for (int j = 0; j < array.length; j++) {
                if (array[j] == check[i]) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    @Override
    public int getInputType() {
        if (mNeedsAdvancedInput) {
            return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
        } else {
            return InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE;
        }
    }

    @Override
    protected char[] getAcceptedChars() {
        return mCharacters;
    }

    /** @deprecated Use {@link #getInstance(Locale)} instead. */
    @Deprecated
    public static DateKeyListener getInstance() {
        return getInstance(null);
    }

    public static DateKeyListener getInstance(Locale locale) {
        DateKeyListener instance;
        synchronized (sLock) {
            instance = sInstanceCache.get(locale);
            if (instance == null) {
                instance = new DateKeyListener(locale);
                sInstanceCache.put(locale, instance);
            }
        }
        return instance;
    }
}
