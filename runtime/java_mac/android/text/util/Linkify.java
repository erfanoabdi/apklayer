// android.text.util.Linkify -- AOSP utility that scans text for web URLs,
// email addresses, etc. and wraps each match in a URLSpan so it renders /
// behaves as a tappable link. AOSP:
//   frameworks/base/core/java/android/text/util/Linkify.java
//
// Why this class exists in Mac.apk: androidx core's LinkifyCompat.addLinks
// delegates straight to android.text.util.Linkify on API >= 28
// (shouldAddLinksFallbackToFramework() -> true; our Build.SDK_INT == 29).
// F-Droid's AppDetailsRecyclerViewAdapter$HeaderViewHolder.bindModel calls
// LinkifyCompat.addLinks(descriptionView, WEB_URLS|EMAIL_ADDRESSES). With no
// Linkify class the call raised NoClassDefFoundError inside onBindViewHolder,
// which propagated up RecyclerView.dispatchLayoutStep2 / onLayoutChildren and
// aborted the whole detail tree's layout pass (blank screen).
//
// We implement the observable contract real Android gives the APK: addLinks
// finds matches via android.util.Patterns, wraps each in a URLSpan with the
// AOSP scheme-prefix rules, and (for the TextView overload) installs a
// LinkMovementMethod -- mirroring frameworks/base behavior. The full
// PHONE_NUMBERS / MAP_ADDRESSES matchers and per-match MatchFilter/Transform-
// Filter machinery are AOSP-shaped (the nested interfaces + the mask
// constants + sUrlMatchFilter exist for source/binary compatibility) but the
// number/map gatherers are not wired since no corpus app links those.
package android.text.util;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.widget.TextView;

import java.util.regex.Matcher;

public class Linkify {
    /** Bit mask: match web URLs. */
    public static final int WEB_URLS = 0x01;
    /** Bit mask: match email addresses. */
    public static final int EMAIL_ADDRESSES = 0x02;
    /** Bit mask: match phone numbers. */
    public static final int PHONE_NUMBERS = 0x04;
    /** Bit mask: match street/map addresses (deprecated upstream). */
    public static final int MAP_ADDRESSES = 0x08;
    /** Bit mask: match everything above. */
    public static final int ALL = WEB_URLS | EMAIL_ADDRESSES | PHONE_NUMBERS | MAP_ADDRESSES;

    /** AOSP MatchFilter: decides whether a regex hit should become a link. */
    public interface MatchFilter {
        boolean acceptMatch(CharSequence s, int start, int end);
    }

    /** AOSP TransformFilter: rewrites the matched text into the link URL. */
    public interface TransformFilter {
        String transformUrl(Matcher match, String url);
    }

    /** AOSP-referenced filter (LinkifyCompat reads this field); accepts every
     *  web-URL match. AOSP's variant additionally rejects matches preceded by a
     *  digit+'.' (version-number false positives); not needed for our use. */
    public static final MatchFilter sUrlMatchFilter = (s, start, end) -> true;

    /** AOSP Linkify.PHONE_NUMBER_MINIMUM_DIGITS (Linkify.java:140). */
    private static final int PHONE_NUMBER_MINIMUM_DIGITS = 5;

    /**
     * AOSP Linkify.sPhoneNumberMatchFilter (Linkify.java:169-183): accept a
     * phone-number match only once it contains at least
     * PHONE_NUMBER_MINIMUM_DIGITS digits, which is what stops short numeric
     * runs ("2024", a version string) from becoming tel: links.
     */
    public static final MatchFilter sPhoneNumberMatchFilter = new MatchFilter() {
        @Override public boolean acceptMatch(CharSequence s, int start, int end) {
            int digitCount = 0;
            for (int i = start; i < end; i++) {
                if (Character.isDigit(s.charAt(i))) {
                    digitCount++;
                    if (digitCount >= PHONE_NUMBER_MINIMUM_DIGITS) {
                        return true;
                    }
                }
            }
            return false;
        }
    };

    /**
     * AOSP Linkify.sPhoneNumberTransformFilter (Linkify.java:192-196):
     * "+1 (919) 555-1212" becomes "+19195551212" -- everything but digits and
     * plus signs is stripped so the result is usable in a tel: URL.
     *
     * AOSP delegates to android.util.Patterns.digitsAndPlusOnly(Matcher), which
     * does not exist in this tree's Patterns (another lane's file); the strip
     * is done inline here rather than adding a method outside this lane.
     */
    public static final TransformFilter sPhoneNumberTransformFilter = new TransformFilter() {
        @Override public String transformUrl(Matcher match, String url) {
            String digits = match.group();
            StringBuilder out = new StringBuilder(digits.length());
            for (int i = 0; i < digits.length(); i++) {
                char c = digits.charAt(i);
                if (Character.isDigit(c) || c == '+') out.append(c);
            }
            return out.toString();
        }
    };

    /** AOSP declares no constructor for Linkify, so the implicit PUBLIC no-arg
     *  one is public API (`ctor public Linkify()` in the signature file). Ours
     *  was private, which is a NoSuchMethodError for `new Linkify()` -- and
     *  code really does write that, because Linkify's own javadoc examples once
     *  showed an instance. */
    public Linkify() {}

    /** AOSP Linkify.addLinks(Spannable, int): add link spans for each enabled
     *  match type. Returns true iff at least one link was added. */
    public static boolean addLinks(Spannable text, int mask) {
        return addLinks(text, mask, (java.util.function.Function<String, URLSpan>) null);
    }

    /** AOSP Linkify.addLinks(Spannable, int, Function&lt;String, URLSpan&gt;) (API 33,
     *  android13 Linkify.java): the mask form with a URLSpan factory, so a caller
     *  can substitute its own URLSpan subclass; null means a plain URLSpan.
     *  Compose's text linking calls exactly this, and before it existed every
     *  composition that linkified text died with NoSuchMethodError (the Epic
     *  Games Store's product page force-closed, 2026-10-20). As AOSP, the
     *  URLSpans already on the text are removed first, so links are rebuilt
     *  rather than stacked. */
    public static boolean addLinks(Spannable text, int mask,
            java.util.function.Function<String, URLSpan> urlSpanFactory) {
        if (text == null || mask == 0) return false;
        URLSpan[] old = text.getSpans(0, text.length(), URLSpan.class);
        if (old != null) {
            for (int i = old.length - 1; i >= 0; i--) text.removeSpan(old[i]);
        }
        boolean added = false;
        if ((mask & WEB_URLS) != 0) {
            added |= gatherLinks(text, android.util.Patterns.WEB_URL,
                                 new String[]{"http://", "https://", "ftp://", "www."}, urlSpanFactory);
        }
        if ((mask & EMAIL_ADDRESSES) != 0) {
            added |= gatherLinks(text, android.util.Patterns.EMAIL_ADDRESS,
                                 new String[]{"mailto:"}, urlSpanFactory);
        }
        // PHONE_NUMBERS / MAP_ADDRESSES intentionally not gathered (see header).
        return added;
    }

    /** AOSP Linkify.addLinks(TextView, int): link the view's text and, if any
     *  link was added, make the links followable via LinkMovementMethod. */
    public static boolean addLinks(TextView text, int mask) {
        if (mask == 0) return false;
        CharSequence t = text.getText();
        if (t instanceof Spannable) {
            if (addLinks((Spannable) t, mask)) {
                addLinkMovementMethod(text);
                return true;
            }
            return false;
        }
        SpannableString s = SpannableString.valueOf(t);
        if (addLinks(s, mask)) {
            addLinkMovementMethod(text);
            text.setText(s);
            return true;
        }
        return false;
    }

    private static void addLinkMovementMethod(TextView t) {
        // AOSP installs LinkMovementMethod so the spans are clickable. Our
        // setMovementMethod stores it; canvas link-tap routing is a follow-up.
        try { t.setMovementMethod(android.text.method.LinkMovementMethod.getInstance()); }
        catch (Throwable ignored) {}
    }

    private static boolean gatherLinks(Spannable text, java.util.regex.Pattern pattern,
                                       String[] schemes,
                                       java.util.function.Function<String, URLSpan> urlSpanFactory) {
        boolean added = false;
        String content = text.toString();
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (!sUrlMatchFilter.acceptMatch(content, start, end)) continue;
            String url = makeUrl(m.group(0), schemes);
            URLSpan span = (urlSpanFactory == null) ? new URLSpan(url) : urlSpanFactory.apply(url);
            text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            added = true;
        }
        return added;
    }

    /** AOSP makeUrl: prepend the first scheme prefix when the matched text has
     *  no recognized scheme. "www." matches get an http:// prefix. */
    private static String makeUrl(String url, String[] prefixes) {
        if (prefixes == null || prefixes.length == 0) return url;
        String lower = url.toLowerCase();
        for (String p : prefixes) {
            if (p.endsWith("://") && lower.startsWith(p)) return url;       // already schemed
            if (p.equals("mailto:") && lower.startsWith(p)) return url;
        }
        // mailto: family
        if (prefixes[0].equals("mailto:")) return "mailto:" + url;
        // www.foo -> http://www.foo ; otherwise apply the matched scheme.
        if (lower.startsWith("www.")) return "http://" + url;
        return "http://" + url;
    }

    /** AOSP Linkify.addLinks(text, pattern, scheme) (API 1): wrap every match of
     *  `pattern` in a URLSpan, prefixing `scheme` when the match lacks it. A real
     *  implementation. */
    public static boolean addLinks(android.text.Spannable spannable,
            java.util.regex.Pattern pattern, String scheme) {
        boolean hasMatches = false;
        String prefix = scheme == null ? "" : scheme;
        java.util.regex.Matcher m = pattern.matcher(spannable);
        while (m.find()) {
            int start = m.start(), end = m.end();
            String url = m.group();
            if (prefix.length() == 0
                    || !url.regionMatches(true, 0, prefix, 0, prefix.length())) {
                url = prefix + url;
            }
            spannable.setSpan(new android.text.style.URLSpan(url), start, end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            hasMatches = true;
        }
        return hasMatches;
    }


    /**
     * AOSP Linkify.addLinks(Spannable, Pattern, String, MatchFilter,
     * TransformFilter) -- `return addLinks(spannable, pattern, scheme, null,
     * matchFilter, transformFilter);`
     *
     * ⚠ BEHAVIOUR CHANGE, deliberate: this used to drop matchFilter and
     * transformFilter on the floor and call the three-argument form, so a
     * caller's "reject this match" / "rewrite this URL" logic did nothing at
     * all -- the exact failure the filters exist to prevent
     * (sPhoneNumberMatchFilter is how a bare "2024" avoids becoming a tel:
     * link). Both are now honoured, through the seven-argument body below.
     */
    public static boolean addLinks(android.text.Spannable spannable, java.util.regex.Pattern pattern,
            String scheme, MatchFilter matchFilter, TransformFilter transformFilter) {
        return addLinks(spannable, pattern, scheme, null, matchFilter, transformFilter, null);
    }

    /** AOSP Linkify.addLinks(Spannable, Pattern, String, String[], MatchFilter,
     *  TransformFilter) (Linkify.java:542-547) -- delegates with a null span
     *  factory. Same behaviour-change note as the overload above. */
    public static boolean addLinks(android.text.Spannable spannable, java.util.regex.Pattern pattern,
            String defaultScheme, String[] schemes, MatchFilter matchFilter, TransformFilter transformFilter) {
        return addLinks(spannable, pattern, defaultScheme, schemes, matchFilter, transformFilter,
                null);
    }

    /**
     * AOSP Linkify.addLinks(Spannable, Pattern, String, String[], MatchFilter,
     * TransformFilter, Function&lt;String, URLSpan&gt;) (Linkify.java:567-609)
     * -- the full form, and the one every other Spannable overload funnels
     * through on a device. `urlSpanFactory` lets a caller substitute its own
     * URLSpan subclass (androidx uses it to route clicks through a custom
     * handler); null means a plain URLSpan.
     *
     * The scheme handling is AOSP's makeUrl (Linkify.java:622-647) verbatim:
     * `defaultScheme` is prepended UNLESS the URL already starts with one of
     * `schemes` (case-insensitively), in which case only the CAPITALISATION is
     * corrected to the declared scheme's.
     */
    public static boolean addLinks(android.text.Spannable spannable,
            java.util.regex.Pattern pattern, String defaultScheme, String[] schemes,
            MatchFilter matchFilter, TransformFilter transformFilter,
            java.util.function.Function<String, URLSpan> urlSpanFactory) {
        if (spannable == null || pattern == null) return false;
        if (defaultScheme == null) defaultScheme = "";
        if (schemes == null) schemes = new String[0];

        final String[] schemesCopy = new String[schemes.length + 1];
        schemesCopy[0] = defaultScheme.toLowerCase(java.util.Locale.ROOT);
        for (int index = 0; index < schemes.length; index++) {
            String scheme = schemes[index];
            schemesCopy[index + 1] = (scheme == null) ? "" : scheme.toLowerCase(java.util.Locale.ROOT);
        }

        boolean hasMatches = false;
        Matcher m = pattern.matcher(spannable);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            boolean allowed = true;
            if (matchFilter != null) {
                allowed = matchFilter.acceptMatch(spannable, start, end);
            }
            if (allowed) {
                String url = makeUrl(m.group(0), schemesCopy, m, transformFilter);
                URLSpan span = (urlSpanFactory == null) ? new URLSpan(url)
                                                        : urlSpanFactory.apply(url);
                spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                hasMatches = true;
            }
        }
        return hasMatches;
    }

    /** AOSP Linkify.makeUrl (Linkify.java:622-647). */
    private static String makeUrl(String url, String[] prefixes, Matcher matcher,
            TransformFilter filter) {
        if (filter != null) {
            url = filter.transformUrl(matcher, url);
        }
        boolean hasPrefix = false;
        for (int i = 0; i < prefixes.length; i++) {
            if (url.regionMatches(true, 0, prefixes[i], 0, prefixes[i].length())) {
                hasPrefix = true;
                // Fix capitalization if necessary
                if (!url.regionMatches(false, 0, prefixes[i], 0, prefixes[i].length())) {
                    url = prefixes[i] + url.substring(prefixes[i].length());
                }
                break;
            }
        }
        if (!hasPrefix && prefixes.length > 0) {
            url = prefixes[0] + url;
        }
        return url;
    }
    public static void addLinks(android.widget.TextView text, java.util.regex.Pattern pattern, String scheme) {
        if (text != null && text.getText() instanceof android.text.Spannable)
            addLinks((android.text.Spannable) text.getText(), pattern, scheme);
    }
    public static void addLinks(android.widget.TextView text, java.util.regex.Pattern pattern, String scheme,
            MatchFilter matchFilter, TransformFilter transformFilter) {
        addLinks(text, pattern, scheme, null, matchFilter, transformFilter);
    }
    public static void addLinks(android.widget.TextView text, java.util.regex.Pattern pattern, String defaultScheme,
            String[] schemes, MatchFilter matchFilter, TransformFilter transformFilter) {
        if (text == null) return;
        // AOSP wraps the TextView's text in a SpannableString when it is not
        // already Spannable, links THAT, and sets it back -- otherwise a plain
        // String-backed TextView silently gains no links at all.
        CharSequence t = text.getText();
        android.text.Spannable sp;
        if (t instanceof android.text.Spannable) {
            sp = (android.text.Spannable) t;
        } else {
            sp = new android.text.SpannableString(t == null ? "" : t);
        }
        if (addLinks(sp, pattern, defaultScheme, schemes, matchFilter, transformFilter, null)) {
            text.setText(sp);
            text.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        }
    }
}

