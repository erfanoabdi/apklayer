// android.icu.text.RuleBasedCollator -- concrete table-driven Collator. ICU4J:
//   developer.android.com/reference/android/icu/text/RuleBasedCollator
//   public class RuleBasedCollator extends Collator
//
// Apps that build a custom collation from a rule string construct this
// directly. The JDK's java.text.RuleBasedCollator parses the same tailoring
// rule syntax, so we delegate. We extend our android.icu.text.Collator front
// and forward to a java.text.RuleBasedCollator built from the rules.
package android.icu.text;

public class RuleBasedCollator extends Collator {
    private final java.text.RuleBasedCollator d;

    public RuleBasedCollator(String rules) throws Exception {
        this.d = new java.text.RuleBasedCollator(rules);
    }

    /** Collator.getInstance: the JDK's own locale collator, wrapped (ICU returns a RuleBasedCollator). */
    RuleBasedCollator(java.text.RuleBasedCollator jdk) {
        this.d = jdk;
    }

    public String getRules() { return d.getRules(); }

    // ---- ICU collation attributes the JDK collator has no switch for ----------
    // ICU (UCOL_NUMERIC_COLLATION, UCOL_ALTERNATE_HANDLING, UCOL_CASE_LEVEL,
    // UCOL_CASE_FIRST) changes the collation ELEMENTS; the JDK's
    // java.text.RuleBasedCollator exposes only strength + decomposition. Each
    // attribute is applied around the JDK comparison with ICU's observable
    // effect (UTS #10 / ICU collation customization docs):
    //   numeric      a run of decimal digits compares by numeric value at the
    //                primary level ("2" < "10"); leading zeros are ignored. Done by
    //                rewriting the digit runs to equal width and comparing ONCE (padNumeric).
    //   shifted      variable characters (whitespace, punctuation: ICU's
    //                default maxVariable=punct) are ignorable at levels 1-3.
    //   caseLevel    an extra level between secondary and tertiary that tells
    //                lower from upper case: at PRIMARY strength "a" != "A".
    //   caseFirst    upper/lower decides which case sorts first at the case
    //                (tertiary) level; "default" is the locale's own order.
    // None set -> the untouched JDK comparison.
    private boolean mNumeric;
    private boolean mShifted;
    private boolean mCaseLevel;
    private int mCaseFirst;          // 0 default (off), 1 lower first, 2 upper first

    public void setNumericCollation(boolean flag) { mNumeric = flag; }
    public boolean getNumericCollation() { return mNumeric; }
    public void setNumericCollationDefault() { mNumeric = false; }

    public void setAlternateHandlingShifted(boolean shifted) { mShifted = shifted; }
    public boolean isAlternateHandlingShifted() { return mShifted; }
    public void setAlternateHandlingDefault() { mShifted = false; }

    public void setCaseLevel(boolean flag) { mCaseLevel = flag; }
    public boolean isCaseLevel() { return mCaseLevel; }
    public void setCaseLevelDefault() { mCaseLevel = false; }

    public void setUpperCaseFirst(boolean upperfirst) { mCaseFirst = upperfirst ? 2 : 0; }
    public boolean isUpperCaseFirst() { return mCaseFirst == 2; }
    public void setLowerCaseFirst(boolean lowerfirst) { mCaseFirst = lowerfirst ? 1 : 0; }
    public boolean isLowerCaseFirst() { return mCaseFirst == 1; }
    public void setCaseFirstDefault() { mCaseFirst = 0; }

    @Override public int compare(String s, String t) {
        if (!mNumeric && !mShifted && !mCaseLevel && mCaseFirst == 0) return d.compare(s, t);
        if (mShifted) {
            s = stripVariable(s);
            t = stripVariable(t);
        }
        if (mNumeric) {
            String[] p = padNumeric(s, t);   // one whole-string compare keeps the ICU level order
            s = p[0];
            t = p[1];
        }
        int r = d.compare(s, t);
        int strength = d.getStrength();
        if (r == 0 && mCaseLevel && strength < java.text.Collator.TERTIARY) {
            r = compareCase(s, t);
        } else if (r != 0 && mCaseFirst == 2 && strength >= java.text.Collator.TERTIARY
                && caseOnlyDifference(s, t)) {
            r = -r;   // the JDK tailorings order lower before upper; upper-first inverts that level
        }
        return r;
    }

    private static boolean isVariable(int cp) {
        if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) return true;
        switch (Character.getType(cp)) {
            case Character.CONNECTOR_PUNCTUATION: case Character.DASH_PUNCTUATION:
            case Character.START_PUNCTUATION: case Character.END_PUNCTUATION:
            case Character.INITIAL_QUOTE_PUNCTUATION: case Character.FINAL_QUOTE_PUNCTUATION:
            case Character.OTHER_PUNCTUATION:
                return true;
            default:
                return false;
        }
    }

    private static String stripVariable(String s) {
        StringBuilder b = new StringBuilder(s.length());
        s.codePoints().filter(cp -> !isVariable(cp)).forEach(b::appendCodePoint);
        return b.toString();
    }

    /**
     * ICU numeric collation (UCOL_NUMERIC_COLLATION): every maximal run of decimal digits
     * collates by its numeric value, as ONE collation element at the primary level, so the
     * levels keep their ICU order -- a later primary difference (10 > 9) beats an earlier
     * tertiary one (a vs A). The JDK collator has no such switch, so the digit runs are
     * rewritten and the WHOLE string is compared once by the JDK collator, which then applies
     * its own level order: each run becomes plain ASCII digits (Character.digit, so Arabic-Indic
     * and fullwidth digits count), leading zeros stripped (one kept), left-padded with '0' to
     * W = the longest significant run in EITHER string. Equal values then yield identical text
     * and a larger value is greater at its first differing digit, exactly the "2" < "10" order.
     * (An earlier version compared chunk by chunk at the configured strength, which let a
     * tertiary case difference in an early chunk beat a primary difference in a later one:
     * compare("a10","A9") answered -1 where ICU answers +1.)
     */
    private static String[] padNumeric(String s, String t) {
        String[] a = digitRuns(s), b = digitRuns(t);
        int w = 1;
        for (int k = 1; k < a.length; k += 2) w = Math.max(w, a[k].length());
        for (int k = 1; k < b.length; k += 2) w = Math.max(w, b[k].length());
        return new String[] { joinRuns(a, w), joinRuns(b, w) };
    }

    /** Split into alternating [text, digits, text, digits, ..., text]; digits as significant ASCII. */
    private static String[] digitRuns(String s) {
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        int i = 0;
        while (true) {
            int j = i;                                   // the text before the next digit run
            while (j < s.length() && !Character.isDigit(s.codePointAt(j))) j += Character.charCount(s.codePointAt(j));
            parts.add(s.substring(i, j));
            if (j >= s.length()) break;
            int e = runEnd(s, j, true);
            StringBuilder x = new StringBuilder();
            for (int p = j; p < e; ) {
                int cp = s.codePointAt(p);
                x.append((char) ('0' + Character.digit(cp, 10)));
                p += Character.charCount(cp);
            }
            int z = 0;
            while (z < x.length() - 1 && x.charAt(z) == '0') z++;
            parts.add(x.substring(z));
            i = e;
        }
        return parts.toArray(new String[0]);
    }

    private static String joinRuns(String[] parts, int w) {
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < parts.length; k++) {
            if ((k & 1) == 1) {
                for (int p = parts[k].length(); p < w; p++) b.append('0');
            }
            b.append(parts[k]);
        }
        return b.toString();
    }

    private static int runEnd(String s, int i, boolean digits) {
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            if (Character.isDigit(cp) != digits) break;
            i += Character.charCount(cp);
        }
        return i;
    }

    /** ICU's case level: lowercase before uppercase, unless upper-first. */
    private int compareCase(String s, String t) {
        int[] a = s.codePoints().filter(Character::isLetter).toArray();
        int[] b = t.codePoints().filter(Character::isLetter).toArray();
        for (int k = 0; k < Math.min(a.length, b.length); k++) {
            boolean ua = Character.isUpperCase(a[k]) || Character.isTitleCase(a[k]);
            boolean ub = Character.isUpperCase(b[k]) || Character.isTitleCase(b[k]);
            if (ua != ub) {
                int r = ua ? 1 : -1;
                return (mCaseFirst == 2) ? -r : r;
            }
        }
        return 0;
    }

    private boolean caseOnlyDifference(String s, String t) {
        java.text.Collator sec = (java.text.Collator) d.clone();
        sec.setStrength(java.text.Collator.SECONDARY);
        return sec.compare(s, t) == 0;
    }

    // ICU strengths are PRIMARY 0, SECONDARY 1, TERTIARY 2, QUATERNARY 3,
    // IDENTICAL 15; java.text's are 0, 1, 2 and IDENTICAL 3. The JDK has no
    // quaternary level, so QUATERNARY maps to its finest level short of
    // IDENTICAL (tertiary) and reads back as TERTIARY.
    @Override public void setStrength(int newStrength) {
        int jdk;
        switch (newStrength) {
            case PRIMARY: jdk = java.text.Collator.PRIMARY; break;
            case SECONDARY: jdk = java.text.Collator.SECONDARY; break;
            case TERTIARY: case QUATERNARY: jdk = java.text.Collator.TERTIARY; break;
            case IDENTICAL: jdk = java.text.Collator.IDENTICAL; break;
            default: throw new IllegalArgumentException("Incorrect comparison level.");
        }
        d.setStrength(jdk);
    }
    @Override public int getStrength() {
        int s = d.getStrength();
        return (s == java.text.Collator.IDENTICAL) ? IDENTICAL : s;
    }
    @Override public void setDecomposition(int dec) {
        int jdk;
        switch (dec) {
            case CANONICAL_DECOMPOSITION: jdk = java.text.Collator.CANONICAL_DECOMPOSITION; break;
            case FULL_DECOMPOSITION: jdk = java.text.Collator.FULL_DECOMPOSITION; break;
            default: jdk = java.text.Collator.NO_DECOMPOSITION; break;
        }
        d.setDecomposition(jdk);
    }
    @Override public int getDecomposition() {
        switch (d.getDecomposition()) {
            case java.text.Collator.CANONICAL_DECOMPOSITION: return CANONICAL_DECOMPOSITION;
            case java.text.Collator.FULL_DECOMPOSITION: return FULL_DECOMPOSITION;
            default: return NO_DECOMPOSITION;
        }
    }
    @Override public boolean equals(String s, String t) { return compare(s, t) == 0; }

    @Override public Object clone() {
        RuleBasedCollator c = new RuleBasedCollator((java.text.RuleBasedCollator) d.clone());
        c.mNumeric = mNumeric;
        c.mShifted = mShifted;
        c.mCaseLevel = mCaseLevel;
        c.mCaseFirst = mCaseFirst;
        return c;
    }
}
