package android.provider;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import java.util.Locale;

/**
 * The user's personal dictionary of words the keyboard should not autocorrect.
 *
 * Mac.apk registers no user-dictionary provider, so an insert reaches nothing
 * and a query returns no rows -- the same as a device where the dictionary has
 * never been written. addWord is CLIENT-SIDE ContentValues construction plus a
 * resolver insert, reproduced exactly, so if a provider is ever registered it
 * works with no change here.
 *
 * Source: frameworks/base/core/java/android/provider/UserDictionary.java
 */
public class UserDictionary {

    /** AOSP declares a public no-arg constructor on this contract class. */
    public UserDictionary() { }

    public static final String AUTHORITY = "user_dictionary";

    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    public static class Words implements BaseColumns {

        /** AOSP declares a public no-arg constructor. */
        public Words() { }

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/words");

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.userword";
        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/vnd.google.userword";

        public static final String _ID = BaseColumns._ID;
        public static final String WORD = "word";
        public static final String FREQUENCY = "frequency";
        public static final String LOCALE = "locale";
        public static final String APP_ID = "appid";
        public static final String SHORTCUT = "shortcut";

        public static final String DEFAULT_SORT_ORDER = FREQUENCY + " DESC";

        /** @deprecated AOSP replaced the int locale type by a real Locale in API 16. */
        @Deprecated public static final int LOCALE_TYPE_ALL = 0;
        /** @deprecated see {@link #LOCALE_TYPE_ALL}. */
        @Deprecated public static final int LOCALE_TYPE_CURRENT = 1;

        /** AOSP's frequency clamp. */
        private static final int FREQUENCY_MIN = 0;
        private static final int FREQUENCY_MAX = 255;

        /** @deprecated AOSP replaced this by the (shortcut, Locale) overload in API 16. */
        @Deprecated
        public static void addWord(Context context, String word, int frequency, int localeType) {
            if (localeType != LOCALE_TYPE_ALL && localeType != LOCALE_TYPE_CURRENT) {
                return;
            }
            final Locale locale = localeType == LOCALE_TYPE_CURRENT
                    ? Locale.getDefault() : null;
            addWord(context, word, frequency, null, locale);
        }

        /**
         * AOSP: a null Locale means "all locales". Frequency is clamped to
         * 0..255 before insertion, exactly as AOSP does.
         */
        public static void addWord(Context context, String word, int frequency, String shortcut,
                Locale locale) {
            final ContentResolver resolver = context.getContentResolver();
            if (word == null || word.length() > 48) {
                return;
            }
            frequency = Math.max(FREQUENCY_MIN, Math.min(FREQUENCY_MAX, frequency));

            final int columnCount = (shortcut == null) ? 4 : 5;
            ContentValues values = new ContentValues(columnCount);
            values.put(WORD, word);
            values.put(FREQUENCY, frequency);
            values.put(LOCALE, locale == null ? null : locale.toString());
            values.put(APP_ID, 0);
            if (shortcut != null) {
                values.put(SHORTCUT, shortcut);
            }
            resolver.insert(CONTENT_URI, values);
        }
    }
}
