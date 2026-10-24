// android.text.method.DateTimeKeyListener -- for typing a DATE AND TIME into
// one text field. Was absent entirely. Mirrors
// frameworks/base/core/java/android/text/method/DateTimeKeyListener.java; see
// DateKeyListener for what mNeedsAdvancedInput does.
package android.text.method;

import android.text.InputType;

import java.util.HashMap;
import java.util.Locale;
import java.util.TreeSet;

public class DateTimeKeyListener extends NumberKeyListener {

    private static final String[] SKELETONS = { "yMdhms", "yMdHms", "yMhms", "yMHms",
            "Mdhms", "MdHms" };
    private static final String SKELETON_12HOUR = "yMdhms";
    private static final String SKELETON_24HOUR = "yMdHms";
    private static final String SYMBOLS_TO_IGNORE = "yMLdahHKkms";

    /** AOSP DateTimeKeyListener.CHARACTERS -- the fixed null-locale set. */
    @Deprecated
    public static final char[] CHARACTERS = new char[] {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'm',
            'p', ':', '/', '-', ' '
        };

    private final char[] mCharacters;
    private final boolean mNeedsAdvancedInput;

    private static final Object sLock = new Object();
    private static final HashMap<Locale, DateTimeKeyListener> sInstanceCache =
            new HashMap<Locale, DateTimeKeyListener>();

    /** @deprecated Use {@link #DateTimeKeyListener(Locale)} instead. */
    @Deprecated
    public DateTimeKeyListener() {
        this(null);
    }

    public DateTimeKeyListener(Locale locale) {
        final TreeSet<Character> chars = new TreeSet<Character>();
        final boolean success = NumberKeyListener.addDigits(chars, locale)
                && NumberKeyListener.addAmPmChars(chars, locale)
                && NumberKeyListener.addFormatCharsFromSkeleton(
                        chars, locale, SKELETON_12HOUR, SYMBOLS_TO_IGNORE)
                && NumberKeyListener.addFormatCharsFromSkeleton(
                        chars, locale, SKELETON_24HOUR, SYMBOLS_TO_IGNORE);
        if (success) {
            mCharacters = NumberKeyListener.collectionToArray(chars);
            mNeedsAdvancedInput = !DateKeyListener.containsAll(CHARACTERS, mCharacters);
        } else {
            mCharacters = CHARACTERS;
            mNeedsAdvancedInput = false;
        }
    }

    @Override
    public int getInputType() {
        if (mNeedsAdvancedInput) {
            return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
        } else {
            return InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_NORMAL;
        }
    }

    @Override
    protected char[] getAcceptedChars() {
        return mCharacters;
    }

    /** @deprecated Use {@link #getInstance(Locale)} instead. */
    @Deprecated
    public static DateTimeKeyListener getInstance() {
        return getInstance(null);
    }

    public static DateTimeKeyListener getInstance(Locale locale) {
        DateTimeKeyListener instance;
        synchronized (sLock) {
            instance = sInstanceCache.get(locale);
            if (instance == null) {
                instance = new DateTimeKeyListener(locale);
                sInstanceCache.put(locale, instance);
            }
        }
        return instance;
    }
}
