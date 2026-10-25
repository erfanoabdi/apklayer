// android.icu.text.Collator -- ICU4J locale-aware string comparison (a verbatim
// repackage of com.ibm.icu.text.Collator). Published API:
//   developer.android.com/reference/android/icu/text/Collator
//   public abstract class Collator implements Comparator<Object>, Cloneable
//
// Used for locale-correct sorting. The JDK's java.text.Collator implements the
// same Unicode Collation Algorithm with the same strength model, so
// android.icu.text.Collator is a faithful abstract front whose getInstance()
// returns a concrete subclass delegating to java.text.Collator -- no ICU4J.
//
// Strength constants line up 1:1 with java.text.Collator
// (PRIMARY=0, SECONDARY=1, TERTIARY=2, QUATERNARY=3, IDENTICAL=15) and so do
// the decomposition modes (NO_DECOMPOSITION / CANONICAL_DECOMPOSITION).
package android.icu.text;

import android.icu.util.ULocale;

import java.util.Comparator;
import java.util.Locale;

public abstract class Collator implements Comparator<Object>, Cloneable {
    public static final int PRIMARY = 0;
    public static final int SECONDARY = 1;
    public static final int TERTIARY = 2;
    public static final int QUATERNARY = 3;
    public static final int IDENTICAL = 15;

    public static final int NO_DECOMPOSITION = 16;
    public static final int CANONICAL_DECOMPOSITION = 17;
    // ICU deprecated FULL_DECOMPOSITION and aliased it onto IDENTICAL (both 15);
    // there is no distinct "full decomposition" mode. 18 was invented here and does
    // not exist in the platform. android.icu.text.Collator, android-30 android.jar
    // (matches external/icu Collator.java: FULL_DECOMPOSITION = IDENTICAL).
    public static final int FULL_DECOMPOSITION = 15;

    protected Collator() {}

    public static Collator getInstance() {
        return wrap(java.text.Collator.getInstance());
    }
    public static Collator getInstance(Locale locale) {
        return wrap(java.text.Collator.getInstance(
            (locale != null) ? locale : Locale.getDefault()));
    }
    public static Collator getInstance(ULocale locale) {
        return wrap(java.text.Collator.getInstance(ULocale.unwrap(locale)));
    }

    // ICU's Collator.getInstance always builds a RuleBasedCollator
    // (Collator.makeInstance -> new RuleBasedCollator(...)), and callers cast:
    // Hermes' PlatformCollatorICU does
    //   mCollator = (RuleBasedCollator) RuleBasedCollator.getInstance(locale)
    // which threw ClassCastException against the opaque wrapper. The JDK's
    // factory likewise returns a java.text.RuleBasedCollator; wrap that AS our
    // RuleBasedCollator. MACAPK_ICU_PLATFORM_TYPES=0 restores the old wrapper.
    private static Collator wrap(java.text.Collator c) {
        if (com.macapk.icu.HostIcu.PLATFORM_TYPES && c instanceof java.text.RuleBasedCollator) {
            return new RuleBasedCollator((java.text.RuleBasedCollator) c);
        }
        return new JdkCollator(c);
    }

    /**
     * ICU: the "collation" keyword values for a locale (its own tailorings plus
     * root's "standard"/"search"/...). The collators here are the JDK's, which
     * have exactly one tailoring per locale -- the standard one -- and no
     * phonebook / pinyin / search variants, so that is the honest list.
     */
    public static final String[] getKeywordValuesForLocale(String key, ULocale locale, boolean commonlyUsed) {
        return new String[] {"standard"};
    }

    public static final String[] getKeywords() {
        return new String[] {"collation"};
    }

    public abstract int compare(String source, String target);
    public abstract void setStrength(int newStrength);
    public abstract int getStrength();
    public abstract void setDecomposition(int decomposition);
    public abstract int getDecomposition();

    @Override
    public int compare(Object o1, Object o2) {
        return compare(o1.toString(), o2.toString());
    }

    public boolean equals(String source, String target) {
        return compare(source, target) == 0;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    private static final class JdkCollator extends Collator {
        private final java.text.Collator d;
        JdkCollator(java.text.Collator d) { this.d = d; }
        @Override public int compare(String s, String t) { return d.compare(s, t); }
        @Override public void setStrength(int newStrength) { d.setStrength(newStrength); }
        @Override public int getStrength() { return d.getStrength(); }
        @Override public void setDecomposition(int dec) {
            // ICU's decomposition constants (16/17) differ from java.text's
            // (NO_DECOMPOSITION=0, CANONICAL_DECOMPOSITION=1, FULL=2). Map.
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
        @Override public boolean equals(String s, String t) { return d.equals(s, t); }
        @Override public Object clone() { return new JdkCollator((java.text.Collator) d.clone()); }
    }
}
