// android.icu.util.ULocale -- ICU4J's locale class (a richer superset of
// java.util.Locale). Published API:
//   developer.android.com/reference/android/icu/util/ULocale
//   final class ULocale implements Serializable, Comparable<ULocale>
//
// On Mac.apk every locale-aware operation ultimately resolves through the
// JDK's java.util.Locale, so ULocale is an immutable wrapper that round-trips
// to java.util.Locale. java.util.Locale already models everything ICU's
// locale ID carries that a formatter can act on: BCP-47 language / script /
// region / variant AND the -u- extension (the JDK's Locale.Builder and
// extension handling were contributed from ICU's ULocale.Builder, by the same
// authors, and keep its grammar). What the JDK does NOT have is ICU's KEYWORD
// view of those extensions: ICU spells "-u-ca-gregory" as the legacy keyword
// "calendar=gregorian" (ICU's ID form "en_US@calendar=gregorian"), and that
// legacy spelling is what getKeywords()/getKeywordValue() return. Hermes' Intl
// (com.facebook.hermes.intl.LocaleObjectICU / UnicodeExtensionKeys) depends on
// exactly that: it asks getKeywordValue("calendar"), maps "colnumeric" back to
// "kn", and aliases the legacy value "gregorian" back to "gregory". So the
// keyword view below is derived from the Locale's -u- keywords through ICU's
// own key/type tables (icu4c/source/data/misc/keyTypeData.txt, keyMap +
// typeMap; quoted in KEY_* / TYPE_* below).
package android.icu.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class ULocale implements java.io.Serializable, Comparable<ULocale> {
    private static final long serialVersionUID = 1L;

    public static final ULocale ROOT = new ULocale(Locale.ROOT);
    public static final ULocale ENGLISH = new ULocale(Locale.ENGLISH);
    public static final ULocale FRENCH = new ULocale(Locale.FRENCH);
    public static final ULocale GERMAN = new ULocale(Locale.GERMAN);
    public static final ULocale ITALIAN = new ULocale(Locale.ITALIAN);
    public static final ULocale JAPANESE = new ULocale(Locale.JAPANESE);
    public static final ULocale KOREAN = new ULocale(Locale.KOREAN);
    public static final ULocale CHINESE = new ULocale(Locale.CHINESE);
    public static final ULocale SIMPLIFIED_CHINESE = new ULocale(Locale.forLanguageTag("zh-Hans"));
    public static final ULocale TRADITIONAL_CHINESE = new ULocale(Locale.forLanguageTag("zh-Hant"));
    public static final ULocale US = new ULocale(Locale.US);
    public static final ULocale UK = new ULocale(Locale.UK);
    public static final ULocale CANADA = new ULocale(Locale.CANADA);
    public static final ULocale CANADA_FRENCH = new ULocale(Locale.CANADA_FRENCH);
    public static final ULocale FRANCE = new ULocale(Locale.FRANCE);
    public static final ULocale GERMANY = new ULocale(Locale.GERMANY);
    public static final ULocale ITALY = new ULocale(Locale.ITALY);
    public static final ULocale JAPAN = new ULocale(Locale.JAPAN);
    public static final ULocale KOREA = new ULocale(Locale.KOREA);
    public static final ULocale CHINA = new ULocale(Locale.CHINA);
    public static final ULocale PRC = CHINA;
    public static final ULocale TAIWAN = new ULocale(Locale.TAIWAN);

    /** ICU's locale categories (ULocale.Category), mirroring java.util.Locale.Category. */
    public enum Category {
        DISPLAY,
        FORMAT
    }

    private final Locale mLocale;

    /**
     * ICU locale ID ("en_US", "zh_Hant_TW", "en_US@calendar=japanese").
     * Without keywords the pre-existing path is kept unchanged (it also accepts
     * a BCP-47 tag, which callers pass in practice). The '@' keyword part is
     * ICU-only syntax: each legacy keyword is turned into the -u- keyword it
     * stands for (or the extension, for a single-letter key).
     */
    public ULocale(String localeID) {
        Locale parsed = null;
        if (localeID != null) {
            int at = localeID.indexOf('@');
            if (at >= 0) parsed = parseKeywords(localeID.substring(0, at), localeID.substring(at + 1));
        }
        this.mLocale = (parsed != null) ? parsed
                : Locale.forLanguageTag(String.valueOf(localeID).replace('_', '-'));
    }

    public ULocale(String a, String b) {
        this.mLocale = new Locale(a, b);
    }

    public ULocale(String a, String b, String c) {
        this.mLocale = new Locale(a, b, c);
    }

    private ULocale(Locale locale) {
        this.mLocale = (locale != null) ? locale : Locale.getDefault();
    }

    public static ULocale getDefault() {
        return new ULocale(Locale.getDefault());
    }

    /** ICU: the default locale for a category (java.util.Locale's, as on Android). */
    public static ULocale getDefault(Category category) {
        return new ULocale(Locale.getDefault(
                category == Category.DISPLAY ? Locale.Category.DISPLAY : Locale.Category.FORMAT));
    }

    public static ULocale forLocale(Locale loc) {
        return (loc == null) ? null : new ULocale(loc);
    }

    /** ICU: a ULocale for a BCP-47 tag; ill-formed subtags are dropped, as java.util.Locale does. */
    public static ULocale forLanguageTag(String languageTag) {
        return new ULocale(Locale.forLanguageTag(languageTag));
    }

    public Locale toLocale() {
        return mLocale;
    }

    private static volatile ULocale[] sAvailable;

    /**
     * ICU returns the locales its data covers. Here the data is the JDK's CLDR
     * provider, so the answer is java.util.Locale.getAvailableLocales() minus
     * ROOT and the JDK's three compatibility entries that carry an extension
     * (ja_JP_JP_#u-ca-japanese, th_TH_TH_#u-nu-thai): ICU's list never holds
     * keywords. Answering with the host's set keeps "available" equal to "what
     * the formatters here can honour".
     */
    public static ULocale[] getAvailableLocales() {
        ULocale[] a = sAvailable;
        if (a == null) {
            List<ULocale> out = new ArrayList<>();
            for (Locale l : Locale.getAvailableLocales()) {
                if (l.equals(Locale.ROOT) || l.hasExtensions()) continue;
                out.add(new ULocale(l));
            }
            a = out.toArray(new ULocale[0]);
            sAvailable = a;
        }
        return a.clone();
    }

    /**
     * ICU 65+ answers this with its CLDR LocaleMatcher (language distance). The
     * JDK ships no language-distance data; its matcher is RFC 4647 "lookup"
     * (java.util.Locale.lookup: truncate the desired tag from the end until it
     * names a supported locale). Desired locales are tried in priority order,
     * as ICU does; the result is the SUPPORTED object, null when nothing
     * matches, and fallback[0] is false only for an exact match -- the same
     * contract as ICU's (Result.getDesiredIndex() < 0 -> null).
     */
    public static ULocale acceptLanguage(ULocale[] acceptLanguageList, ULocale[] availableLocales,
                                         boolean[] fallback) {
        if (fallback != null) fallback[0] = true;
        if (acceptLanguageList == null || availableLocales == null) return null;
        List<Locale> supported = new ArrayList<>();
        for (ULocale u : availableLocales) if (u != null) supported.add(u.mLocale);
        for (ULocale desired : acceptLanguageList) {
            if (desired == null) continue;
            String tag = desired.mLocale.stripExtensions().toLanguageTag();
            Locale hit;
            try {
                hit = Locale.lookup(Locale.LanguageRange.parse(tag), supported);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (hit == null) continue;
            for (ULocale u : availableLocales) {
                if (u != null && u.mLocale.equals(hit)) {
                    if (fallback != null && desired.equals(u)) fallback[0] = false;
                    return u;
                }
            }
        }
        return null;
    }

    public static ULocale acceptLanguage(ULocale[] acceptLanguageList, boolean[] fallback) {
        return acceptLanguage(acceptLanguageList, getAvailableLocales(), fallback);
    }

    // ---- ICU ID view -------------------------------------------------------

    /**
     * ICU: -u-va-posix on a locale with no variant of its own. ULocale.getInstance appends "_POSIX" to the
     * ID and drops the va keyword; the ID then normalises ("en_POSIX" -> "en__POSIX"), which is the
     * language[_Script][_REGION][_VARIANT] spelling getBaseName builds with variant POSIX.
     */
    private boolean isVaPosix() {
        return mLocale.getVariant().isEmpty() && "posix".equalsIgnoreCase(mLocale.getUnicodeLocaleType("va"));
    }

    /** ICU base name: language[_Script][_REGION][_VARIANT], e.g. "zh_Hant_TW", "en__POSIX". */
    public String getBaseName() {
        StringBuilder sb = new StringBuilder(mLocale.getLanguage());
        String script = mLocale.getScript();
        String region = mLocale.getCountry();
        String variant = isVaPosix() ? "POSIX" : mLocale.getVariant();   // ICU: -u-va-posix IS the POSIX variant
        if (!script.isEmpty()) sb.append('_').append(script);
        if (!region.isEmpty()) sb.append('_').append(region);
        if (!variant.isEmpty()) {
            if (region.isEmpty()) sb.append('_');
            sb.append('_').append(variant.toUpperCase(Locale.ROOT));
        }
        return sb.toString();
    }

    /** ICU locale ID: base name plus "@key=value;key=value" (legacy keywords, sorted). */
    public String getName() {
        Map<String, String> kw = keywordMap();
        if (kw.isEmpty()) return getBaseName();
        StringBuilder sb = new StringBuilder(getBaseName()).append('@');
        boolean first = true;
        for (Map.Entry<String, String> e : kw.entrySet()) {
            if (!first) sb.append(';');
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /** ICU: an iterator over the LEGACY keyword names, sorted; null when there are none. */
    public Iterator<String> getKeywords() {
        Map<String, String> kw = keywordMap();
        return kw.isEmpty() ? null : Collections.unmodifiableSet(kw.keySet()).iterator();
    }

    /** ICU: the value for a LEGACY keyword name ("calendar", "numbers", "colnumeric", ...). */
    public String getKeywordValue(String keywordName) {
        if (keywordName == null) return null;
        return keywordMap().get(keywordName.trim().toLowerCase(Locale.ROOT));
    }

    /** ICU: a new locale with the keyword set (null value removes it). Legacy or BCP key accepted. */
    public ULocale setKeywordValue(String keyword, String value) {
        if (keyword == null) return this;
        String k = keyword.trim().toLowerCase(Locale.ROOT);
        Locale.Builder b = new Locale.Builder().setLocale(mLocale);
        try {
            if (k.length() == 1) {
                b.setExtension(k.charAt(0), value);
            } else if ("attribute".equals(k)) {
                for (String a : mLocale.getUnicodeLocaleAttributes()) b.removeUnicodeLocaleAttribute(a);
                if (value != null) for (String a : value.split("[-_]")) b.addUnicodeLocaleAttribute(a);
            } else {
                String bcpKey = toUnicodeLocaleKey(k);
                if (bcpKey == null) return this;   // not representable on java.util.Locale
                b.setUnicodeLocaleKeyword(bcpKey, value == null ? null : toUnicodeLocaleType(k, value));
            }
        } catch (java.util.IllformedLocaleException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        return new ULocale(b.build());
    }

    /** The -u- keywords (legacy spelling), the unicode attributes and the other extensions. */
    private Map<String, String> keywordMap() {
        TreeMap<String, String> m = new TreeMap<>();
        if (!mLocale.hasExtensions()) return m;
        boolean vaPosix = isVaPosix();
        for (String key : mLocale.getUnicodeLocaleKeys()) {
            String bt = mLocale.getUnicodeLocaleType(key);
            // ICU4J ULocale.getInstance(BaseLocale, LocaleExtensions) (icu4j/main/core/.../ULocale.java
            // ~4103): toLegacyType(bcpKey, bcpType.length() == 0 ? "yes" : bcpType) -- "use "yes" as the
            // value of typeless keywords". en-u-kn is colnumeric=yes, never colnumeric="".
            if (bt == null || bt.isEmpty()) bt = "yes";
            // same block: u-va-posix is a VARIANT, not a keyword -- it adds "_POSIX" to the ID (getBaseName)
            if (vaPosix && "va".equals(key)) continue;
            m.put(toLegacyKey(key), toLegacyType(key, bt));
        }
        if (!mLocale.getUnicodeLocaleAttributes().isEmpty()) {
            m.put("attribute", String.join("-", new java.util.TreeSet<>(mLocale.getUnicodeLocaleAttributes())));
        }
        for (Character c : mLocale.getExtensionKeys()) {
            if (c == Locale.UNICODE_LOCALE_EXTENSION) continue;
            m.put(String.valueOf(c), mLocale.getExtension(c));
        }
        return m;
    }

    private static Locale parseKeywords(String base, String keywords) {
        try {
            Locale.Builder b = new Locale.Builder()
                    .setLocale(Locale.forLanguageTag(base.replace('_', '-')));
            for (String kv : keywords.split(";")) {
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                String k = kv.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String v = kv.substring(eq + 1).trim();
                if (k.length() == 1) {
                    b.setExtension(k.charAt(0), v);
                } else {
                    String bcpKey = toUnicodeLocaleKey(k);
                    if (bcpKey != null) b.setUnicodeLocaleKeyword(bcpKey, toUnicodeLocaleType(k, v));
                }
            }
            return b.build();
        } catch (RuntimeException e) {
            return null;   // keep the pre-existing parse
        }
    }

    // ---- ICU key/type tables -------------------------------------------------
    // icu4c/source/data/misc/keyTypeData.txt (unicode-org/icu main), keyMap{}:
    //   calendar{"ca"} colalternate{"ka"} colbackwards{"kb"} colcasefirst{"kf"}
    //   colcaselevel{"kc"} colhiraganaquaternary{"kh"} collation{"co"}
    //   colnormalization{"kk"} colnumeric{"kn"} colreorder{"kr"} colstrength{"ks"}
    //   currency{"cu"} hours{"hc"} measure{"ms"} numbers{"nu"} timezone{"tz"}
    //   variabletop{"vt"}; every other key maps to itself (cf{""}, d0{""}, ...).
    private static final String[][] KEYS = {
        {"calendar", "ca"}, {"colalternate", "ka"}, {"colbackwards", "kb"},
        {"colcasefirst", "kf"}, {"colcaselevel", "kc"}, {"colhiraganaquaternary", "kh"},
        {"collation", "co"}, {"colnormalization", "kk"}, {"colnumeric", "kn"},
        {"colreorder", "kr"}, {"colstrength", "ks"}, {"currency", "cu"}, {"hours", "hc"},
        {"measure", "ms"}, {"numbers", "nu"}, {"timezone", "tz"}, {"variabletop", "vt"},
    };
    // keyTypeData.txt typeMap{}: the entries whose BCP type differs from the legacy one.
    //   calendar: ethiopic-amete-alem{"ethioaa"} gregorian{"gregory"}
    //   colalternate: non-ignorable{"noignore"}
    //   colbackwards/colcaselevel/colhiraganaquaternary/colnormalization/colnumeric:
    //       no{"false"} yes{"true"};  colcasefirst: no{"false"}
    //   collation: dictionary{"dict"} gb2312han{"gb2312"} phonebook{"phonebk"} traditional{"trad"}
    //   colstrength: identical{"identic"} primary{"level1"} quaternary{"level4"}
    //       secondary{"level2"} tertiary{"level3"}
    //   measure: imperial{"uksystem"};  numbers: traditional{"traditio"}
    // (timezone types map Olson IDs to BCP ids through timezoneTypes.txt; that table is
    // not carried, so tz values pass through unchanged.)
    private static final String[][] TYPES = {
        {"calendar", "ethiopic-amete-alem", "ethioaa"}, {"calendar", "gregorian", "gregory"},
        {"colalternate", "non-ignorable", "noignore"},
        {"colbackwards", "no", "false"}, {"colbackwards", "yes", "true"},
        {"colcaselevel", "no", "false"}, {"colcaselevel", "yes", "true"},
        {"colhiraganaquaternary", "no", "false"}, {"colhiraganaquaternary", "yes", "true"},
        {"colnormalization", "no", "false"}, {"colnormalization", "yes", "true"},
        {"colnumeric", "no", "false"}, {"colnumeric", "yes", "true"},
        {"colcasefirst", "no", "false"},
        {"collation", "dictionary", "dict"}, {"collation", "gb2312han", "gb2312"},
        {"collation", "phonebook", "phonebk"}, {"collation", "traditional", "trad"},
        {"colstrength", "identical", "identic"}, {"colstrength", "primary", "level1"},
        {"colstrength", "quaternary", "level4"}, {"colstrength", "secondary", "level2"},
        {"colstrength", "tertiary", "level3"},
        {"measure", "imperial", "uksystem"},
        {"numbers", "traditional", "traditio"},
    };

    private static boolean wellFormedKey(String k) {
        return k != null && !k.isEmpty() && k.chars().allMatch(c -> Character.isLetterOrDigit(c));
    }

    /** ICU: legacy keyword for a BCP or legacy key ("ca" -> "calendar"); unknown well-formed keys lowercased. */
    public static String toLegacyKey(String keyword) {
        if (keyword == null) return null;
        String k = keyword.toLowerCase(Locale.ROOT);
        for (String[] e : KEYS) if (e[0].equals(k) || e[1].equals(k)) return e[0];
        return wellFormedKey(k) ? k : null;
    }

    /** ICU: BCP-47 key for a legacy or BCP key ("calendar" -> "ca"); null when not a -u- key. */
    public static String toUnicodeLocaleKey(String keyword) {
        if (keyword == null) return null;
        String k = keyword.toLowerCase(Locale.ROOT);
        for (String[] e : KEYS) if (e[0].equals(k) || e[1].equals(k)) return e[1];
        return (k.length() == 2 && wellFormedKey(k)) ? k : null;
    }

    /** ICU: legacy type ("ca","gregory" -> "gregorian"); unknown well-formed types lowercased. */
    public static String toLegacyType(String keyword, String value) {
        if (value == null) return null;
        String key = toLegacyKey(keyword);
        String v = value.toLowerCase(Locale.ROOT);
        for (String[] e : TYPES) if (e[0].equals(key) && (e[2].equals(v) || e[1].equals(v))) return e[1];
        return v;
    }

    /** ICU: BCP type ("calendar","gregorian" -> "gregory"); unknown well-formed types lowercased. */
    public static String toUnicodeLocaleType(String keyword, String value) {
        if (value == null) return null;
        String key = toLegacyKey(keyword);
        String v = value.toLowerCase(Locale.ROOT);
        for (String[] e : TYPES) if (e[0].equals(key) && (e[1].equals(v) || e[2].equals(v))) return e[2];
        return v;
    }

    // ---- plain accessors -----------------------------------------------------

    public String getLanguage() { return mLocale.getLanguage(); }
    public String getCountry() { return mLocale.getCountry(); }
    public String getVariant() { return mLocale.getVariant(); }
    public String getScript() { return mLocale.getScript(); }

    public String getUnicodeLocaleType(String key) { return mLocale.getUnicodeLocaleType(key); }
    public java.util.Set<String> getUnicodeLocaleKeys() { return mLocale.getUnicodeLocaleKeys(); }
    public java.util.Set<String> getUnicodeLocaleAttributes() { return mLocale.getUnicodeLocaleAttributes(); }
    public String getExtension(char key) { return mLocale.getExtension(key); }
    public java.util.Set<Character> getExtensionKeys() { return mLocale.getExtensionKeys(); }

    public String getDisplayName() { return mLocale.getDisplayName(); }
    public String getDisplayName(ULocale displayLocale) { return mLocale.getDisplayName(unwrap(displayLocale)); }
    public String getDisplayLanguage() { return mLocale.getDisplayLanguage(); }
    public String getDisplayLanguage(ULocale displayLocale) { return mLocale.getDisplayLanguage(unwrap(displayLocale)); }
    public String getDisplayCountry() { return mLocale.getDisplayCountry(); }
    public String getDisplayCountry(ULocale displayLocale) { return mLocale.getDisplayCountry(unwrap(displayLocale)); }

    public String toLanguageTag() { return mLocale.toLanguageTag(); }

    /** ICU: toString() is the ICU locale ID, i.e. getName(). */
    @Override public String toString() { return getName(); }

    @Override public boolean equals(Object o) {
        return (o instanceof ULocale) && ((ULocale) o).mLocale.equals(mLocale);
    }

    @Override public int hashCode() { return mLocale.hashCode(); }

    @Override public int compareTo(ULocale other) {
        return (other == null) ? 1 : getName().compareTo(other.getName());
    }

    // Internal helper: any ULocale-or-null -> a usable java.util.Locale.
    public static Locale unwrap(ULocale u) {
        return (u != null) ? u.mLocale : Locale.getDefault();
    }

    public static ULocale addLikelySubtags(ULocale loc) { return loc; }

    /**
     * ICU's ULocale.Builder. Delegates to java.util.Locale.Builder, which was
     * contributed to the JDK from this very class and keeps its grammar and
     * well-formedness rules (setLanguageTag, setUnicodeLocaleKeyword taking BCP
     * keys and types, clearExtensions, ...). Only the exception type differs:
     * java.util.IllformedLocaleException becomes android.icu.util's.
     */
    public static final class Builder {
        private final Locale.Builder b = new Locale.Builder();

        public Builder() {}

        public Builder setLocale(ULocale locale) {
            try { b.setLocale(locale == null ? null : locale.mLocale); }
            catch (java.util.IllformedLocaleException e) { throw IllformedLocaleException.fromJdk(e); }
            return this;
        }
        public Builder setLanguageTag(String languageTag) {
            try { b.setLanguageTag(languageTag); }
            catch (java.util.IllformedLocaleException e) { throw IllformedLocaleException.fromJdk(e); }
            return this;
        }
        public Builder setLanguage(String language) {
            try { b.setLanguage(language); }
            catch (java.util.IllformedLocaleException e) { throw IllformedLocaleException.fromJdk(e); }
            return this;
        }
        public Builder setScript(String script) {
            try { b.setScript(script); }
            catch (java.util.IllformedLocaleException e) { throw IllformedLocaleException.fromJdk(e); }
            return this;
        }
        public Builder setRegion(String region) {
            try { b.setRegion(region); }
            catch (java.util.IllformedLocaleException e) { throw IllformedLocaleException.fromJdk(e); }
            return this;
        }
        public Builder setVariant(String variant) {
            try { b.setVariant(variant); }
            catch (java.util.IllformedLocaleException e) { throw IllformedLocaleException.fromJdk(e); }
            return this;
        }
        public Builder setExtension(char key, String value) {
            try { b.setExtension(key, value); }
            catch (java.util.IllformedLocaleException e) { throw IllformedLocaleException.fromJdk(e); }
            return this;
        }
        public Builder setUnicodeLocaleKeyword(String key, String type) {
            try { b.setUnicodeLocaleKeyword(key, type); }
            catch (java.util.IllformedLocaleException e) { throw IllformedLocaleException.fromJdk(e); }
            return this;
        }
        public Builder addUnicodeLocaleAttribute(String attribute) {
            try { b.addUnicodeLocaleAttribute(attribute); }
            catch (java.util.IllformedLocaleException e) { throw IllformedLocaleException.fromJdk(e); }
            return this;
        }
        public Builder removeUnicodeLocaleAttribute(String attribute) {
            try { b.removeUnicodeLocaleAttribute(attribute); }
            catch (java.util.IllformedLocaleException e) { throw IllformedLocaleException.fromJdk(e); }
            return this;
        }
        public Builder clear() { b.clear(); return this; }
        public Builder clearExtensions() { b.clearExtensions(); return this; }

        public ULocale build() { return new ULocale(b.build()); }
    }
}
