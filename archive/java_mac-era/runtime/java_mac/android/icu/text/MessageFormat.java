// android.icu.text.MessageFormat -- ICU4J message formatting (repackage of
// com.ibm.icu.text.MessageFormat). Published API:
//   developer.android.com/reference/android/icu/text/MessageFormat
//   public class MessageFormat extends UFormat
//
// Substitutes positional arguments into a pattern ("{0} sent {1} messages").
// The JDK's java.text.MessageFormat parses the same {index,type,style}
// argument syntax for the number/date/time/choice types apps use, so we
// delegate. NOTE: ICU additionally supports `plural`/`select`/`selectordinal`
// argument types that java.text lacks; patterns using those will format the
// raw inner text rather than the plural-selected branch. We document this gap
// rather than mis-format -- PluralRules-backed selection is a separate item.
package android.icu.text;

import android.icu.util.ULocale;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.Locale;

// AOSP: `public class MessageFormat extends UFormat` (UFormat extends
// java.text.Format). android-30 android.jar.
public class MessageFormat extends UFormat {
    private final java.text.MessageFormat d;

    public MessageFormat(String pattern) {
        this.d = new java.text.MessageFormat(pattern);
    }
    public MessageFormat(String pattern, Locale locale) {
        this.d = new java.text.MessageFormat(pattern,
            (locale != null) ? locale : Locale.getDefault());
    }
    public MessageFormat(String pattern, ULocale locale) {
        this.d = new java.text.MessageFormat(pattern, ULocale.unwrap(locale));
    }

    public void applyPattern(String pattern) { d.applyPattern(pattern); }
    public String toPattern() { return d.toPattern(); }
    public void setLocale(Locale locale) { d.setLocale(locale); }

    public final StringBuffer format(Object[] arguments, StringBuffer result, FieldPosition pos) {
        return d.format(arguments, result, pos);
    }
    public final String format(Object[] arguments) {
        return d.format(arguments);
    }

    @Override
    public StringBuffer format(Object arguments, StringBuffer result, FieldPosition pos) {
        return d.format(arguments, result, pos);
    }

    public Object[] parse(String source) throws java.text.ParseException {
        return d.parse(source);
    }
    public Object[] parse(String source, ParsePosition pos) {
        return d.parse(source, pos);
    }
    @Override
    public Object parseObject(String source, ParsePosition pos) {
        return d.parseObject(source, pos);
    }

    // Static convenience (ICU + java.text both expose this).
    public static String format(String pattern, Object... arguments) {
        return java.text.MessageFormat.format(pattern, arguments);
    }

    public static String autoQuoteApostrophe(String pattern) {
        // ICU helper to make a pattern apostrophe-safe; java.text has no public
        // equivalent. Return the pattern unchanged (apps mostly pass already
        // doubled apostrophes), which is the safe identity behavior.
        return pattern;
    }
}
