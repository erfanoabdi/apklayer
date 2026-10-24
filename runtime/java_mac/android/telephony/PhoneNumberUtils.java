// android.telephony.PhoneNumberUtils -- phone-number string utilities.
//
// Mac.apk has no telephony, but almost nothing in this class DEPENDS on
// telephony: these are pure string algorithms, and apps call them for contact
// matching and display formatting whether or not a radio exists. A stub that
// returns a plausible-looking wrong answer is worse than a missing method,
// because the caller gets no error -- it just merges the wrong two contacts.
// So everything below is the REAL AOSP algorithm.
//
// Source: frameworks/base/telephony/java/android/telephony/PhoneNumberUtils.java
// (android11-release). Constants and helper semantics quoted at their sites.
package android.telephony;

import java.util.regex.Pattern;

public class PhoneNumberUtils {

    /* Special characters, AOSP lines 90-92. */
    public static final char PAUSE = ',';
    public static final char WAIT = ';';
    public static final char WILD = 'N';

    /*
     * AOSP: "global-phone-number = ["+"] 1*( DIGIT / written-sep )" with
     * written-sep = ("-"/"."). AOSP line 118.
     */
    private static final Pattern GLOBAL_PHONE_NUMBER_PATTERN =
            Pattern.compile("[\\+]?[0-9.-]+");

    /**
     * The number of trailing digits that must match for compare() to call two
     * numbers the same.
     *
     * AOSP reads this from the framework resource
     * config_phonenumber_compare_min_match; the AOSP default is 7
     * (frameworks/base/core/res/res/values/config.xml:1070). We have no
     * carrier or region overlay to diverge from, so we pin the AOSP default
     * rather than pretending to consult a config we do not have.
     */
    private static final int MIN_MATCH = 7;

    private static int getMinMatch() {
        return MIN_MATCH;
    }

    /** True if c is ISO-LATIN characters 0-9. AOSP line 122. */
    public static boolean isISODigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** True if c is ISO-LATIN characters 0-9, *, #. AOSP line 128. */
    public static final boolean is12Key(char c) {
        return (c >= '0' && c <= '9') || c == '*' || c == '#';
    }

    /** True if c is ISO-LATIN characters 0-9, *, #, +, WILD. AOSP line 134. */
    public static final boolean isDialable(char c) {
        return (c >= '0' && c <= '9') || c == '*' || c == '#' || c == '+' || c == WILD;
    }

    /** True if c is ISO-LATIN characters 0-9, *, #, + (no WILD). AOSP line 140. */
    public static final boolean isReallyDialable(char c) {
        return (c >= '0' && c <= '9') || c == '*' || c == '#' || c == '+';
    }

    /** True if c is 0-9, *, #, +, WILD, WAIT, PAUSE. AOSP line 146. */
    public static final boolean isNonSeparator(char c) {
        return (c >= '0' && c <= '9') || c == '*' || c == '#' || c == '+'
                || c == WILD || c == WAIT || c == PAUSE;
    }

    /** This and anything right of it is post-dial (PAUSE or WAIT). AOSP line 155. */
    public static final boolean isStartsPostDial(char c) {
        return c == PAUSE || c == WAIT;
    }

    /** AOSP line 411: smallest non-negative of a and b, or -1 if both negative. */
    private static int minPositive(int a, int b) {
        if (a >= 0 && b >= 0) {
            return (a < b) ? a : b;
        } else if (a >= 0) {
            return a;
        } else if (b >= 0) {
            return b;
        } else {
            return -1;
        }
    }

    /**
     * Index of the last character of the network portion; anything after it is
     * a post-dial string. AOSP line 429.
     */
    private static int indexOfLastNetworkChar(String a) {
        int pIndex, wIndex;
        int origLength;
        int trimIndex;

        origLength = a.length();

        pIndex = a.indexOf(PAUSE);
        wIndex = a.indexOf(WAIT);

        trimIndex = minPositive(pIndex, wIndex);

        if (trimIndex < 0) {
            return origLength - 1;
        } else {
            return trimIndex - 1;
        }
    }

    /**
     * Compare phone numbers a and b, return true if they are identical enough
     * for caller-ID purposes.
     *
     * AOSP line 481: dispatches to the LOOSE comparison. The strict matcher is
     * selected by the framework resource config_use_strict_phone_number_
     * comparation, whose AOSP default is false
     * (frameworks/base/core/res/res/values/config.xml:1061), so loose is the
     * behaviour a stock device exhibits and the behaviour we implement.
     */
    public static boolean compare(String a, String b) {
        return compare(a, b, false);
    }

    /**
     * AOSP line 492: the Context overload consults the strict-comparison
     * resource. We carry no carrier/region overlay that could flip it, so the
     * answer is the AOSP default (false -> loose). Taking Context as Object
     * would break callers' link-time signature, so it is typed properly.
     */
    public static boolean compare(android.content.Context context, String a, String b) {
        return compare(a, b, false);
    }

    /** AOSP line 502 (@hide, but kept so the 3-arg form links). */
    public static boolean compare(String a, String b, boolean useStrictComparation) {
        // AOSP would branch to compareStrictly() here. We do not implement the
        // strict matcher: it is only ever selected by a region overlay we do
        // not ship (Japan/Kazakhstan), and answering "loose" is what a stock
        // AOSP build answers. Silently doing something OTHER than one of
        // AOSP's two documented algorithms would be the wrong kind of guess.
        return compareLoosely(a, b);
    }

    /**
     * Compare from right to left, requiring MIN_MATCH dialable characters to
     * match, tolerating trunk ('0') and international ('+' / '00' / '011')
     * prefixes on either side.
     *
     * AOSP line 521. Copied faithfully -- note it is deliberately "similar()",
     * not "equals()": it returns true for numbers that are not textually the
     * same.
     */
    public static boolean compareLoosely(String a, String b) {
        int ia, ib;
        int matched;
        int numNonDialableCharsInA = 0;
        int numNonDialableCharsInB = 0;
        int minMatch = getMinMatch();

        if (a == null || b == null) return a == b;

        if (a.length() == 0 || b.length() == 0) {
            return false;
        }

        ia = indexOfLastNetworkChar(a);
        ib = indexOfLastNetworkChar(b);
        matched = 0;

        while (ia >= 0 && ib >= 0) {
            char ca, cb;
            boolean skipCmp = false;

            ca = a.charAt(ia);

            if (!isDialable(ca)) {
                ia--;
                skipCmp = true;
                numNonDialableCharsInA++;
            }

            cb = b.charAt(ib);

            if (!isDialable(cb)) {
                ib--;
                skipCmp = true;
                numNonDialableCharsInB++;
            }

            if (!skipCmp) {
                if (cb != ca && ca != WILD && cb != WILD) {
                    break;
                }
                ia--; ib--; matched++;
            }
        }

        if (matched < minMatch) {
            int effectiveALen = a.length() - numNonDialableCharsInA;
            int effectiveBLen = b.length() - numNonDialableCharsInB;

            // If the dialable-char counts match but matched < minMatch, treat
            // them as equal (AOSP's example: 404-04 and 40404).
            if (effectiveALen == effectiveBLen && effectiveALen == matched) {
                return true;
            }

            return false;
        }

        // At least one string matched completely.
        if (matched >= minMatch && (ia < 0 || ib < 0)) {
            return true;
        }

        /*
         * What remains must be one of:
         *   - a '+' on one and a '00' or '011' on the other
         *   - a '0' on one and a (+,00)<country code> on the other
         */

        if (matchIntlPrefix(a, ia + 1)
                && matchIntlPrefix(b, ib + 1)) {
            return true;
        }

        if (matchTrunkPrefix(a, ia + 1)
                && matchIntlPrefixAndCC(b, ib + 1)) {
            return true;
        }

        if (matchTrunkPrefix(b, ib + 1)
                && matchIntlPrefixAndCC(a, ia + 1)) {
            return true;
        }

        return false;
    }

    /**
     * All of 'a' up to len must be an international prefix or
     * separators/non-dialing digits. AOSP line 2883.
     * Regex being matched: ([^0-9*#+pwn]\+[^0-9*#+pwn] | [^0-9*#+pwn]0(0|11)[^0-9*#+pwn])$
     */
    private static boolean matchIntlPrefix(String a, int len) {
        int state = 0;
        for (int i = 0; i < len; i++) {
            char c = a.charAt(i);

            switch (state) {
                case 0:
                    if (c == '+') state = 1;
                    else if (c == '0') state = 2;
                    else if (isNonSeparator(c)) return false;
                    break;

                case 2:
                    if (c == '0') state = 3;
                    else if (c == '1') state = 4;
                    else if (isNonSeparator(c)) return false;
                    break;

                case 4:
                    if (c == '1') state = 5;
                    else if (isNonSeparator(c)) return false;
                    break;

                default:
                    if (isNonSeparator(c)) return false;
                    break;
            }
        }

        return state == 1 || state == 3 || state == 5;
    }

    /**
     * All of 'a' up to len must be a (+|00|011)<country code>. AOSP is
     * deliberately loose about the country code: any \d{1,3} matches.
     * AOSP line 2922.
     */
    private static boolean matchIntlPrefixAndCC(String a, int len) {
        int state = 0;
        for (int i = 0; i < len; i++) {
            char c = a.charAt(i);

            switch (state) {
                case 0:
                    if (c == '+') state = 1;
                    else if (c == '0') state = 2;
                    else if (isNonSeparator(c)) return false;
                    break;

                case 2:
                    if (c == '0') state = 3;
                    else if (c == '1') state = 4;
                    else if (isNonSeparator(c)) return false;
                    break;

                case 4:
                    if (c == '1') state = 5;
                    else if (isNonSeparator(c)) return false;
                    break;

                case 1:
                case 3:
                case 5:
                    if (isISODigit(c)) state = 6;
                    else if (isNonSeparator(c)) return false;
                    break;

                case 6:
                case 7:
                    if (isISODigit(c)) state++;
                    else if (isNonSeparator(c)) return false;
                    break;

                default:
                    if (isNonSeparator(c)) return false;
                    break;
            }
        }

        return state == 6 || state == 7 || state == 8;
    }

    /** All of 'a' up to len must match the non-US trunk prefix ('0'). AOSP line 2971. */
    private static boolean matchTrunkPrefix(String a, int len) {
        boolean found;

        found = false;

        for (int i = 0; i < len; i++) {
            char c = a.charAt(i);

            if (c == '0' && !found) {
                found = true;
            } else if (isNonSeparator(c)) {
                return false;
            }
        }

        return found;
    }

    /**
     * Translate [A-Za-z] into keypad digits per ITU E.161 / ISO-IEC 9995-8.
     * AOSP line 2305; the map is AOSP's KEYPAD_MAP (line 2329). Implemented
     * with a switch rather than a SparseIntArray -- same answers, no
     * allocation, and no dependency on our SparseIntArray.
     */
    public static String convertKeypadLettersToDigits(String input) {
        if (input == null) {
            return input;
        }
        int len = input.length();
        if (len == 0) {
            return input;
        }

        char[] out = input.toCharArray();

        for (int i = 0; i < len; i++) {
            out[i] = keypadDigit(out[i]);
        }

        return new String(out);
    }

    /** Chars absent from AOSP's KEYPAD_MAP are returned unchanged. */
    private static char keypadDigit(char c) {
        switch (c) {
            case 'a': case 'b': case 'c':
            case 'A': case 'B': case 'C': return '2';
            case 'd': case 'e': case 'f':
            case 'D': case 'E': case 'F': return '3';
            case 'g': case 'h': case 'i':
            case 'G': case 'H': case 'I': return '4';
            case 'j': case 'k': case 'l':
            case 'J': case 'K': case 'L': return '5';
            case 'm': case 'n': case 'o':
            case 'M': case 'N': case 'O': return '6';
            case 'p': case 'q': case 'r': case 's':
            case 'P': case 'Q': case 'R': case 'S': return '7';
            case 't': case 'u': case 'v':
            case 'T': case 'U': case 'V': return '8';
            case 'w': case 'x': case 'y': case 'z':
            case 'W': case 'X': case 'Y': case 'Z': return '9';
            default: return c;
        }
    }

    /**
     * Strip separators, keeping dialable characters and the post-dial
     * controls. AOSP line 1000-ish (public API since 1).
     *
     * NOTE this is NOT normalizeNumber: it keeps '+' ANYWHERE (not just
     * leading), keeps '*' '#' WILD WAIT PAUSE, and folds non-ASCII digits
     * (fullwidth, Arabic-Indic) to ASCII via Character.digit. The previous
     * Mac.apk version aliased normalizeNumber, which silently dropped those.
     */
    public static String stripSeparators(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        int len = phoneNumber.length();
        StringBuilder ret = new StringBuilder(len);

        for (int i = 0; i < len; i++) {
            char c = phoneNumber.charAt(i);
            // Character.digit() supports ASCII and Unicode digits.
            int digit = Character.digit(c, 10);
            if (digit != -1) {
                ret.append(digit);
            } else if (isNonSeparator(c)) {
                ret.append(c);
            }
        }

        return ret.toString();
    }

    /**
     * AOSP's normalize: digits only, plus a LEADING '+'. Letters cause a
     * keypad conversion and a re-run. Note '*' and '#' are dropped, unlike
     * stripSeparators -- an earlier Mac.apk version kept them, which made
     * normalized numbers compare unequal against real normalized values.
     */
    public static String normalizeNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int len = phoneNumber.length();
        for (int i = 0; i < len; i++) {
            char c = phoneNumber.charAt(i);
            int digit = Character.digit(c, 10);
            if (digit != -1) {
                sb.append(digit);
            } else if (sb.length() == 0 && c == '+') {
                sb.append(c);
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return normalizeNumber(convertKeypadLettersToDigits(phoneNumber));
            }
        }
        return sb.toString();
    }

    /**
     * AOSP matches GLOBAL_PHONE_NUMBER_PATTERN in full. The previous Mac.apk
     * version returned true for ANY non-empty string, so "hello" was a valid
     * global phone number.
     */
    public static boolean isGlobalPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() == 0) {
            return false;
        }
        return GLOBAL_PHONE_NUMBER_PATTERN.matcher(phoneNumber).matches();
    }

    /** AOSP: strips separators after folding keypad letters. */
    public static String extractNetworkPortion(String phoneNumber) {
        if (phoneNumber == null) return null;
        StringBuilder ret = new StringBuilder();
        for (int i = 0, len = phoneNumber.length(); i < len; i++) {
            char c = phoneNumber.charAt(i);
            int digit = Character.digit(c, 10);
            if (digit != -1) {
                ret.append(digit);
            } else if (c == '+') {
                ret.append(c);
            } else if (isDialable(c)) {
                ret.append(c);
            } else if (isStartsPostDial(c)) {
                break;
            }
        }
        return ret.toString();
    }

    /** AOSP: everything right of the first PAUSE/WAIT, separators removed. */
    public static String extractPostDialPortion(String phoneNumber) {
        if (phoneNumber == null) return null;

        int trimIndex = indexOfLastNetworkChar(phoneNumber);
        StringBuilder ret = new StringBuilder();

        for (int i = trimIndex + 1, s = phoneNumber.length(); i < s; i++) {
            char c = phoneNumber.charAt(i);
            if (isNonSeparator(c)) {
                ret.append(c);
            }
        }

        return ret.toString();
    }

    // Display formatting. AOSP routes these through libphonenumber, which we
    // do not vendor; returning the input unchanged is a visible-but-harmless
    // degradation (the number shows unformatted) rather than a wrong answer,
    // which is why these are left as pass-throughs while compare() is real.
    public static String formatNumber(String source) { return source; }
    public static String formatNumber(String phoneNumber, String defaultCountryIso) { return phoneNumber; }
    public static String formatNumber(String phoneNumber, String phoneNumberE164, String defaultCountryIso) { return phoneNumber; }

    public static boolean isWellFormedSmsAddress(String address) {
        if (address == null) return false;
        String networkPortion = extractNetworkPortion(address);
        return (!(networkPortion.equals("+") || networkPortion.length() == 0))
                && isDialable(networkPortion);
    }

    /** AOSP line 1120: every char of the string must be dialable. */
    private static boolean isDialable(String address) {
        for (int i = 0, count = address.length(); i < count; i++) {
            if (!isDialable(address.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // ========================================================================
    // API-36 surface added 2026-08-24. Everything below is either a PURE
    // algorithm reproduced from AOSP (BCD/TOA/reversal/NANP formatting) or an
    // explicitly-stated degradation where AOSP delegates to libphonenumber,
    // which Mac.apk does not vendor. No answer here is invented.
    // Source: frameworks/base/telephony/java/android/telephony/PhoneNumberUtils.java
    // ========================================================================

    public static final int BCD_EXTENDED_TYPE_EF_ADN = 1;
    public static final int BCD_EXTENDED_TYPE_CALLED_PARTY = 2;

    public static final int TOA_International = 0x91;
    public static final int TOA_Unknown = 0x81;

    public static final int FORMAT_UNKNOWN = 0;
    public static final int FORMAT_NANP = 1;
    public static final int FORMAT_JAPAN = 2;

    private static final String BCD_EF_ADN_EXTENDED = "*#,N;";
    private static final String BCD_CALLED_PARTY_EXTENDED = "*#abc";

    private static final String PREFIX_WPS = "*272";
    private static final String PREFIX_WPS_CLIR_ACTIVATE = "*31#*272";
    private static final String PREFIX_WPS_CLIR_DEACTIVATE = "#31#*272";

    private static final String[] NANP_COUNTRIES = new String[] {
        "US", "CA", "AS", "AI", "AG", "BS", "BB", "BM", "VG", "KY", "DM", "DO",
        "GD", "GU", "JM", "PR", "MS", "MP", "KN", "LC", "VC", "TT", "TC", "VI",
    };

    // --- TOA -----------------------------------------------------------------

    /** Makes sure a TOA_International number carries a leading '+'. */
    public static String stringFromStringAndTOA(String s, int TOA) {
        if (s == null) return null;
        if (TOA == TOA_International && s.length() > 0 && s.charAt(0) != '+') {
            return "+" + s;
        }
        return s;
    }

    /** TOA_International if there is a '+' prefix, else TOA_Unknown. */
    public static int toaFromString(String s) {
        if (s != null && s.length() > 0 && s.charAt(0) == '+') {
            return TOA_International;
        }
        return TOA_Unknown;
    }

    // --- Reversal / min-match -------------------------------------------------

    /**
     * AOSP: the network portion, reversed. Used as a DB index for caller-id
     * lookups, so the exact character set matters.
     */
    public static String getStrippedReversed(String phoneNumber) {
        String np = extractNetworkPortionAlt(phoneNumber);
        if (np == null) return null;
        return internalGetStrippedReversed(np, np.length());
    }

    /** AOSP: the last MIN_MATCH digits, reversed. */
    public static String toCallerIDMinMatch(String phoneNumber) {
        String np = extractNetworkPortionAlt(phoneNumber);
        return internalGetStrippedReversed(np, MIN_MATCH);
    }

    private static String internalGetStrippedReversed(String np, int numDigits) {
        if (np == null) return null;
        StringBuilder ret = new StringBuilder(numDigits);
        int length = np.length();
        for (int i = length - 1, s = length; i >= 0 && (s - i) <= numDigits; i--) {
            ret.append(np.charAt(i));
        }
        return ret.toString();
    }

    /**
     * AOSP extractNetworkPortionAlt: like extractNetworkPortion but keeps only
     * ONE '+' and stops at the first post-dial character.
     */
    public static String extractNetworkPortionAlt(String phoneNumber) {
        if (phoneNumber == null) return null;
        int len = phoneNumber.length();
        StringBuilder ret = new StringBuilder(len);
        boolean haveSeenPlus = false;
        for (int i = 0; i < len; i++) {
            char c = phoneNumber.charAt(i);
            if (c == '+') {
                if (haveSeenPlus) continue;
                haveSeenPlus = true;
            }
            if (isDialable(c)) {
                ret.append(c);
            } else if (isStartsPostDial(c)) {
                break;
            }
        }
        return ret.toString();
    }

    // --- BCD (TS 24.008 10.5.4.7) --------------------------------------------

    public static byte[] numberToCalledPartyBCD(String number) {
        return numberToCalledPartyBCD(number, BCD_EXTENDED_TYPE_EF_ADN);
    }

    public static byte[] numberToCalledPartyBCD(String number, int bcdExtType) {
        return numberToCalledPartyBCDHelper(number, false, bcdExtType);
    }

    public static byte[] networkPortionToCalledPartyBCD(String s) {
        String networkPortion = extractNetworkPortion(s);
        return numberToCalledPartyBCDHelper(networkPortion, false, BCD_EXTENDED_TYPE_EF_ADN);
    }

    public static byte[] networkPortionToCalledPartyBCDWithLength(String s) {
        String networkPortion = extractNetworkPortion(s);
        return numberToCalledPartyBCDHelper(networkPortion, true, BCD_EXTENDED_TYPE_EF_ADN);
    }

    private static byte[] numberToCalledPartyBCDHelper(String number, boolean includeLength,
            int bcdExtType) {
        if (number == null) return null;
        int numberLenReal = number.length();
        int numberLenEffective = numberLenReal;
        boolean hasPlus = number.indexOf('+') != -1;
        if (hasPlus) numberLenEffective--;

        if (numberLenEffective == 0) return null;

        int resultLen = (numberLenEffective + 1) / 2;   // 4 bits per digit
        int extraBytes = 1;                             // prepended TOA byte
        if (includeLength) extraBytes++;                // optional length byte
        resultLen += extraBytes;

        byte[] result = new byte[resultLen];

        int digitCount = 0;
        for (int i = 0; i < numberLenReal; i++) {
            char c = number.charAt(i);
            if (c == '+') continue;
            int shift = ((digitCount & 0x01) == 1) ? 4 : 0;
            result[extraBytes + (digitCount >> 1)] |=
                    (byte) ((charToBCD(c, bcdExtType) & 0x0F) << shift);
            digitCount++;
        }

        // 1-fill any trailing odd nibble.
        if ((digitCount & 0x01) == 1) result[extraBytes + (digitCount >> 1)] |= 0xF0;

        int offset = 0;
        if (includeLength) result[offset++] = (byte) (resultLen - 1);
        result[offset] = (byte) (hasPlus ? TOA_International : TOA_Unknown);

        return result;
    }

    public static String calledPartyBCDToString(byte[] bytes, int offset, int length) {
        return calledPartyBCDToString(bytes, offset, length, BCD_EXTENDED_TYPE_EF_ADN);
    }

    public static String calledPartyBCDToString(byte[] bytes, int offset, int length,
            int bcdExtType) {
        boolean prependPlus = false;
        StringBuilder ret = new StringBuilder(1 + length * 2);

        if (length < 2) return "";

        // Only the TON field is taken into consideration.
        if ((bytes[offset] & 0xf0) == (TOA_International & 0xf0)) {
            prependPlus = true;
        }

        internalCalledPartyBCDFragmentToString(ret, bytes, offset + 1, length - 1, bcdExtType);

        if (prependPlus && ret.length() == 0) {
            // If the only thing there is a prepended plus, return "".
            return "";
        }

        if (prependPlus) {
            // An "international number" gets a '+' prepended to the dialing
            // number -- but GSM MMI codes (TS 22.030 6.5.2) also arrive here,
            // and for those the '+' belongs after the leading */# sequence.
            String retString = ret.toString();
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("(^[#*])(.*)([#*])(.*)(#)$").matcher(retString);
            if (m.matches()) {
                if ("".equals(m.group(2))) {
                    // Started with two [#*] and ends with '#': no dialing
                    // number, so just append the '+' (handles "**21#+").
                    ret = new StringBuilder();
                    ret.append(m.group(1)).append(m.group(3)).append(m.group(4))
                            .append(m.group(5)).append("+");
                } else {
                    // Starts with [#*] and ends with '#': group 4 is the
                    // dialing number, so the '+' goes in front of it.
                    ret = new StringBuilder();
                    ret.append(m.group(1)).append(m.group(2)).append(m.group(3))
                            .append("+").append(m.group(4)).append(m.group(5));
                }
            } else {
                ret.insert(0, '+');
            }
        }

        return ret.toString();
    }

    public static String calledPartyBCDFragmentToString(byte[] bytes, int offset, int length) {
        return calledPartyBCDFragmentToString(bytes, offset, length, BCD_EXTENDED_TYPE_EF_ADN);
    }

    public static String calledPartyBCDFragmentToString(byte[] bytes, int offset, int length,
            int bcdExtType) {
        StringBuilder ret = new StringBuilder(length * 2);
        internalCalledPartyBCDFragmentToString(ret, bytes, offset, length, bcdExtType);
        return ret.toString();
    }

    private static void internalCalledPartyBCDFragmentToString(StringBuilder sb, byte[] bytes,
            int offset, int length, int bcdExtType) {
        for (int i = offset; i < length + offset; i++) {
            char c = bcdToChar((byte) (bytes[i] & 0xf), bcdExtType);
            if (c == 0) return;
            sb.append(c);

            byte b = (byte) ((bytes[i] >> 4) & 0xf);
            if (b == 0xf && i + 1 == length + offset) {
                // Ignore the final 0xf filler nibble.
                break;
            }
            c = bcdToChar(b, bcdExtType);
            if (c == 0) return;
            sb.append(c);
        }
    }

    private static int charToBCD(char c, int bcdExtType) {
        if ('0' <= c && c <= '9') return c - '0';
        String extended = null;
        if (BCD_EXTENDED_TYPE_EF_ADN == bcdExtType) extended = BCD_EF_ADN_EXTENDED;
        else if (BCD_EXTENDED_TYPE_CALLED_PARTY == bcdExtType) extended = BCD_CALLED_PARTY_EXTENDED;
        if (extended == null || extended.indexOf(c) == -1) {
            throw new RuntimeException("invalid char for BCD " + c);
        }
        return 0xa + extended.indexOf(c);
    }

    private static char bcdToChar(byte b, int bcdExtType) {
        if (b < 0xa) return (char) ('0' + b);
        String extended = null;
        if (BCD_EXTENDED_TYPE_EF_ADN == bcdExtType) extended = BCD_EF_ADN_EXTENDED;
        else if (BCD_EXTENDED_TYPE_CALLED_PARTY == bcdExtType) extended = BCD_CALLED_PARTY_EXTENDED;
        if (extended == null || b - 0xa >= extended.length()) return 0;
        return extended.charAt(b - 0xa);
    }

    // --- Misc pure helpers ---------------------------------------------------

    /** Normalises non-ASCII (e.g. Devanagari, Arabic-Indic) digits to ASCII. */
    public static String replaceUnicodeDigits(String number) {
        if (number == null) return null;
        StringBuilder normalizedDigits = new StringBuilder(number.length());
        for (char c : number.toCharArray()) {
            int digit = Character.digit(c, 10);
            if (digit != -1) normalizedDigits.append(digit);
            else normalizedDigits.append(c);
        }
        return normalizedDigits.toString();
    }

    /** Wireless Priority Service prefixes; a pure prefix test. */
    public static boolean isWpsCallNumber(String number) {
        return (number != null) && (number.startsWith(PREFIX_WPS)
                || number.startsWith(PREFIX_WPS_CLIR_ACTIVATE)
                || number.startsWith(PREFIX_WPS_CLIR_DEACTIVATE));
    }

    public static int getFormatTypeForLocale(java.util.Locale locale) {
        return getFormatTypeFromCountryCode(locale == null ? null : locale.getCountry());
    }

    private static int getFormatTypeFromCountryCode(String country) {
        if (country == null) return FORMAT_UNKNOWN;
        for (String nanp : NANP_COUNTRIES) {
            if (nanp.compareToIgnoreCase(country) == 0) return FORMAT_NANP;
        }
        if ("jp".compareToIgnoreCase(country) == 0) return FORMAT_JAPAN;
        return FORMAT_UNKNOWN;
    }

    private static final int NANP_STATE_DIGIT = 1;
    private static final int NANP_STATE_PLUS = 2;
    private static final int NANP_STATE_ONE = 3;
    private static final int NANP_STATE_DASH = 4;

    /** AOSP's NANP dash placement, reproduced exactly. Pure string work. */
    public static void formatNanpNumber(android.text.Editable text) {
        int length = text.length();
        if (length > "+1-nnn-nnn-nnnn".length()) {
            // Too long to be formatted.
            return;
        } else if (length <= 5) {
            // A shortcode, or too short to format.
            return;
        }

        CharSequence saved = text.subSequence(0, length);

        removeDashes(text);
        length = text.length();

        int[] dashPositions = new int[3];
        int numDashes = 0;

        int state = NANP_STATE_DIGIT;
        int numDigits = 0;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            switch (c) {
                case '1':
                    if (numDigits == 0 || state == NANP_STATE_PLUS) {
                        state = NANP_STATE_ONE;
                        break;
                    }
                    // fall through
                case '2': case '3': case '4': case '5':
                case '6': case '7': case '8': case '9': case '0':
                    if (state == NANP_STATE_PLUS) {
                        // Only NANP numbers are supported.
                        text.replace(0, length, saved);
                        return;
                    } else if (state == NANP_STATE_ONE) {
                        dashPositions[numDashes++] = i;
                    } else if (state != NANP_STATE_DASH && (numDigits == 3 || numDigits == 6)) {
                        dashPositions[numDashes++] = i;
                    }
                    state = NANP_STATE_DIGIT;
                    numDigits++;
                    break;
                case '-':
                    state = NANP_STATE_DASH;
                    break;
                case '+':
                    if (i == 0) {
                        state = NANP_STATE_PLUS;
                        break;
                    }
                    // fall through
                default:
                    // Unknown character: bail on formatting.
                    text.replace(0, length, saved);
                    return;
            }
        }

        if (numDigits == 7) {
            // With 7 digits we want xxx-xxxx, not xxx-xxx-x.
            numDashes--;
        }

        for (int i = 0; i < numDashes; i++) {
            int pos = dashPositions[i];
            text.replace(pos + i, pos + i, "-");
        }

        int len = text.length();
        while (len > 0) {
            if (text.charAt(len - 1) == '-') {
                text.delete(len - 1, len);
                len--;
            } else {
                break;
            }
        }
    }

    private static void removeDashes(android.text.Editable text) {
        int p = 0;
        while (p < text.length()) {
            if (text.charAt(p) == '-') text.delete(p, p + 1);
            else p++;
        }
    }

    /**
     * AOSP delegates to JapanesePhoneNumberFormatter, a per-prefix table Mac.apk
     * does not ship. Leaving the text unchanged is the same visible-but-harmless
     * degradation as formatNumber() above: the number displays unformatted
     * rather than displaying WRONG grouping.
     */
    public static void formatJapaneseNumber(android.text.Editable text) { }

    // --- libphonenumber-dependent surface ------------------------------------
    // AOSP routes the four methods below through com.google.i18n.phonenumbers,
    // whose per-region metadata (country calling codes, national trunk prefixes,
    // number plans) is what makes "6502530000" + "US" become "+16502530000".
    // Mac.apk does not vendor that library. Guessing the country code and trunk
    // prefix would produce a plausible but WRONG number, so instead:
    //   - a number already in international ('+') form is normalised exactly,
    //     because that case needs no metadata;
    //   - anything else returns null, which is precisely what AOSP returns for a
    //     number libphonenumber cannot parse, and which every caller must
    //     already handle.

    public static String formatNumberToE164(String phoneNumber, String defaultCountryIso) {
        if (phoneNumber == null) return null;
        String stripped = stripSeparators(phoneNumber);
        if (stripped == null || stripped.length() < 2) return null;
        if (stripped.charAt(0) != '+') {
            // Needs libphonenumber metadata to resolve; see the note above.
            return null;
        }
        StringBuilder sb = new StringBuilder("+");
        for (int i = 1; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (c < '0' || c > '9') return null;
            sb.append(c);
        }
        // E.164 allows at most 15 digits after the '+'.
        if (sb.length() < 3 || sb.length() > 16) return null;
        return sb.toString();
    }

    public static String formatNumberToRFC3966(String phoneNumber, String defaultCountryIso) {
        String e164 = formatNumberToE164(phoneNumber, defaultCountryIso);
        return e164 == null ? null : "tel:" + e164;
    }

    /**
     * AOSP uses libphonenumber's isNumberMatch. compare() below is a faithful
     * reproduction of AOSP's own compareLoosely/compareStrictly, which is the
     * same relation for the EXACT_MATCH / NSN_MATCH cases this method reports
     * true for; it differs only for the SHORT_NSN_MATCH tie-break.
     */
    public static boolean areSamePhoneNumber(String number1, String number2,
            String defaultCountryIso) {
        if (number1 == null || number2 == null) return false;
        return compare(number1, number2);
    }

    // --- Emergency / voicemail ------------------------------------------------
    // The list below is AOSP's OWN platform fallback for a device with no SIM
    // (frameworks/opt/telephony .../EmergencyNumberTracker.java:
    //   ((isSimAbsent()) ? "112,911,000,08,110,118,119,999" : "112,911")),
    // which is exactly Mac.apk's state. See TelephonyManager.isEmergencyNumber
    // for the full reasoning; the short version is that answering "112 is not
    // an emergency number" would be both untrue and the dangerous direction.

    /** @deprecated AOSP replaced this by TelephonyManager.isEmergencyNumber in API 29. */
    @Deprecated
    public static boolean isEmergencyNumber(String number) {
        if (number == null) return false;
        String stripped = extractNetworkPortionAlt(number);
        for (String ecc : new String[] { "112", "911", "000", "08", "110", "118", "119", "999" }) {
            if (ecc.equals(stripped)) return true;
        }
        return false;
    }

    /** @deprecated AOSP replaced this by TelephonyManager.isEmergencyNumber in API 29. */
    @Deprecated
    public static boolean isLocalEmergencyNumber(android.content.Context context, String number) {
        return isEmergencyNumber(number);
    }

    /**
     * No SIM and no voicemail service, so no voicemail number is configured and
     * nothing can match it. AOSP's own body returns false the moment
     * TelephonyManager.getVoiceMailNumber() is empty.
     *
     * @deprecated AOSP deprecated this in API 29.
     */
    @Deprecated
    public static boolean isVoiceMailNumber(String number) { return false; }

    /**
     * AOSP resolves tel:/sip: URIs directly and everything else through the
     * Contacts provider. Mac.apk deliberately registers no Contacts provider,
     * so the query path yields nothing and null is returned -- which is also
     * AOSP's answer when the query matches no row.
     */
    public static String getNumberFromIntent(android.content.Intent intent,
            android.content.Context context) {
        if (intent == null) return null;
        android.net.Uri uri = intent.getData();
        if (uri == null) return null;
        String scheme = uri.getScheme();
        if (scheme == null) return null;
        if (scheme.equals("tel") || scheme.equals("sip")) {
            return uri.getSchemeSpecificPart();
        }
        return null;
    }

    // --- TTS -----------------------------------------------------------------
    // AOSP parses with libphonenumber first so TalkBack can read a country code
    // separately. Without it we take AOSP's OWN fallback branch -- the one it
    // uses for any number libphonenumber cannot parse -- which splits the string
    // at non-dialable characters. That is a real, working TTS span, just without
    // the country-code split.

    public static android.text.style.TtsSpan createTtsSpan(String phoneNumberString) {
        if (phoneNumberString == null) return null;
        return new android.text.style.TtsSpan.TelephoneBuilder()
                .setNumberParts(splitAtNonNumerics(phoneNumberString))
                .build();
    }

    public static CharSequence createTtsSpannable(CharSequence phoneNumber) {
        if (phoneNumber == null) return null;
        android.text.Spannable spannable =
                android.text.Spannable.Factory.getInstance().newSpannable(phoneNumber);
        addTtsSpan(spannable, 0, spannable.length());
        return spannable;
    }

    public static void addTtsSpan(android.text.Spannable s, int start, int endExclusive) {
        s.setSpan(createTtsSpan(s.subSequence(start, endExclusive).toString()),
                start, endExclusive, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static String splitAtNonNumerics(CharSequence number) {
        StringBuilder sb = new StringBuilder(number.length());
        for (int i = 0; i < number.length(); i++) {
            sb.append(is12Key(number.charAt(i)) ? number.charAt(i) : " ");
        }
        // AOSP: leading/trailing or repeated spaces make the TTS span
        // non-functional in TalkBack, so they must go.
        return sb.toString().replaceAll(" +", " ").trim();
    }
}
