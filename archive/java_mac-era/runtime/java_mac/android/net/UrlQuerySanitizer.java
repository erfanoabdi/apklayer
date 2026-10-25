// android.net.UrlQuerySanitizer
//
// Parses + sanitizes the query portion of a URL into name/value pairs. Pure
// string processing, no hardware, so implemented faithfully against AOSP's
// observable behavior. Mirrors
// frameworks/base/core/java/android/net/UrlQuerySanitizer.java.
package android.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;

public class UrlQuerySanitizer {

    private final HashMap<String, String> mEntries = new HashMap<>();
    private final ArrayList<ParameterValuePair> mEntriesList = new ArrayList<>();
    private boolean mAllowUnregisteredParameters = true;
    private boolean mPreferFirstRepeatedParameter = false;
    private ValueSanitizer mUnregisteredParameterValueSanitizer = new IllegalCharacterValueSanitizer(
            IllegalCharacterValueSanitizer.ALL_OK);
    private final HashMap<String, ValueSanitizer> mSanitizers = new HashMap<>();

    public UrlQuerySanitizer() {}

    public UrlQuerySanitizer(String url) { parseUrl(url); }

    /**
     * AOSP treats a URL with NO '?' as having an EMPTY query, not as a bare
     * query string. This used to parse the whole URL in that case, which
     * silently turned "http://host/a=b" into a parameter -- corrected here to
     * match core/java/android/net/UrlQuerySanitizer.java.
     */
    public void parseUrl(String url) {
        if (url == null) {
            return;
        }
        int queryIndex = url.indexOf('?');
        String query = queryIndex >= 0 ? url.substring(queryIndex + 1) : "";
        parseQuery(query);
    }

    /**
     * AOSP splits on '&' and hands each RAW (still-escaped) name/value to
     * parseEntry. Routing through parseEntry is not cosmetic: parseEntry is
     * protected precisely so a subclass can override it, and an app that does
     * so was previously bypassed entirely.
     */
    public void parseQuery(String query) {
        clear();
        if (query == null || query.isEmpty()) {
            return;
        }
        StringTokenizer tokenizer = new StringTokenizer(query, "&");
        while (tokenizer.hasMoreElements()) {
            String attributeValuePair = tokenizer.nextToken();
            if (attributeValuePair.length() > 0) {
                int assignmentIndex = attributeValuePair.indexOf('=');
                if (assignmentIndex < 0) {
                    // No assignment found, treat as if empty value.
                    parseEntry(attributeValuePair, "");
                } else {
                    parseEntry(attributeValuePair.substring(0, assignmentIndex),
                            attributeValuePair.substring(assignmentIndex + 1));
                }
            }
        }
    }

    /**
     * AOSP unescapes the NAME first, resolves the sanitizer from the unescaped
     * name, then unescapes and sanitizes the value. Overridable.
     */
    protected void parseEntry(String parameter, String value) {
        String unescapedParameter = unescape(parameter);
        ValueSanitizer valueSanitizer = getEffectiveValueSanitizer(unescapedParameter);
        if (valueSanitizer == null) {
            return;
        }
        String unescapedValue = unescape(value);
        String sanitizedValue = valueSanitizer.sanitize(unescapedValue);
        addSanitizedEntry(unescapedParameter, sanitizedValue);
    }

    /**
     * AOSP ALWAYS appends to the ordered list, and only the MAP put is
     * suppressed by preferFirstRepeatedParameter. The previous private helper
     * skipped both, so getParameterList() lost repeated parameters that AOSP
     * keeps. Overridable.
     */
    protected void addSanitizedEntry(String parameter, String value) {
        mEntriesList.add(new ParameterValuePair(parameter, value));
        if (mPreferFirstRepeatedParameter && mEntries.containsKey(parameter)) {
            return;
        }
        mEntries.put(parameter, value);
    }

    public String getValue(String parameter) { return mEntries.get(parameter); }
    public boolean hasParameter(String parameter) { return mEntries.containsKey(parameter); }
    public Set<String> getParameterSet() { return new HashSet<>(mEntries.keySet()); }
    public List<ParameterValuePair> getParameterList() { return mEntriesList; }

    /** protected on AOSP, not public -- matched so an overriding subclass
     *  declaring `protected void clear()` is not narrowing the access. */
    protected void clear() { mEntries.clear(); mEntriesList.clear(); }

    public void setAllowUnregisteredParamaters(boolean allow) {
        mAllowUnregisteredParameters = allow;
    }
    public boolean getAllowUnregisteredParamaters() { return mAllowUnregisteredParameters; }
    public void setPreferFirstRepeatedParameter(boolean prefer) {
        mPreferFirstRepeatedParameter = prefer;
    }
    public boolean getPreferFirstRepeatedParameter() { return mPreferFirstRepeatedParameter; }

    public void registerParameter(String parameter, ValueSanitizer valueSanitizer) {
        if (valueSanitizer == null) mSanitizers.remove(parameter);
        else mSanitizers.put(parameter, valueSanitizer);
    }
    /** AOSP registers ONE sanitizer against several names in one call. */
    public void registerParameters(String[] parameters, ValueSanitizer valueSanitizer) {
        if (parameters == null) {
            return;
        }
        for (String parameter : parameters) {
            mSanitizers.put(parameter, valueSanitizer);
        }
    }

    public ValueSanitizer getValueSanitizer(String parameter) { return mSanitizers.get(parameter); }
    public ValueSanitizer getEffectiveValueSanitizer(String parameter) {
        ValueSanitizer s = getValueSanitizer(parameter);
        if (s == null && mAllowUnregisteredParameters) {
            s = getUnregisteredParameterValueSanitizer();
        }
        return s;
    }
    public ValueSanitizer getUnregisteredParameterValueSanitizer() {
        return mUnregisteredParameterValueSanitizer;
    }
    public void setUnregisteredParameterValueSanitizer(ValueSanitizer s) {
        mUnregisteredParameterValueSanitizer = s;
    }

    /**
     * AOSP does its OWN '+'-and-%XX decoding rather than calling
     * URLDecoder.decode, and the difference is observable: URLDecoder THROWS
     * IllegalArgumentException on a malformed escape ("%zz", or a trailing
     * "%"), while AOSP leaves the offending characters alone and keeps going.
     * A query string from the network is exactly where malformed escapes turn
     * up, so the old URLDecoder call could throw out of parseQuery on input
     * Android parses fine. AOSP's loop is reproduced here, including its
     * "i + 2 < length" bound (which, faithfully, leaves a %XX at the very end
     * of the string undecoded).
     */
    public String unescape(String string) {
        if (string == null) {
            return null;
        }
        if (string.indexOf('+') < 0 && string.indexOf('%') < 0) {
            return string;
        }
        int length = string.length();
        StringBuilder stringBuilder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = string.charAt(i);
            if (c == '+') {
                c = ' ';
            } else if (c == '%' && i + 2 < length) {
                char c1 = string.charAt(i + 1);
                char c2 = string.charAt(i + 2);
                if (isHexDigit(c1) && isHexDigit(c2)) {
                    c = (char) (decodeHexDigit(c1) * 16 + decodeHexDigit(c2));
                    i += 2;
                }
            }
            stringBuilder.append(c);
        }
        return stringBuilder.toString();
    }

    /** AOSP: the digit's value, or -1 when the char is not a hex digit. */
    protected int decodeHexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        } else if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        } else {
            return -1;
        }
    }

    protected boolean isHexDigit(char c) {
        return decodeHexDigit(c) >= 0;
    }

    public interface ValueSanitizer {
        String sanitize(String value);
    }

    public static class ParameterValuePair {
        public final String mParameter;
        public final String mValue;
        public ParameterValuePair(String parameter, String value) {
            mParameter = parameter;
            mValue = value;
        }
    }

    public static class IllegalCharacterValueSanitizer implements ValueSanitizer {
        // Flag bit positions verified against
        // frameworks/base/core/java/android/net/UrlQuerySanitizer.java.
        // These are public static final int fields, so any app compiled
        // against a real android.jar has them inlined (constant-folded) into
        // its own DEX bytecode -- a wrong bit position here silently
        // misinterprets any flag combination such an app hands us, even
        // though the symbolic names line up. (Previously this tree's bit
        // positions were shifted by one and SCRIPT_URL_OK was out of order;
        // fixed to match AOSP exactly.)
        public static final int SPACE_OK = 1 << 0;
        public static final int OTHER_WHITESPACE_OK = 1 << 1;
        public static final int NON_7_BIT_ASCII_OK = 1 << 2;
        public static final int DQUOTE_OK = 1 << 3;
        public static final int SQUOTE_OK = 1 << 4;
        public static final int LT_OK = 1 << 5;
        public static final int GT_OK = 1 << 6;
        public static final int AMP_OK = 1 << 7;
        public static final int PCT_OK = 1 << 8;
        public static final int NUL_OK = 1 << 9;
        public static final int SCRIPT_URL_OK = 1 << 10;
        public static final int ALL_OK = 0x7ff;
        public static final int ALL_WHITESPACE_OK = SPACE_OK | OTHER_WHITESPACE_OK;

        // Common flag combinations, named and valued per AOSP, backing the
        // UrlQuerySanitizer.getXxxLegal() static factories below.
        public static final int ALL_ILLEGAL = 0;
        public static final int ALL_BUT_NUL_LEGAL = ALL_OK & ~NUL_OK;
        public static final int ALL_BUT_WHITESPACE_LEGAL = ALL_OK & ~(ALL_WHITESPACE_OK | NUL_OK);
        public static final int URL_LEGAL = NON_7_BIT_ASCII_OK | SQUOTE_OK | AMP_OK | PCT_OK;
        public static final int URL_AND_SPACE_LEGAL = URL_LEGAL | SPACE_OK;
        public static final int AMP_LEGAL = AMP_OK;
        public static final int AMP_AND_SPACE_LEGAL = AMP_OK | SPACE_OK;
        public static final int SPACE_LEGAL = SPACE_OK;
        public static final int ALL_BUT_NUL_AND_ANGLE_BRACKETS_LEGAL = ALL_OK & ~(NUL_OK | LT_OK | GT_OK);

        private static final String JAVASCRIPT_PREFIX = "javascript:";
        private static final String VBSCRIPT_PREFIX = "vbscript:";
        private static final int MIN_SCRIPT_PREFIX_LENGTH =
                Math.min(JAVASCRIPT_PREFIX.length(), VBSCRIPT_PREFIX.length());

        private final int mFlags;
        public IllegalCharacterValueSanitizer(int flags) { mFlags = flags; }

        // AOSP sanitize(): strip a leading javascript:/vbscript: prefix
        // unless SCRIPT_URL_OK, trim whitespace off both ends unless
        // ALL_WHITESPACE_OK, then replace each illegal character with ' '
        // (if SPACE_OK) or '_'. Returns null for null input, matching AOSP
        // (the ValueSanitizer interface contract callers rely on).
        @Override public String sanitize(String value) {
            if (value == null) return null;
            int length = value.length();
            if ((mFlags & SCRIPT_URL_OK) == 0 && length >= MIN_SCRIPT_PREFIX_LENGTH) {
                String asLower = value.toLowerCase(Locale.ROOT);
                if (asLower.startsWith(JAVASCRIPT_PREFIX) || asLower.startsWith(VBSCRIPT_PREFIX)) {
                    return "";
                }
            }
            if ((mFlags & ALL_WHITESPACE_OK) == 0) {
                value = trimWhitespace(value);
                length = value.length();
            }
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                char c = value.charAt(i);
                if (!characterIsLegal(c)) {
                    c = (mFlags & SPACE_OK) != 0 ? ' ' : '_';
                }
                sb.append(c);
            }
            return sb.toString();
        }

        // Can't use String#trim: it uses a different definition of
        // whitespace than isWhitespace() below (AOSP's own comment).
        private String trimWhitespace(String value) {
            int start = 0;
            int last = value.length() - 1;
            int end = last;
            while (start <= end && isWhitespace(value.charAt(start))) start++;
            while (end >= start && isWhitespace(value.charAt(end))) end--;
            if (start == 0 && end == last) return value;
            return value.substring(start, end + 1);
        }

        private boolean isWhitespace(char c) {
            switch (c) {
                case ' ': case '\t': case '\f': case '\n': case '\r':
                case 11: /* vertical tab */
                    return true;
                default:
                    return false;
            }
        }

        private boolean characterIsLegal(char c) {
            switch (c) {
                case ' ': return (mFlags & SPACE_OK) != 0;
                case '\t': case '\f': case '\n': case '\r':
                case 11: /* vertical tab */
                    return (mFlags & OTHER_WHITESPACE_OK) != 0;
                case '\"': return (mFlags & DQUOTE_OK) != 0;
                case '\'': return (mFlags & SQUOTE_OK) != 0;
                case '<': return (mFlags & LT_OK) != 0;
                case '>': return (mFlags & GT_OK) != 0;
                case '&': return (mFlags & AMP_OK) != 0;
                case '%': return (mFlags & PCT_OK) != 0;
                case '\0': return (mFlags & NUL_OK) != 0;
                default:
                    return (c >= 32 && c < 127) || (c >= 128 && (mFlags & NON_7_BIT_ASCII_OK) != 0);
            }
        }
    }

    // Singleton sanitizers backing the static factories below (AOSP:
    // private static final fields constructed once at class-init).
    private static final ValueSanitizer sAllIllegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.ALL_ILLEGAL);
    private static final ValueSanitizer sAllButNulLegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.ALL_BUT_NUL_LEGAL);
    private static final ValueSanitizer sAllButWhitespaceLegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.ALL_BUT_WHITESPACE_LEGAL);
    private static final ValueSanitizer sUrlLegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.URL_LEGAL);
    private static final ValueSanitizer sUrlAndSpaceLegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.URL_AND_SPACE_LEGAL);
    private static final ValueSanitizer sAmpLegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.AMP_LEGAL);
    private static final ValueSanitizer sAmpAndSpaceLegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.AMP_AND_SPACE_LEGAL);
    private static final ValueSanitizer sSpaceLegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.SPACE_LEGAL);
    private static final ValueSanitizer sAllButNulAndAngleBracketsLegal =
            new IllegalCharacterValueSanitizer(
                    IllegalCharacterValueSanitizer.ALL_BUT_NUL_AND_ANGLE_BRACKETS_LEGAL);

    /** A sanitizer that does not allow any special characters, and does not allow script URLs. */
    public static ValueSanitizer getAllIllegal() { return sAllIllegal; }
    /** A sanitizer that allows everything except Nul ('\0'). Script URLs are allowed. */
    public static ValueSanitizer getAllButNulLegal() { return sAllButNulLegal; }
    /** A sanitizer that allows everything except Nul and whitespace characters. Script URLs are allowed. */
    public static ValueSanitizer getAllButWhitespaceLegal() { return sAllButWhitespaceLegal; }
    /** A sanitizer that allows the characters used by encoded URLs. Does not allow script URLs. */
    public static ValueSanitizer getUrlLegal() { return sUrlLegal; }
    /** Same as getUrlLegal() but also allows spaces. Does not allow script URLs. */
    public static ValueSanitizer getUrlAndSpaceLegal() { return sUrlAndSpaceLegal; }
    /** A sanitizer that allows only ampersand ('&amp;'). Does not allow script URLs. */
    public static ValueSanitizer getAmpLegal() { return sAmpLegal; }
    /** A sanitizer that allows only ampersand ('&amp;') and space (' '). Does not allow script URLs. */
    public static ValueSanitizer getAmpAndSpaceLegal() { return sAmpAndSpaceLegal; }
    /** A sanitizer that allows only space (' '). Does not allow script URLs. */
    public static ValueSanitizer getSpaceLegal() { return sSpaceLegal; }
    /** A sanitizer that allows everything except Nul and angle brackets ('&lt;', '&gt;'). Script URLs are allowed. */
    public static ValueSanitizer getAllButNulAndAngleBracketsLegal() { return sAllButNulAndAngleBracketsLegal; }
}
