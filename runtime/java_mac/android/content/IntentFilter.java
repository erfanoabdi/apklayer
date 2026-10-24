// android.content.IntentFilter -- describes the Intents a component (or a
// registered BroadcastReceiver) is willing to handle: a set of actions,
// categories, and data constraints (scheme / authority / path / mime type).
// PackageManager.queryIntent* and the in-process broadcast registry use
// match() to test an Intent against a filter.
//
// AOSP ref:
//   frameworks/base/core/java/android/content/IntentFilter.java
//
// Previously only actions+categories were stored and there was NO data
// matching, so implicit-intent resolution (VIEW a content:// or http URI,
// SEND a mime type) could never match a typed filter. This implements the
// real data-matching engine (scheme/ssp/authority/path/type + match()),
// faithful to AOSP's matchData/matchDataAuthority/match algorithm.
package android.content;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PatternMatcher;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class IntentFilter implements Parcelable {
    /** AOSP core/java/android/content/IntentFilter.java -- the priority
     *  values the system reserves for its own receivers; an app that sets a
     *  priority outside (SYSTEM_LOW_PRIORITY, SYSTEM_HIGH_PRIORITY) has it
     *  clamped on a device. */
    public static final int SYSTEM_HIGH_PRIORITY = 1000;
    public static final int SYSTEM_LOW_PRIORITY = -1000;

    // ---- match result categories (higher = better) ----
    public static final int MATCH_CATEGORY_MASK     = 0x0fff0000;
    public static final int MATCH_ADJUSTMENT_MASK   = 0x0000ffff;
    public static final int MATCH_ADJUSTMENT_NORMAL = 0x00008000;
    public static final int MATCH_CATEGORY_EMPTY    = 0x00100000;
    public static final int MATCH_CATEGORY_SCHEME   = 0x00200000;
    public static final int MATCH_CATEGORY_HOST     = 0x00300000;
    public static final int MATCH_CATEGORY_PORT     = 0x00400000;
    public static final int MATCH_CATEGORY_PATH     = 0x00500000;
    public static final int MATCH_CATEGORY_SCHEME_SPECIFIC_PART = 0x00580000;
    public static final int MATCH_CATEGORY_TYPE     = 0x00600000;

    // ---- no-match error codes ----
    public static final int NO_MATCH_TYPE     = -1;
    public static final int NO_MATCH_DATA     = -2;
    public static final int NO_MATCH_ACTION   = -3;
    public static final int NO_MATCH_CATEGORY = -4;

    private final List<String> mActions = new ArrayList<>();
    private final List<String> mCategories = new ArrayList<>();
    private final List<String> mDataSchemes = new ArrayList<>();
    private final List<PatternMatcher> mDataSchemeSpecificParts = new ArrayList<>();
    private final List<AuthorityEntry> mDataAuthorities = new ArrayList<>();
    private final List<PatternMatcher> mDataPaths = new ArrayList<>();
    private final List<String> mDataTypes = new ArrayList<>();
    // AOSP mHasStaticPartialTypes: set when a "type/*" was added, because
    // addDataType stores such a type NORMALIZED down to its base ("text/*" ->
    // "text") and findMimeType must be able to tell that stored "text" apart
    // from a hypothetical literal type named "text". Without the flag the
    // partial-type branches of findMimeType cannot fire at all.
    private boolean mHasPartialTypes;
    // AOSP mPriority: ordering hint for broadcast receivers and for resolution
    // among competing filters. Storage only here (Mac.apk hosts one package so
    // nothing competes), but registerReceiver call sites set it and read it
    // back, and its absence was a NoSuchMethodError.
    private int mPriority;

    public IntentFilter() {}
    public IntentFilter(String action) { mActions.add(action); }
    /** AOSP's 2-arg constructor DECLARES `throws MalformedMimeTypeException`
     *  and lets addDataType's exception out unchanged -- it does not translate
     *  it to IllegalArgumentException. An app that catches the documented
     *  checked exception around `new IntentFilter(a, t)` never saw ours. */
    public IntentFilter(String action, String dataType) throws MalformedMimeTypeException {
        this(action);
        addDataType(dataType);
    }
    public IntentFilter(IntentFilter o) {
        mPriority = o.mPriority;
        mActions.addAll(o.mActions);
        mCategories.addAll(o.mCategories);
        mDataSchemes.addAll(o.mDataSchemes);
        mDataSchemeSpecificParts.addAll(o.mDataSchemeSpecificParts);
        mDataAuthorities.addAll(o.mDataAuthorities);
        mDataPaths.addAll(o.mDataPaths);
        mDataTypes.addAll(o.mDataTypes);
        mHasPartialTypes = o.mHasPartialTypes;
    }

    /** AOSP IntentFilter.create converts the CHECKED MalformedMimeTypeException
     *  into an unchecked RuntimeException; it never returns null. Swallowing to
     *  null meant a caller that trusts the documented non-null return NPE'd far
     *  from the actual bad MIME string. */
    public static IntentFilter create(String action, String dataType) {
        try {
            return new IntentFilter(action, dataType);
        } catch (MalformedMimeTypeException e) {
            throw new RuntimeException("Bad MIME type", e);
        }
    }

    // ---- priority ----
    public final void setPriority(int priority) { mPriority = priority; }
    public final int getPriority() { return mPriority; }

    // ---- actions ----
    public final void addAction(String action) { if (!mActions.contains(action)) mActions.add(action); }
    public final int countActions() { return mActions.size(); }
    public final String getAction(int index) { return mActions.get(index); }
    public final boolean hasAction(String action) { return action != null && mActions.contains(action); }
    public final boolean matchAction(String action) { return hasAction(action); }
    // ⚠ THESE RETURN null WHEN EMPTY, AND THAT IS AOSP-FAITHFUL, NOT A QUIRK.
    // AOSP creates each list lazily on first add, so a filter that never had one
    // added returns null (android14-release IntentFilter.java:1129, :1449).
    //
    // Apps USE the null as a signal. AppAuth's BrowserSelector -- which Thunderbird,
    // and every other app that signs in with OAuth, runs to find a usable browser --
    // distinguishes a real browser from a deep-link handler with literally
    // `filter.authoritiesIterator() == null`. Returning a non-null EMPTY iterator
    // made every candidate look like a deep-link handler, so AppAuth concluded no
    // browser exists and threw ActivityNotFoundException BEFORE ever calling
    // startActivity. That is the whole "oauth failed because no browser found"
    // report (2026-08-24): the failure was in RESOLVE, not in launching.
    //
    // Measured blast radius: no caller of any of these seven exists anywhere in
    // runtime/java, so this only changes what GUEST code sees -- which is the point.
    public final Iterator<String> actionsIterator() { return mActions.isEmpty() ? null : mActions.iterator(); }

    // ---- categories ----
    public final void addCategory(String category) { if (!mCategories.contains(category)) mCategories.add(category); }
    public final int countCategories() { return mCategories.size(); }
    public final String getCategory(int index) { return mCategories.get(index); }
    public final boolean hasCategory(String category) { return mCategories.contains(category); }
    public final Iterator<String> categoriesIterator() { return mCategories.isEmpty() ? null : mCategories.iterator(); }

    /** AOSP matchCategories: returns null if the filter matches every category
     *  in the set, else the first category that is not handled. */
    public final String matchCategories(Set<String> categories) {
        if (categories == null) return null;
        for (String c : categories) {
            if (!mCategories.contains(c)) return c;
        }
        return null;
    }

    // ---- data schemes ----
    /** AOSP does NOT null-check here: `scheme.intern()` NPEs on a null scheme,
     *  which is the documented way an app learns its manifest/registration is
     *  malformed. Silently ignoring it let a filter that meant to declare a
     *  scheme end up matching everything scheme-less instead. */
    public final void addDataScheme(String scheme) {
        if (!mDataSchemes.contains(scheme)) mDataSchemes.add(scheme.intern());
    }
    public final int countDataSchemes() { return mDataSchemes.size(); }
    public final String getDataScheme(int index) { return mDataSchemes.get(index); }
    public final boolean hasDataScheme(String scheme) { return mDataSchemes.contains(scheme); }
    public final Iterator<String> schemesIterator() { return mDataSchemes.isEmpty() ? null : mDataSchemes.iterator(); }

    // ---- scheme-specific parts ----
    public final void addDataSchemeSpecificPart(String ssp, int type) {
        mDataSchemeSpecificParts.add(new PatternMatcher(ssp, type));
    }
    public final int countDataSchemeSpecificParts() { return mDataSchemeSpecificParts.size(); }
    public final PatternMatcher getDataSchemeSpecificPart(int index) { return mDataSchemeSpecificParts.get(index); }
    public final boolean hasDataSchemeSpecificPart(String data) {
        for (PatternMatcher p : mDataSchemeSpecificParts) if (p.match(data)) return true;
        return false;
    }
    public final Iterator<PatternMatcher> schemeSpecificPartsIterator() { return mDataSchemeSpecificParts.isEmpty() ? null : mDataSchemeSpecificParts.iterator(); }

    // ---- authorities ----
    public final void addDataAuthority(String host, String port) {
        mDataAuthorities.add(new AuthorityEntry(host, port));
    }
    public final int countDataAuthorities() { return mDataAuthorities.size(); }
    public final AuthorityEntry getDataAuthority(int index) { return mDataAuthorities.get(index); }
    public final boolean hasDataAuthority(Uri data) { return matchDataAuthority(data) >= 0; }
    public final Iterator<AuthorityEntry> authoritiesIterator() { return mDataAuthorities.isEmpty() ? null : mDataAuthorities.iterator(); }
    public final int matchDataAuthority(Uri data) {
        if (data == null || mDataAuthorities.isEmpty()) return NO_MATCH_DATA;
        for (AuthorityEntry ae : mDataAuthorities) {
            int r = ae.match(data);
            if (r != NO_MATCH_DATA) return r;
        }
        return NO_MATCH_DATA;
    }

    // ---- paths ----
    public final void addDataPath(String path, int type) {
        mDataPaths.add(new PatternMatcher(path, type));
    }
    public final int countDataPaths() { return mDataPaths.size(); }
    public final PatternMatcher getDataPath(int index) { return mDataPaths.get(index); }
    public final boolean hasDataPath(String data) {
        for (PatternMatcher p : mDataPaths) if (p.match(data)) return true;
        return false;
    }
    public final Iterator<PatternMatcher> pathsIterator() { return mDataPaths.isEmpty() ? null : mDataPaths.iterator(); }

    // ---- types ----
    /** AOSP's addDataType -> processMimeType. A null type NPEs on
     *  `type.indexOf('/')` before any validation runs, so the observable
     *  failure for null is NullPointerException, NOT MalformedMimeTypeException
     *  -- an app catching only the checked exception around a null type is
     *  supposed to crash, and did not on us. */
    public final void addDataType(String type) throws MalformedMimeTypeException {
        final int slashpos = type.indexOf('/');
        final int typelen = type.length();
        if (slashpos <= 0 || typelen < slashpos + 2) {
            throw new MalformedMimeTypeException(type);
        }
        // AOSP stores "type/sub" verbatim but normalizes a trailing "/*" down to
        // the bare base type ("text/*" -> "text", "*/*" -> "*") and remembers
        // that a PARTIAL type was seen. Both halves are needed: findMimeType
        // only consults the bare-base entries when mHasPartialTypes is set.
        String internalType = type;
        boolean isPartialType = false;
        if (typelen == slashpos + 2 && type.charAt(slashpos + 1) == '*') {
            internalType = type.substring(0, slashpos);
            isPartialType = true;
        }
        if (!mDataTypes.contains(internalType)) {
            mDataTypes.add(internalType.intern());
        }
        mHasPartialTypes = mHasPartialTypes || isPartialType;
    }
    // ---- Relative-URI filter groups (API 35). Each group narrows the filter
    // beyond scheme/host by constraining the path/query/fragment; the FIRST
    // matching group's action (ALLOW/BLOCK) decides. These are pure storage +
    // the accessors AOSP declares; match() below does not consult them yet,
    // which is stated rather than implied -- an app that adds a group today
    // gets it stored and read back, not enforced during matching.
    private final List<UriRelativeFilterGroup> mUriRelativeFilterGroups =
            new ArrayList<UriRelativeFilterGroup>();

    public final void addUriRelativeFilterGroup(UriRelativeFilterGroup group) {
        java.util.Objects.requireNonNull(group);
        mUriRelativeFilterGroups.add(group);
    }
    public final int countUriRelativeFilterGroups() { return mUriRelativeFilterGroups.size(); }
    public final UriRelativeFilterGroup getUriRelativeFilterGroup(int index) {
        return mUriRelativeFilterGroups.get(index);
    }
    public final void clearUriRelativeFilterGroups() { mUriRelativeFilterGroups.clear(); }

    /** AOSP core/java/android/content/IntentFilter.java:2348 --
     *      return i -> match(null, i, false, TAG) >= 0;
     *  A null resolver is deliberate: without type resolution the intent's
     *  declared type is used as-is. */
    public final java.util.function.Predicate<Intent> asPredicate() {
        return i -> match(null, i, false, "IntentFilter") >= 0;
    }

    /** AOSP :2362 -- the same predicate, but the intent's type is resolved
     *  through the ContentResolver first (so a content:// Uri gets its real
     *  MIME type before matching). */
    public final java.util.function.Predicate<Intent> asPredicateWithTypeResolution(
            ContentResolver resolver) {
        java.util.Objects.requireNonNull(resolver);
        return i -> match(resolver, i, true, "IntentFilter") >= 0;
    }

    /** AOSP :2814 -- the debug dump, ported section for section (actions,
     *  categories, schemes, ssps, authorities, paths, relative-filter groups,
     *  types, then the priority line and the autoVerify line). The mime-group
     *  and mOrder/partial-types sections are omitted because this class has no
     *  such fields. */
    public final void dump(android.util.Printer du, String prefix) {
        StringBuilder sb = new StringBuilder(256);
        for (String a : mActions) {
            sb.setLength(0);
            sb.append(prefix); sb.append("Action: \""); sb.append(a); sb.append("\"");
            du.println(sb.toString());
        }
        for (String c : mCategories) {
            sb.setLength(0);
            sb.append(prefix); sb.append("Category: \""); sb.append(c); sb.append("\"");
            du.println(sb.toString());
        }
        for (String sch : mDataSchemes) {
            sb.setLength(0);
            sb.append(prefix); sb.append("Scheme: \""); sb.append(sch); sb.append("\"");
            du.println(sb.toString());
        }
        for (PatternMatcher pe : mDataSchemeSpecificParts) {
            sb.setLength(0);
            sb.append(prefix); sb.append("Ssp: \""); sb.append(pe); sb.append("\"");
            du.println(sb.toString());
        }
        for (AuthorityEntry ae : mDataAuthorities) {
            sb.setLength(0);
            sb.append(prefix); sb.append("Authority: \""); sb.append(ae.getHost());
            sb.append("\": "); sb.append(ae.getPort());
            du.println(sb.toString());
        }
        for (PatternMatcher pe : mDataPaths) {
            sb.setLength(0);
            sb.append(prefix); sb.append("Path: \""); sb.append(pe); sb.append("\"");
            du.println(sb.toString());
        }
        for (UriRelativeFilterGroup g : mUriRelativeFilterGroups) {
            sb.setLength(0);
            sb.append(prefix); sb.append("UriRelativeFilterGroup: \"");
            sb.append(g); sb.append("\"");
            du.println(sb.toString());
        }
        for (String t : mDataTypes) {
            sb.setLength(0);
            sb.append(prefix); sb.append("Type: \""); sb.append(t); sb.append("\"");
            du.println(sb.toString());
        }
        if (mPriority != 0) {
            sb.setLength(0);
            sb.append(prefix); sb.append("mPriority="); sb.append(mPriority);
            du.println(sb.toString());
        }
    }

    // ---- XML persistence (AOSP :2506 / :2650). The element and attribute
    // names are AOSP's private string constants, transcribed:
    //     action/cat/scheme/ssp/auth/path/type/group, name/literal/prefix/
    //     sglob/aglob/suffix/host/port/autoVerify
    // so a filter written here is readable by AOSP's parser and vice versa.
    // AOSP's writer also emits mime groups, static types and the extras
    // Bundle; this class has no such fields, so those elements are absent
    // (its reader skips unknown tags, and so does ours).
    private static final String ACTION_STR = "action";
    private static final String CAT_STR = "cat";
    private static final String NAME_STR = "name";
    private static final String SCHEME_STR = "scheme";
    private static final String SSP_STR = "ssp";
    private static final String AUTH_STR = "auth";
    private static final String PATH_STR = "path";
    private static final String TYPE_STR = "type";
    private static final String HOST_STR = "host";
    private static final String PORT_STR = "port";
    private static final String LITERAL_STR = "literal";
    private static final String PREFIX_STR = "prefix";
    private static final String SGLOB_STR = "sglob";
    private static final String AGLOB_STR = "aglob";
    private static final String SUFFIX_STR = "suffix";

    public final void writeToXml(org.xmlpull.v1.XmlSerializer serializer)
            throws java.io.IOException {
        for (String a : mActions) {
            serializer.startTag(null, ACTION_STR);
            serializer.attribute(null, NAME_STR, a);
            serializer.endTag(null, ACTION_STR);
        }
        for (String c : mCategories) {
            serializer.startTag(null, CAT_STR);
            serializer.attribute(null, NAME_STR, c);
            serializer.endTag(null, CAT_STR);
        }
        for (String t : mDataTypes) {
            serializer.startTag(null, TYPE_STR);
            serializer.attribute(null, NAME_STR, t);
            serializer.endTag(null, TYPE_STR);
        }
        for (String sch : mDataSchemes) {
            serializer.startTag(null, SCHEME_STR);
            serializer.attribute(null, NAME_STR, sch);
            serializer.endTag(null, SCHEME_STR);
        }
        for (PatternMatcher pe : mDataSchemeSpecificParts) {
            serializer.startTag(null, SSP_STR);
            writePattern(serializer, pe);
            serializer.endTag(null, SSP_STR);
        }
        for (AuthorityEntry ae : mDataAuthorities) {
            serializer.startTag(null, AUTH_STR);
            serializer.attribute(null, HOST_STR, ae.getHost());
            if (ae.getPort() >= 0) {
                serializer.attribute(null, PORT_STR, Integer.toString(ae.getPort()));
            }
            serializer.endTag(null, AUTH_STR);
        }
        for (PatternMatcher pe : mDataPaths) {
            serializer.startTag(null, PATH_STR);
            writePattern(serializer, pe);
            serializer.endTag(null, PATH_STR);
        }
    }

    private static void writePattern(org.xmlpull.v1.XmlSerializer serializer, PatternMatcher pe)
            throws java.io.IOException {
        switch (pe.getType()) {
            case PatternMatcher.PATTERN_LITERAL:
                serializer.attribute(null, LITERAL_STR, pe.getPath()); break;
            case PatternMatcher.PATTERN_PREFIX:
                serializer.attribute(null, PREFIX_STR, pe.getPath()); break;
            case PatternMatcher.PATTERN_SIMPLE_GLOB:
                serializer.attribute(null, SGLOB_STR, pe.getPath()); break;
            case PatternMatcher.PATTERN_ADVANCED_GLOB:
                serializer.attribute(null, AGLOB_STR, pe.getPath()); break;
            case PatternMatcher.PATTERN_SUFFIX:
                serializer.attribute(null, SUFFIX_STR, pe.getPath()); break;
            default:
                break;
        }
    }

    public final void readFromXml(org.xmlpull.v1.XmlPullParser parser)
            throws org.xmlpull.v1.XmlPullParserException, java.io.IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT
                && (type != org.xmlpull.v1.XmlPullParser.END_TAG
                    || parser.getDepth() > outerDepth)) {
            if (type != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
            String tagName = parser.getName();
            if (ACTION_STR.equals(tagName)) {
                String name = parser.getAttributeValue(null, NAME_STR);
                if (name != null) addAction(name);
            } else if (CAT_STR.equals(tagName)) {
                String name = parser.getAttributeValue(null, NAME_STR);
                if (name != null) addCategory(name);
            } else if (TYPE_STR.equals(tagName)) {
                String name = parser.getAttributeValue(null, NAME_STR);
                if (name != null) {
                    try { addDataType(name); }
                    catch (MalformedMimeTypeException e) { /* AOSP also skips */ }
                }
            } else if (SCHEME_STR.equals(tagName)) {
                String name = parser.getAttributeValue(null, NAME_STR);
                if (name != null) addDataScheme(name);
            } else if (SSP_STR.equals(tagName)) {
                readPattern(parser, true);
            } else if (AUTH_STR.equals(tagName)) {
                String host = parser.getAttributeValue(null, HOST_STR);
                String port = parser.getAttributeValue(null, PORT_STR);
                if (host != null) addDataAuthority(host, port);
            } else if (PATH_STR.equals(tagName)) {
                readPattern(parser, false);
            }
            skipCurrentTag(parser);
        }
    }

    private void readPattern(org.xmlpull.v1.XmlPullParser parser, boolean ssp) {
        String v;
        int t;
        if ((v = parser.getAttributeValue(null, LITERAL_STR)) != null) {
            t = PatternMatcher.PATTERN_LITERAL;
        } else if ((v = parser.getAttributeValue(null, PREFIX_STR)) != null) {
            t = PatternMatcher.PATTERN_PREFIX;
        } else if ((v = parser.getAttributeValue(null, SGLOB_STR)) != null) {
            t = PatternMatcher.PATTERN_SIMPLE_GLOB;
        } else if ((v = parser.getAttributeValue(null, AGLOB_STR)) != null) {
            t = PatternMatcher.PATTERN_ADVANCED_GLOB;
        } else if ((v = parser.getAttributeValue(null, SUFFIX_STR)) != null) {
            t = PatternMatcher.PATTERN_SUFFIX;
        } else {
            return;
        }
        if (ssp) addDataSchemeSpecificPart(v, t);
        else     addDataPath(v, t);
    }

    // AOSP uses XmlUtils.skipCurrentTag(); ported inline (this tree has no
    // com.android.internal.util.XmlUtils).
    private static void skipCurrentTag(org.xmlpull.v1.XmlPullParser parser)
            throws org.xmlpull.v1.XmlPullParserException, java.io.IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT
                && (type != org.xmlpull.v1.XmlPullParser.END_TAG
                    || parser.getDepth() > outerDepth)) {
            // skip
        }
    }

    public final int countDataTypes() { return mDataTypes.size(); }
    public final String getDataType(int index) { return mDataTypes.get(index); }
    public final boolean hasDataType(String type) { return !mDataTypes.isEmpty() && findMimeType(type); }
    public final Iterator<String> typesIterator() { return mDataTypes.isEmpty() ? null : mDataTypes.iterator(); }

    /** Faithful port of AOSP IntentFilter.findMimeType. The two branches we
     *  were missing both matter for real share sheets:
     *   - a QUERY of "&#42;/&#42;" matches any non-empty filter (an app asking
     *     "who can handle anything?"), and
     *   - a QUERY of "text/&#42;" matches a filter declaring "text/plain", via
     *     the regionMatches prefix scan at the bottom.
     *  Note the asymmetry is deliberate: the partial-type branches consult the
     *  bare-base entries only when this filter itself declared a "type/&#42;". */
    private boolean findMimeType(String type) {
        final List<String> t = mDataTypes;
        if (type == null) return false;
        if (t.contains(type)) return true;

        // An Intent wanting to match every type in this filter.
        final int typeLength = type.length();
        if (typeLength == 3 && type.equals("*/*")) {
            return !t.isEmpty();
        }

        // This filter wanting to match every Intent type.
        if (mHasPartialTypes && t.contains("*")) return true;

        final int slashpos = type.indexOf('/');
        if (slashpos > 0) {
            if (mHasPartialTypes && t.contains(type.substring(0, slashpos))) return true;
            if (typeLength == slashpos + 2 && type.charAt(slashpos + 1) == '*') {
                // Query is "base/*": any stored type with the same "base/"
                // prefix satisfies it.
                final int numTypes = t.size();
                for (int i = 0; i < numTypes; i++) {
                    if (type.regionMatches(0, t.get(i), 0, slashpos + 1)) return true;
                }
            }
        }
        return false;
    }

    // ---- the matching engine ----

    /** AOSP matchData: tests the supplied (type, scheme, data) against the
     *  filter's data constraints, returning a MATCH_CATEGORY_* result or a
     *  NO_MATCH_* error code. */
    public final int matchData(String type, String scheme, Uri data) {
        // AOSP tests its lists for NULL; ours are eagerly-allocated finals, so
        // "empty" is the equivalent state throughout this method.
        final boolean hasSchemes = !mDataSchemes.isEmpty();
        final boolean hasTypes = !mDataTypes.isEmpty();

        int match = MATCH_CATEGORY_EMPTY;

        // No data constraints at all. Note the gate is on DATA, not on scheme:
        // an Intent carrying a scheme-less Uri ("/just/a/path") still has data
        // and must NOT match a filter that declares none.
        if (!hasSchemes && !hasTypes) {
            return ((type == null && data == null)
                ? (MATCH_CATEGORY_EMPTY + MATCH_ADJUSTMENT_NORMAL) : NO_MATCH_DATA);
        }

        if (hasSchemes) {
            // A NULL intent scheme is compared as "" -- that is how a filter
            // declaring <data android:scheme=""> matches data with no scheme.
            if (mDataSchemes.contains(scheme != null ? scheme : "")) {
                match = MATCH_CATEGORY_SCHEME;
            } else {
                return NO_MATCH_DATA;
            }

            if (!mDataSchemeSpecificParts.isEmpty() && data != null) {
                match = hasDataSchemeSpecificPart(data.getSchemeSpecificPart())
                        ? MATCH_CATEGORY_SCHEME_SPECIFIC_PART : NO_MATCH_DATA;
            }
            if (match != MATCH_CATEGORY_SCHEME_SPECIFIC_PART) {
                // No matching ssp, so an authority has to carry the match.
                // AOSP deliberately falls through to this rather than
                // returning, so a filter declaring BOTH ssp and authority can
                // still match on the authority when the ssp misses.
                if (!mDataAuthorities.isEmpty()) {
                    int authMatch = matchDataAuthority(data);
                    if (authMatch >= 0) {
                        if (mDataPaths.isEmpty()) {
                            match = authMatch;
                        } else if (hasDataPath(data.getPath())) {
                            match = MATCH_CATEGORY_PATH;
                        } else {
                            return NO_MATCH_DATA;
                        }
                    } else {
                        return NO_MATCH_DATA;
                    }
                }
            }
            // Neither an ssp nor an authority matched.
            if (match == NO_MATCH_DATA) {
                return NO_MATCH_DATA;
            }
        } else {
            // Special case: a filter with types but no schemes matches either an
            // Intent with no data Uri at all, or one whose scheme is content: or
            // file:. This is the convenience that lets a share target declare
            // only <data android:mimeType="text/plain"/> without also having to
            // enumerate content: and file:. Critically it also means such a
            // filter REJECTS an http: intent -- we had no such branch, so every
            // ACTION_SEND filter claimed http/https links it cannot handle.
            if (scheme != null && !"".equals(scheme)
                    && !"content".equals(scheme)
                    && !"file".equals(scheme)) {
                return NO_MATCH_DATA;
            }
        }

        if (hasTypes) {
            if (findMimeType(type)) {
                match = MATCH_CATEGORY_TYPE;
            } else {
                return NO_MATCH_TYPE;
            }
        } else {
            // No MIME types declared: match only an Intent with no MIME type.
            // AOSP applies this even when the SCHEME already matched (we had an
            // extra `&& !hasSchemes` guard, so a scheme-only filter wrongly
            // accepted a typed Intent).
            if (type != null) {
                return NO_MATCH_TYPE;
            }
        }

        return match + MATCH_ADJUSTMENT_NORMAL;
    }

    /** AOSP match(action, type, scheme, data, categories, logTag). */
    public final int match(String action, String type, String scheme, Uri data,
                           Set<String> categories, String logTag) {
        if (action != null && !matchAction(action)) {
            return NO_MATCH_ACTION;
        }
        int dataMatch = matchData(type, scheme, data);
        if (dataMatch < 0) {
            return dataMatch;
        }
        String categoryMismatch = matchCategories(categories);
        if (categoryMismatch != null) {
            return NO_MATCH_CATEGORY;
        }
        return dataMatch;
    }

    /** AOSP match(resolver, intent, resolve, logTag): convenience overload
     *  that reads the action/type/scheme/data/categories off the intent. */
    public final int match(ContentResolver resolver, Intent intent,
                           boolean resolve, String logTag) {
        // AOSP verbatim: the ONLY difference between resolve=true and false is
        // which type getter is used, and Intent.resolveType is itself the
        // method that asks the ContentResolver for a content:// Uri's MIME
        // type. Re-deriving that logic here (as this method used to) meant the
        // two paths could drift apart.
        String type = resolve ? intent.resolveType(resolver) : intent.getType();
        return match(intent.getAction(), type, intent.getScheme(),
                     intent.getData(), intent.getCategories(), logTag);
    }

    // ---- Parcelable ----
    //
    // Field order mirrors frameworks/base/core/java/android/content/
    // IntentFilter.java:2260-2324 (writeToParcel) and :2354-2404 (the Parcel
    // constructor), android11-release.
    //
    // ⚠ Until v1.0.940 this was an empty body plus a CREATOR that answered a
    // blank filter. That was invisible while android.os.Parcel faked
    // marshalling with a side list of live objects; now that it marshals for
    // real, a registerReceiver/queryBroadcastReceivers round trip would hand
    // back a filter that matches NOTHING, with no error to say why.
    //
    // AOSP keeps five list fields this tree does not have, and it splits one
    // boolean this tree keeps whole. Rather than compress the layout, each
    // absent field keeps its slot and is written as AOSP's own "absent" value,
    // so the wire shape stays AOSP-compatible in both directions and adding
    // the field later is a value change rather than a format break:
    //   mStaticDataTypes / mMimeGroups -- written as 0 ("null list"). AOSP
    //       added both for MIME-group support in package-manager land; this
    //       tree keeps only the resolved mDataTypes.
    //   mHasDynamicPartialTypes -- this tree keeps a single mHasPartialTypes
    //       (AOSP's hasPartialTypes() is the OR of the static and dynamic
    //       flags), so it is written into the STATIC slot and the dynamic slot
    //       carries 0; the reader ORs them back, which is exactly AOSP's own
    //       predicate and so survives reading a filter written by either side.
    //   autoVerify / instantAppVisibility / order -- written as 0. All three
    //       are system-server resolution policy (link verification, instant-app
    //       exposure, resolution ordering) with nothing on this tree to set or
    //       read them.
    // REMOVAL CONDITION for each: when the field exists on this class, marshal
    // it into the slot it already occupies below.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringList(mActions);
        // AOSP guards each list with a 1/0 present marker because its fields
        // are nullable. Ours are final and never null, so the marker is always
        // 1 -- kept anyway so a parcel written here is readable by AOSP's
        // constructor and vice versa.
        dest.writeInt(1);
        dest.writeStringList(mCategories);
        dest.writeInt(1);
        dest.writeStringList(mDataSchemes);
        dest.writeInt(0);                      // mStaticDataTypes: absent here
        dest.writeInt(1);
        dest.writeStringList(mDataTypes);
        dest.writeInt(0);                      // mMimeGroups: absent here

        dest.writeInt(mDataSchemeSpecificParts.size());
        for (PatternMatcher p : mDataSchemeSpecificParts) p.writeToParcel(dest, flags);
        dest.writeInt(mDataAuthorities.size());
        for (AuthorityEntry a : mDataAuthorities) a.writeToParcel(dest);
        dest.writeInt(mDataPaths.size());
        for (PatternMatcher p : mDataPaths) p.writeToParcel(dest, flags);

        dest.writeInt(mPriority);
        dest.writeInt(mHasPartialTypes ? 1 : 0);   // AOSP's STATIC flag slot
        dest.writeInt(0);                          // mHasDynamicPartialTypes
        dest.writeInt(0);                          // autoVerify
        dest.writeInt(0);                          // instantAppVisibility
        dest.writeInt(0);                          // order
    }

    /** AOSP's `public IntentFilter(Parcel source)`, spelled as an instance
     *  reader because this class's list fields are final and so must be
     *  refilled rather than reassigned. Every list is cleared first, so
     *  reading into a filter that already has entries replaces them rather
     *  than appending -- which is what AOSP's fresh-object constructor does. */
    private void readFromParcel(Parcel source) {
        mActions.clear();
        source.readStringList(mActions);
        mCategories.clear();
        if (source.readInt() != 0) source.readStringList(mCategories);
        mDataSchemes.clear();
        if (source.readInt() != 0) source.readStringList(mDataSchemes);
        if (source.readInt() != 0) {
            // mStaticDataTypes slot: consume the payload if a writer that HAS
            // the field produced one, then discard it -- mDataTypes below is
            // the resolved list this tree matches against.
            source.createStringArrayList();
        }
        mDataTypes.clear();
        if (source.readInt() != 0) source.readStringList(mDataTypes);
        if (source.readInt() != 0) source.createStringArrayList();   // mMimeGroups slot

        mDataSchemeSpecificParts.clear();
        int n = source.readInt();
        for (int i = 0; i < n; i++) {
            mDataSchemeSpecificParts.add(PatternMatcher.CREATOR.createFromParcel(source));
        }
        mDataAuthorities.clear();
        n = source.readInt();
        for (int i = 0; i < n; i++) mDataAuthorities.add(new AuthorityEntry(source));
        mDataPaths.clear();
        n = source.readInt();
        for (int i = 0; i < n; i++) {
            mDataPaths.add(PatternMatcher.CREATOR.createFromParcel(source));
        }

        mPriority = source.readInt();
        // AOSP's hasPartialTypes() is (static || dynamic); this tree keeps the
        // OR directly, so read both slots and fold them the same way.
        boolean staticPartial  = source.readInt() > 0;
        boolean dynamicPartial = source.readInt() > 0;
        mHasPartialTypes = staticPartial || dynamicPartial;
        source.readInt();   // autoVerify slot
        source.readInt();   // instantAppVisibility slot
        source.readInt();   // order slot
    }

    public static final Creator<IntentFilter> CREATOR = new Creator<IntentFilter>() {
        @Override public IntentFilter createFromParcel(Parcel source) {
            IntentFilter f = new IntentFilter();
            f.readFromParcel(source);
            return f;
        }
        @Override public IntentFilter[] newArray(int size) { return new IntentFilter[size]; }
    };

    /** AOSP IntentFilter.MalformedMimeTypeException thrown by addDataType. */
    public static class MalformedMimeTypeException extends android.util.AndroidException {
        public MalformedMimeTypeException() {}
        public MalformedMimeTypeException(String name) { super(name); }
    }

    /** AOSP IntentFilter.AuthorityEntry: a host (optionally wildcarded with a
     *  leading '*') plus an optional port. */
    public static final class AuthorityEntry {
        private final String mOrigHost;
        private final String mHost;
        private final boolean mWild;
        private final int mPort;

        public AuthorityEntry(String host, String port) {
            mOrigHost = host;
            mWild = host != null && host.length() > 0 && host.charAt(0) == '*';
            mHost = mWild ? host.substring(1) : host;
            mPort = (port != null) ? Integer.parseInt(port) : -1;
        }

        /** AOSP AuthorityEntry(Parcel) / writeToParcel(Parcel)
         *  (IntentFilter.java:1093-1105, android11-release). AuthorityEntry is
         *  NOT itself a Parcelable on AOSP -- IntentFilter flattens each entry
         *  inline -- so these are package-private and take no flags.
         *
         *  All four fields travel, including the two that are derivable from
         *  mOrigHost. AOSP writes them anyway, and it matters here: mWild and
         *  mHost are computed by the (host, port) constructor, and rebuilding
         *  through it would re-parse the port from a String we no longer have
         *  and would throw on the -1 ("no port") case. */
        AuthorityEntry(Parcel src) {
            mOrigHost = src.readString();
            mHost = src.readString();
            mWild = src.readInt() != 0;
            mPort = src.readInt();
        }

        void writeToParcel(Parcel dest) {
            dest.writeString(mOrigHost);
            dest.writeString(mHost);
            dest.writeInt(mWild ? 1 : 0);
            dest.writeInt(mPort);
        }

        public String getHost() { return mOrigHost; }
        public int getPort() { return mPort; }

        /** AOSP AuthorityEntry.match: returns a MATCH_CATEGORY_HOST/PORT result
         *  or NO_MATCH_DATA. AOSP does NOT null-check `data` -- it dereferences
         *  it immediately, so a null Uri is a NullPointerException. (The
         *  null-tolerant entry point is IntentFilter.matchDataAuthority, which
         *  guards before ever reaching here.) The wildcard case compares the
         *  host's TAIL case-insensitively, which is why the suffix is sliced
         *  off first and then compareToIgnoreCase'd rather than region-matched
         *  case-sensitively. */
        public int match(Uri data) {
            String host = data.getHost();
            if (host == null) return NO_MATCH_DATA;
            if (mWild) {
                if (host.length() < mHost.length()) return NO_MATCH_DATA;
                host = host.substring(host.length() - mHost.length());
            }
            if (host.compareToIgnoreCase(mHost) != 0) return NO_MATCH_DATA;
            if (mPort >= 0) {
                if (mPort != data.getPort()) return NO_MATCH_DATA;
                return MATCH_CATEGORY_PORT;
            }
            return MATCH_CATEGORY_HOST;
        }
    }
}
