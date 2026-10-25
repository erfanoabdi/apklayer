// android.net.Uri -- mirrors AOSP core/java/android/net/Uri.java
// (android-11.0.0_r48).
//
// The load-bearing contract, quoted from AOSP's own class javadoc: "this
// class performs little to no validation ... it will return garbage rather
// than throw an exception unless otherwise specified." parse() therefore
// never validates; StringUri below splits scheme/authority/path/query/
// fragment with plain index scans, exactly like frameworks/base.
//
// This used to wrap java.net.URI, which is a STRICT RFC-3986 validator. Any
// string it rejected (a raw space is the common trigger) left every
// component null while toString() still printed the original intact, so
// `uri.getScheme().equals(...)` NPE'd and getHost()/getPath() routing
// silently mis-routed -- for SAF content:// URIs whose path holds a real
// filename with a space, geo: URIs with an address query, mailto: with a
// display name, and loosely-encoded attribution/deep-link URIs.
//
// THREE Uri implementations, not one (this is the shape AOSP actually has,
// and flattening it to "a String plus a Builder that concatenates" is what
// produced ~24 answer divergences against the emulator):
//
//   StringUri       -- what Uri.parse() returns. Lazy index scans over the
//                      original string; every component is already ENCODED.
//   OpaqueUri       -- Uri.fromParts() and Builder.build() with an opaquePart:
//                      "scheme:ssp#fragment", no authority/path/query.
//   HierarchicalUri -- Builder.build() and Uri.fromFile(): components held as
//                      Part/PathPart and re-serialised on toString().
//
// The Part abstraction is not decoration. It carries TWO facts that a bare
// String cannot: (a) whether the value it holds is the ENCODED or the DECODED
// form, so Builder.authority("u:p@h") can percent-encode while
// Builder.encodedAuthority("u:p@h") does not; and (b) NULL vs EMPTY, which is
// what makes makeUriString() emit '?' / '#' only for a non-empty query /
// fragment. Uri.parse("http://h/p#").buildUpon().build() therefore LOSES the
// '#' on a real device -- that is Part.EMPTY.isEmpty() at Uri.java:1327, not a
// bug to fix.
package android.net;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public abstract class Uri implements Parcelable, Comparable<Uri> {

    /** Index of a character that was searched for and not found. */
    private static final int NOT_FOUND = -1;

    /** Placeholder value for an index which hasn't been calculated yet. */
    private static final int NOT_CALCULATED = -2;

    /** Error message presented when a user tries to treat an opaque URI as
     *  hierarchical. AOSP Uri.java:444. */
    private static final String NOT_HIERARCHICAL = "This isn't a hierarchical URI.";

    /** Default encoding. */
    private static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * NOTE: EMPTY accesses this field during its own initialization, so this
     * field *must* stay declared FIRST, or else EMPTY will see a null value.
     *
     * Placeholder for strings which haven't been cached. This enables us to
     * cache null. The instance is deliberately fresh (`new String`) so it can be
     * compared by IDENTITY and can never be confused with app data. It is an
     * alias of AbstractPart.NOT_CACHED; see there for why it lives there.
     */
    private static final String NOT_CACHED = AbstractPart.NOT_CACHED;

    /** The empty URI, equivalent to "". */
    public static final Uri EMPTY = new HierarchicalUri(null, Part.NULL,
            PathPart.EMPTY, Part.NULL, Part.NULL);

    public abstract boolean isHierarchical();

    /** Opaque is simply the negation of hierarchical; only one of the two is
     *  ever overridden by a subclass. */
    public boolean isOpaque() { return !isHierarchical(); }

    public abstract boolean isRelative();

    public boolean isAbsolute() { return !isRelative(); }

    public abstract String getScheme();
    public abstract String getSchemeSpecificPart();
    public abstract String getEncodedSchemeSpecificPart();
    public abstract String getAuthority();
    public abstract String getEncodedAuthority();
    public abstract String getUserInfo();
    public abstract String getEncodedUserInfo();
    public abstract String getHost();
    public abstract int    getPort();
    public abstract String getPath();
    public abstract String getEncodedPath();
    public abstract String getQuery();
    public abstract String getEncodedQuery();
    public abstract String getFragment();
    public abstract String getEncodedFragment();
    public abstract List<String> getPathSegments();
    public abstract String getLastPathSegment();
    public abstract String toString();
    public abstract Builder buildUpon();

    @Override public boolean equals(Object o) {
        if (!(o instanceof Uri)) return false;
        Uri other = (Uri) o;
        return toString().equals(other.toString());
    }

    @Override public int hashCode() { return toString().hashCode(); }

    @Override public int compareTo(Uri other) { return toString().compareTo(other.toString()); }

    /**
     * Creates a Uri which parses the given encoded URI string.
     *
     * AOSP Uri.java:457 -> new StringUri(uriString), whose constructor throws
     * NullPointerException("uriString"). There is deliberately no "empty Uri"
     * fallback: an app that reaches here with null has a bug, and swallowing it
     * turns a stack trace at the real call site into mis-routing far away.
     *
     * @throws NullPointerException if uriString is null
     */
    public static Uri parse(String uriString) {
        return new StringUri(uriString);
    }

    /**
     * Creates a Uri from a file. The URI has the form "file://<absolute path>".
     * Encodes path characters with the exception of '/'.
     *
     * AOSP Uri.java:471. The path goes in as the DECODED form, so a space
     * becomes %20 and a '#' becomes %23 on toString() -- without that, a file
     * whose name contains '#' or '?' produces a Uri string whose fragment /
     * query separator lands in the middle of the filename.
     *
     * @throws NullPointerException if file is null
     */
    public static Uri fromFile(File file) {
        if (file == null) {
            throw new NullPointerException("file");
        }
        PathPart path = PathPart.fromDecoded(file.getAbsolutePath());
        return new HierarchicalUri("file", Part.EMPTY, path, Part.NULL, Part.NULL);
    }

    /**
     * Creates an opaque Uri from the given components. ENCODES the ssp, which
     * means this method cannot be used to create hierarchical URIs.
     * AOSP Uri.java:845.
     *
     * @throws NullPointerException if scheme or ssp is null
     */
    public static Uri fromParts(String scheme, String ssp, String fragment) {
        if (scheme == null) {
            throw new NullPointerException("scheme");
        }
        if (ssp == null) {
            throw new NullPointerException("ssp");
        }
        return new OpaqueUri(scheme, Part.fromDecoded(ssp), Part.fromDecoded(fragment));
    }

    /**
     * Creates a new Uri by appending an ALREADY-ENCODED path segment to a base
     * Uri. AOSP Uri.java:2340 calls appendEncodedPath, not appendPath: the
     * segment goes in verbatim, so a caller holding a raw string must encode it
     * itself and a caller holding an encoded one is not double-encoded.
     *
     * @throws NullPointerException if baseUri is null
     */
    public static Uri withAppendedPath(Uri baseUri, String pathSegment) {
        Builder builder = baseUri.buildUpon();
        builder = builder.appendEncodedPath(pathSegment);
        return builder.build();
    }

    // ---------------------------------------------------------------------
    // Query parameters. All three readers live on the base class, all three
    // throw UnsupportedOperationException on an opaque Uri, and all three throw
    // NullPointerException on a null key -- an app uses that throw to detect a
    // mailto:/tel: and returning null instead sends it down the wrong branch.
    //
    // They also DISAGREE WITH EACH OTHER, on purpose-by-accident, and the
    // disagreement is mirrored rather than "corrected" (we reimplement
    // observable behaviour, not our idea of correctness):
    //   * getQueryParameter encodes the lookup key with Uri.encode  (space -> %20)
    //     while getQueryParameters encodes it with URLEncoder       (space -> +),
    //     so for a key containing a space the two return DIFFERENT answers on a
    //     real device (Uri.java:1720 vs :1658).
    //   * getQueryParameter decodes the value with convertPlus=TRUE  (:1738)
    //     while getQueryParameters uses plain Uri.decode, convertPlus=FALSE,
    //     so "?q=a+b" reads back as "a b" from one and "a+b" from the other.
    // ---------------------------------------------------------------------

    /**
     * Returns a set of the unique names of all query parameters. Iterating over
     * the set returns the names in order of first occurrence.
     * AOSP Uri.java:1603.
     *
     * @throws UnsupportedOperationException if this isn't a hierarchical URI
     */
    public Set<String> getQueryParameterNames() {
        if (isOpaque()) {
            throw new UnsupportedOperationException(NOT_HIERARCHICAL);
        }

        String query = getEncodedQuery();
        if (query == null) {
            return Collections.emptySet();
        }

        Set<String> names = new LinkedHashSet<String>();
        int start = 0;
        // A do/while, so an EMPTY query ("http://h/p?") yields one EMPTY name
        // rather than none, and "a=1&&b=2" yields three names not two. Empty
        // names are NOT skipped.
        do {
            int next = query.indexOf('&', start);
            int end = (next == -1) ? query.length() : next;

            int separator = query.indexOf('=', start);
            if (separator > end || separator == -1) {
                separator = end;
            }

            String name = query.substring(start, separator);
            names.add(decode(name));

            // Move start to end of name.
            start = end + 1;
        } while (start < query.length());

        return Collections.unmodifiableSet(names);
    }

    /**
     * Searches the query string for parameter values with the given key.
     * AOSP Uri.java:1643.
     *
     * @param key which will be encoded -- with URLEncoder, NOT Uri.encode; see
     *  the note above the block. Do not "fix" this to match getQueryParameter.
     * @throws UnsupportedOperationException if this isn't a hierarchical URI
     * @throws NullPointerException if key is null
     */
    public List<String> getQueryParameters(String key) {
        if (isOpaque()) {
            throw new UnsupportedOperationException(NOT_HIERARCHICAL);
        }
        if (key == null) {
            throw new NullPointerException("key");
        }

        String query = getEncodedQuery();
        if (query == null) {
            return Collections.emptyList();
        }

        String encodedKey;
        try {
            encodedKey = URLEncoder.encode(key, DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }

        ArrayList<String> values = new ArrayList<String>();

        int start = 0;
        do {
            int nextAmpersand = query.indexOf('&', start);
            int end = nextAmpersand != -1 ? nextAmpersand : query.length();

            int separator = query.indexOf('=', start);
            if (separator > end || separator == -1) {
                separator = end;
            }

            if (separator - start == encodedKey.length()
                    && query.regionMatches(start, encodedKey, 0, encodedKey.length())) {
                if (separator == end) {
                    values.add("");
                } else {
                    values.add(decode(query.substring(separator + 1, end)));
                }
            }

            // Move start to end of name.
            if (nextAmpersand != -1) {
                start = nextAmpersand + 1;
            } else {
                break;
            }
        } while (true);

        return Collections.unmodifiableList(values);
    }

    /**
     * Searches the query string for the first value with the given key.
     * AOSP Uri.java:1707.
     *
     * @param key which will be encoded -- with Uri.encode, NOT URLEncoder.
     * @throws UnsupportedOperationException if this isn't a hierarchical URI
     * @throws NullPointerException if key is null
     * @return the decoded value or null if no parameter is found
     */
    public String getQueryParameter(String key) {
        if (isOpaque()) {
            throw new UnsupportedOperationException(NOT_HIERARCHICAL);
        }
        if (key == null) {
            throw new NullPointerException("key");
        }

        final String query = getEncodedQuery();
        if (query == null) {
            return null;
        }

        final String encodedKey = encode(key, null);
        final int length = query.length();
        int start = 0;
        do {
            int nextAmpersand = query.indexOf('&', start);
            int end = nextAmpersand != -1 ? nextAmpersand : length;

            int separator = query.indexOf('=', start);
            if (separator > end || separator == -1) {
                separator = end;
            }

            if (separator - start == encodedKey.length()
                    && query.regionMatches(start, encodedKey, 0, encodedKey.length())) {
                if (separator == end) {
                    return "";
                } else {
                    String encodedValue = query.substring(separator + 1, end);
                    // convertPlus=TRUE here and ONLY here: the value half of a
                    // query pair is read form-urlencoded, so "?q=a+b" is "a b".
                    return UriCodec.decode(encodedValue, true, StandardCharsets.UTF_8, false);
                }
            }

            // Move start to end of name.
            if (nextAmpersand != -1) {
                start = nextAmpersand + 1;
            } else {
                break;
            }
        } while (true);
        return null;
    }

    /**
     * Searches the query string for the first value with the given key and
     * interprets it as a boolean. "false" and "0" are false, everything else is
     * true. AOSP Uri.java:1761.
     */
    public boolean getBooleanQueryParameter(String key, boolean defaultValue) {
        String flag = getQueryParameter(key);
        if (flag == null) {
            return defaultValue;
        }
        flag = flag.toLowerCase(Locale.ROOT);
        return (!"false".equals(flag) && !"0".equals(flag));
    }

    /** AOSP Uri.normalizeScheme: lowercases the scheme. */
    public Uri normalizeScheme() {
        String scheme = getScheme();
        if (scheme == null) return this;                 // give up
        String lowerScheme = scheme.toLowerCase(Locale.ROOT);
        if (scheme.equals(lowerScheme)) return this;     // no change
        return buildUpon().scheme(lowerScheme).build();
    }

    public boolean isPathPrefixMatch(Uri prefix) {
        if (prefix == null) return false;
        return toString().startsWith(prefix.toString());
    }

    // ---------------------------------------------------------------------
    // Parcelling. AOSP writes a type id plus each Part; we write the encoded
    // string and re-parse, which is observably identical for the toString()
    // round trip every Intent-data / ClipData path relies on.
    // ---------------------------------------------------------------------

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { dest.writeString(toString()); }

    public static final Creator<Uri> CREATOR = new Creator<Uri>() {
        @Override public Uri createFromParcel(Parcel s) {
            String v = s.readString();
            // Keep this null guard: the static writer below encodes a null Uri
            // as a null string, and Uri.parse(null) now throws as AOSP does.
            return v == null ? null : parse(v);
        }
        @Override public Uri[] newArray(int size) { return new Uri[size]; }
    };

    /**
     * AOSP's null-tolerant static writer. The instance writeToParcel cannot
     * encode a null Uri, so every AOSP caller that may hold one (Intent data,
     * ClipData items, InputContentInfo) uses this form instead; CREATOR reads
     * the null back symmetrically.
     */
    public static void writeToParcel(Parcel out, Uri uri) {
        out.writeString(uri == null ? null : uri.toString());
    }

    // ---------------------------------------------------------------------
    // encode / decode
    // ---------------------------------------------------------------------

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    /** AOSP Uri.encode(s): percent-encodes every byte that is NOT one of the
     *  unreserved characters [A-Za-z0-9_-!.~'()*] using uppercase %XX.
     *  Crucially, unlike java.net.URLEncoder, AOSP encodes a space as %20 (NOT
     *  '+') -- '+' is a literal in a URI path/query and only means "space" in
     *  the legacy application/x-www-form-urlencoded form context. Apps
     *  round-trip values through Uri.encode/Uri.decode and a '+'-for-space
     *  mismatch corrupts values containing spaces. AOSP Uri.java:1852. */
    public static String encode(String s) {
        return encode(s, null);
    }

    /** AOSP Uri.encode(s, allow): like encode(s) but ALSO leaves the characters
     *  in `allow` un-encoded (callers pass e.g. "/" to keep path separators
     *  literal). AOSP Uri.java:1869. */
    public static String encode(String s, String allow) {
        if (s == null) return null;
        StringBuilder out = null; // lazily allocated; null => nothing encoded yet
        // We walk the ORIGINAL string char-by-char to honour `allow`, but
        // percent-encode at the UTF-8 BYTE level, grouping a surrogate pair so
        // its four bytes are emitted as one code point rather than two
        // unpaired-surrogate replacements.
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            boolean safe = isAllowed(c, allow);
            if (safe) {
                if (out != null) out.append(c);
            } else {
                if (out == null) {
                    out = new StringBuilder(s.length() + 16);
                    out.append(s, 0, i);
                }
                String piece;
                if (Character.isHighSurrogate(c) && i + 1 < len
                        && Character.isLowSurrogate(s.charAt(i + 1))) {
                    piece = s.substring(i, i + 2);
                    i++;
                } else {
                    piece = String.valueOf(c);
                }
                for (byte b : piece.getBytes(StandardCharsets.UTF_8)) {
                    out.append('%');
                    out.append(HEX_DIGITS[(b >> 4) & 0xF]);
                    out.append(HEX_DIGITS[b & 0xF]);
                }
            }
        }
        return out == null ? s : out.toString();
    }

    /** The AOSP "unreserved" set kept literal by Uri.encode, plus the caller's
     *  `allow`. AOSP Uri.java:1956. */
    private static boolean isAllowed(char c, String allow) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || "_-!.~'()*".indexOf(c) != NOT_FOUND
                || (allow != null && allow.indexOf(c) != NOT_FOUND);
    }

    /**
     * Decodes '%'-escaped octets using UTF-8. Replaces INVALID octets with
     * U+FFFD -- it does NOT pass a malformed escape through, so "100%" decodes
     * to "100�" and not "100%". AOSP Uri.java:1973.
     */
    public static String decode(String s) {
        if (s == null) {
            return null;
        }
        // Fast path, provably output-identical: with convertPlus=false and no
        // '%' present, UriCodec's loop only ever reaches its `default:` arm (and
        // the '+' arm, which re-appends '+'), so it rebuilds `s` verbatim.
        if (s.indexOf('%') < 0) {
            return s;
        }
        return UriCodec.decode(
                s, false /* convertPlus */, StandardCharsets.UTF_8, false /* throwOnFailure */);
    }

    // ---------------------------------------------------------------------
    // Part / PathPart: an immutable wrapper holding the encoded and decoded
    // versions of one URI component, lazily deriving whichever it was not built
    // from. AOSP Uri.java:1984-2328.
    // ---------------------------------------------------------------------

    static abstract class AbstractPart {

        /** The one AbstractPart.NOT_CACHED instance. It lives HERE, not in Uri, so that the
         *  Part family can read it without running Uri's static initializer;
         *  every class in this file reads it from here.
         *
         *  When it lived in Uri, a process whose FIRST Uri touch was a Part (a
         *  Uri.Builder call, which is how React Native's image views first meet
         *  Uri) ran Part.<clinit> -> new EmptyPart -> AbstractPart read
         *  Uri.NOT_CACHED -> Uri.<clinit> -> EMPTY = new HierarchicalUri(null,
         *  Part.NULL, ...) while Part.NULL was still null, so EMPTY kept null
         *  parts and EMPTY.toString() / equals(EMPTY) threw NPE for the rest of
         *  the process. AOSP has the same cycle but never sees it: the zygote
         *  initializes Uri in the boot image before any app code runs.
         *  (Efteling 5.25.0, 2026-10-07: ReactImageView.setSource compares
         *  against Uri.EMPTY; the NPE aborted the Fabric mount and the screen
         *  stayed blank.) A field, not a new holder class, so FRAMEWORK_ID
         *  (class presence) does not move. */
        @SuppressWarnings("StringOperationCanBeSimplified")
        static final String NOT_CACHED = new String("NOT CACHED");

        // Possible values of mCanonicalRepresentation.
        static final int REPRESENTATION_ENCODED = 1;
        static final int REPRESENTATION_DECODED = 2;

        volatile String encoded;
        volatile String decoded;
        private final int mCanonicalRepresentation;

        AbstractPart(String encoded, String decoded) {
            // Identity comparison against AbstractPart.NOT_CACHED, not equals(): a caller may
            // legitimately supply the literal text "NOT CACHED".
            if (encoded != AbstractPart.NOT_CACHED) {
                this.mCanonicalRepresentation = REPRESENTATION_ENCODED;
                this.encoded = encoded;
                this.decoded = AbstractPart.NOT_CACHED;
            } else if (decoded != AbstractPart.NOT_CACHED) {
                this.mCanonicalRepresentation = REPRESENTATION_DECODED;
                this.encoded = AbstractPart.NOT_CACHED;
                this.decoded = decoded;
            } else {
                throw new IllegalArgumentException("Neither encoded nor decoded");
            }
        }

        abstract String getEncoded();

        final String getDecoded() {
            boolean hasDecoded = decoded != AbstractPart.NOT_CACHED;
            return hasDecoded ? decoded : (decoded = decode(encoded));
        }
    }

    static class Part extends AbstractPart {

        /** A part with null values. */
        static final Part NULL = new EmptyPart(null);

        /** A part with empty strings for values. */
        static final Part EMPTY = new EmptyPart("");

        private Part(String encoded, String decoded) {
            super(encoded, decoded);
        }

        boolean isEmpty() {
            return false;
        }

        String getEncoded() {
            boolean hasEncoded = encoded != AbstractPart.NOT_CACHED;
            return hasEncoded ? encoded : (encoded = encode(decoded));
        }

        /** Returns the given part, or {@link #NULL} if it is null. */
        static Part nonNull(Part part) {
            return part == null ? NULL : part;
        }

        static Part fromEncoded(String encoded) {
            return from(encoded, AbstractPart.NOT_CACHED);
        }

        static Part fromDecoded(String decoded) {
            return from(AbstractPart.NOT_CACHED, decoded);
        }

        static Part from(String encoded, String decoded) {
            // Both sides are checked because either one may be AbstractPart.NOT_CACHED. The
            // null-vs-empty distinction produced here is exactly what
            // makeUriString() reads to decide whether to emit '?' / '#'.
            if (encoded == null) {
                return NULL;
            }
            if (encoded.length() == 0) {
                return EMPTY;
            }
            if (decoded == null) {
                return NULL;
            }
            if (decoded.length() == 0) {
                return EMPTY;
            }
            return new Part(encoded, decoded);
        }

        private static class EmptyPart extends Part {
            EmptyPart(String value) {
                super(value, value);
                if (value != null && !value.isEmpty()) {
                    throw new IllegalArgumentException("Expected empty value, got: " + value);
                }
                // Avoid having to re-calculate the non-canonical value.
                encoded = decoded = value;
            }

            @Override boolean isEmpty() {
                return true;
            }
        }
    }

    static class PathPart extends AbstractPart {

        /** A part with null values. */
        static final PathPart NULL = new PathPart(null, null);

        /** A part with empty strings for values. */
        static final PathPart EMPTY = new PathPart("", "");

        private PathPart(String encoded, String decoded) {
            super(encoded, decoded);
        }

        String getEncoded() {
            boolean hasEncoded = encoded != AbstractPart.NOT_CACHED;
            // Don't encode '/'.
            return hasEncoded ? encoded : (encoded = encode(decoded, "/"));
        }

        /**
         * Appends an ALREADY-ENCODED segment. AOSP Uri.java:2213. Two shapes
         * worth knowing: with no old path at all the result is made ABSOLUTE
         * ("/b", not "b"), and a null segment is concatenated as the four
         * characters "null" rather than skipped.
         */
        static PathPart appendEncodedSegment(PathPart oldPart, String newSegment) {
            // If there is no old path, should the new path be relative or
            // absolute? AOSP picks absolute.
            if (oldPart == null) {
                return fromEncoded("/" + newSegment);
            }

            String oldPath = oldPart.getEncoded();

            if (oldPath == null) {
                oldPath = "";
            }

            int oldPathLength = oldPath.length();
            String newPath;
            if (oldPathLength == 0) {
                // No old path.
                newPath = "/" + newSegment;
            } else if (oldPath.charAt(oldPathLength - 1) == '/') {
                newPath = oldPath + newSegment;
            } else {
                newPath = oldPath + "/" + newSegment;
            }

            return fromEncoded(newPath);
        }

        static PathPart appendDecodedSegment(PathPart oldPart, String decoded) {
            String encoded = encode(decoded);
            return appendEncodedSegment(oldPart, encoded);
        }

        static PathPart fromEncoded(String encoded) {
            return from(encoded, AbstractPart.NOT_CACHED);
        }

        static PathPart fromDecoded(String decoded) {
            return from(AbstractPart.NOT_CACHED, decoded);
        }

        static PathPart from(String encoded, String decoded) {
            if (encoded == null) {
                return NULL;
            }
            if (encoded.length() == 0) {
                return EMPTY;
            }
            return new PathPart(encoded, decoded);
        }

        /**
         * Prepends path values with "/" if they're present, not empty, and don't
         * already start with "/". AOSP Uri.java:2302.
         */
        static PathPart makeAbsolute(PathPart oldPart) {
            boolean encodedCached = oldPart.encoded != AbstractPart.NOT_CACHED;

            // We don't care which version we use, and we don't want to force
            // unnecessary encoding/decoding.
            String oldPath = encodedCached ? oldPart.encoded : oldPart.decoded;

            if (oldPath == null || oldPath.length() == 0 || oldPath.startsWith("/")) {
                return oldPart;
            }

            String newEncoded = encodedCached ? "/" + oldPart.encoded : AbstractPart.NOT_CACHED;

            boolean decodedCached = oldPart.decoded != AbstractPart.NOT_CACHED;
            String newDecoded = decodedCached ? "/" + oldPart.decoded : AbstractPart.NOT_CACHED;

            return new PathPart(newEncoded, newDecoded);
        }
    }

    // ---------------------------------------------------------------------
    // Shared hierarchical behaviour: userinfo / host / port / segments, all
    // derived from the ENCODED authority so an encoded '@' or ':' inside
    // userinfo cannot move a split point. AOSP Uri.java:1066.
    // ---------------------------------------------------------------------

    private abstract static class AbstractHierarchicalUri extends Uri {

        @Override public String getLastPathSegment() {
            List<String> segments = getPathSegments();
            int size = segments.size();
            if (size == 0) {
                return null;
            }
            return segments.get(size - 1);
        }

        /** AOSP splits the ENCODED path on '/' and decodes each segment, so a
         *  percent-encoded '/' inside a segment (%2F) stays part of that segment
         *  instead of splitting it. AOSP Uri.java:2180. */
        @Override public List<String> getPathSegments() {
            String p = getEncodedPath();
            if (p == null || p.isEmpty()) return Collections.emptyList();
            ArrayList<String> out = new ArrayList<>();
            int previous = 0;
            int current;
            while ((current = p.indexOf('/', previous)) > -1) {
                // Keeps us from adding a segment when the path starts with '/',
                // and an empty segment for "//".
                if (previous < current) out.add(decode(p.substring(previous, current)));
                previous = current + 1;
            }
            if (previous < p.length()) out.add(decode(p.substring(previous)));
            return out;
        }

        private Part userInfo;

        private Part getUserInfoPart() {
            return userInfo == null ? userInfo = Part.fromEncoded(parseUserInfo()) : userInfo;
        }

        @Override public final String getEncodedUserInfo() {
            return getUserInfoPart().getEncoded();
        }

        private String parseUserInfo() {
            String authority = getEncodedAuthority();
            if (authority == null) {
                return null;
            }
            int end = authority.lastIndexOf('@');
            return end == NOT_FOUND ? null : authority.substring(0, end);
        }

        @Override public String getUserInfo() {
            return getUserInfoPart().getDecoded();
        }

        private volatile String host = AbstractPart.NOT_CACHED;

        @Override public String getHost() {
            boolean cached = (host != AbstractPart.NOT_CACHED);
            return cached ? host : (host = parseHost());
        }

        private String parseHost() {
            final String authority = getEncodedAuthority();
            if (authority == null) {
                return null;
            }
            // Parse out user info and then port.
            int userInfoSeparator = authority.lastIndexOf('@');
            int portSeparator = findPortSeparator(authority);

            String encodedHost = portSeparator == NOT_FOUND
                    ? authority.substring(userInfoSeparator + 1)
                    : authority.substring(userInfoSeparator + 1, portSeparator);

            return decode(encodedHost);
        }

        private volatile int port = NOT_CALCULATED;

        @Override public int getPort() {
            return port == NOT_CALCULATED ? port = parsePort() : port;
        }

        private int parsePort() {
            final String authority = getEncodedAuthority();
            int portSeparator = findPortSeparator(authority);
            if (portSeparator == NOT_FOUND) {
                return -1;
            }
            String portString = decode(authority.substring(portSeparator + 1));
            try {
                return Integer.parseInt(portString);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        /** Reverse-scan for the ':' that opens the port, bailing out at the first
         *  character that is neither ':' nor an ASCII digit -- so a colon inside
         *  userinfo or an IPv6 literal is not mistaken for one. */
        private int findPortSeparator(String authority) {
            if (authority == null) {
                return NOT_FOUND;
            }
            for (int i = authority.length() - 1; i >= 0; --i) {
                final char character = authority.charAt(i);
                if (':' == character) return i;
                // Character.isDigit would include non-ascii digits.
                if (character < '0' || character > '9') return NOT_FOUND;
            }
            return NOT_FOUND;
        }
    }

    /** AOSP's StringUri: the original string plus lazy index-scan splitters.
     *  Every parse* method below is the AOSP algorithm verbatim in shape --
     *  first ':' is the scheme separator, "//" right after it opens the
     *  authority, the authority ends at the first '/' '\' '?' or '#', the
     *  path runs to the first '?' or '#', the query to the first '#'. */
    private static final class StringUri extends AbstractHierarchicalUri {

        private final String uriString;
        private volatile int cachedSsi = NOT_CALCULATED;
        private volatile int cachedFsi = NOT_CALCULATED;

        StringUri(String uriString) {
            if (uriString == null) {
                throw new NullPointerException("uriString");
            }
            this.uriString = uriString;
        }

        /** Finds the first ':'. Returns -1 if none found. */
        private int findSchemeSeparator() {
            return cachedSsi == NOT_CALCULATED
                ? cachedSsi = uriString.indexOf(':')
                : cachedSsi;
        }

        /** Finds the first '#' at or after the scheme separator. */
        private int findFragmentSeparator() {
            return cachedFsi == NOT_CALCULATED
                ? cachedFsi = uriString.indexOf('#', findSchemeSeparator())
                : cachedFsi;
        }

        @Override public boolean isHierarchical() {
            int ssi = findSchemeSeparator();
            if (ssi == NOT_FOUND) return true;                // relative URIs are hierarchical
            if (uriString.length() == ssi + 1) return false;   // no ssp
            return uriString.charAt(ssi + 1) == '/';           // ssp starting with '/'
        }
        @Override public boolean isRelative() { return findSchemeSeparator() == NOT_FOUND; }

        @Override public String getScheme() {
            int ssi = findSchemeSeparator();
            return ssi == NOT_FOUND ? null : uriString.substring(0, ssi);
        }

        /** Everything between the ':' and the '#'. */
        private String parseSsp() {
            int ssi = findSchemeSeparator();
            int fsi = findFragmentSeparator();
            return fsi == NOT_FOUND
                ? uriString.substring(ssi + 1)
                : uriString.substring(ssi + 1, fsi);
        }
        private Part ssp;
        private Part getSsp() {
            return ssp == null ? ssp = Part.fromEncoded(parseSsp()) : ssp;
        }
        @Override public String getEncodedSchemeSpecificPart() { return getSsp().getEncoded(); }
        @Override public String getSchemeSpecificPart() { return getSsp().getDecoded(); }

        /** Authority = the run after "//" up to the path/query/fragment. Per
         *  the URL spec a '\' is treated as a '/' when it appears in a host. */
        private String parseAuthority() {
            final int ssi = findSchemeSeparator();
            final int length = uriString.length();
            if (length > ssi + 2
                    && uriString.charAt(ssi + 1) == '/'
                    && uriString.charAt(ssi + 2) == '/') {
                int end = ssi + 3;
                while (end < length) {
                    char c = uriString.charAt(end);
                    if (c == '/' || c == '\\' || c == '?' || c == '#') break;
                    end++;
                }
                return uriString.substring(ssi + 3, end);
            }
            return null;
        }
        private Part authority;
        private Part getAuthorityPart() {
            return authority == null ? authority = Part.fromEncoded(parseAuthority()) : authority;
        }
        @Override public String getEncodedAuthority() { return getAuthorityPart().getEncoded(); }
        @Override public String getAuthority() { return getAuthorityPart().getDecoded(); }

        private String parsePath() {
            final int ssi = findSchemeSeparator();
            if (ssi > NOT_FOUND) {
                // Opaque URI: nothing after the ':', or the ssp is not '/'-led.
                if (ssi + 1 == uriString.length()) return null;
                if (uriString.charAt(ssi + 1) != '/') return null;
            }
            final int length = uriString.length();
            int pathStart;
            if (length > ssi + 2
                    && uriString.charAt(ssi + 1) == '/'
                    && uriString.charAt(ssi + 2) == '/') {
                // Skip over the authority to the path.
                pathStart = ssi + 3;
                while (pathStart < length) {
                    char c = uriString.charAt(pathStart);
                    if (c == '?' || c == '#') return "";   // empty path
                    if (c == '/' || c == '\\') break;
                    pathStart++;
                }
            } else {
                pathStart = ssi + 1;
            }
            int pathEnd = pathStart;
            while (pathEnd < length) {
                char c = uriString.charAt(pathEnd);
                if (c == '?' || c == '#') break;
                pathEnd++;
            }
            return uriString.substring(pathStart, pathEnd);
        }
        private PathPart path;
        private PathPart getPathPart() {
            return path == null ? path = PathPart.fromEncoded(parsePath()) : path;
        }
        @Override public String getEncodedPath() { return getPathPart().getEncoded(); }
        @Override public String getPath() { return getPathPart().getDecoded(); }

        private String parseQuery() {
            int qsi = uriString.indexOf('?', findSchemeSeparator());
            if (qsi == NOT_FOUND) return null;
            int fsi = findFragmentSeparator();
            if (fsi == NOT_FOUND) return uriString.substring(qsi + 1);
            if (fsi < qsi) return null;   // '#' before '?': no query
            return uriString.substring(qsi + 1, fsi);
        }
        private Part query;
        private Part getQueryPart() {
            return query == null ? query = Part.fromEncoded(parseQuery()) : query;
        }
        @Override public String getEncodedQuery() { return getQueryPart().getEncoded(); }
        @Override public String getQuery() { return getQueryPart().getDecoded(); }

        private String parseFragment() {
            int fsi = findFragmentSeparator();
            return fsi == NOT_FOUND ? null : uriString.substring(fsi + 1);
        }
        private Part fragment;
        private Part getFragmentPart() {
            return fragment == null ? fragment = Part.fromEncoded(parseFragment()) : fragment;
        }
        @Override public String getEncodedFragment() { return getFragmentPart().getEncoded(); }
        @Override public String getFragment() { return getFragmentPart().getDecoded(); }

        @Override public String toString() { return uriString; }

        /** AOSP Uri.java:812. Seeds the builder from the ENCODED components and
         *  preserves opacity, so rebuilding a mailto:/tel: URI does not turn its
         *  scheme-specific part into an authority. */
        @Override public Builder buildUpon() {
            if (isHierarchical()) {
                return new Builder()
                        .scheme(getScheme())
                        .authority(getAuthorityPart())
                        .path(getPathPart())
                        .query(getQueryPart())
                        .fragment(getFragmentPart());
            } else {
                return new Builder()
                        .scheme(getScheme())
                        .opaquePart(getSsp())
                        .fragment(getFragmentPart());
            }
        }
    }

    /** Opaque URI: "scheme:ssp#fragment". No authority, path, query, host,
     *  port or user info -- every one of those accessors answers null/-1/empty,
     *  and the three query readers throw. AOSP Uri.java:861. */
    private static final class OpaqueUri extends Uri {

        private final String scheme;
        private final Part ssp;
        private final Part fragment;

        OpaqueUri(String scheme, Part ssp, Part fragment) {
            this.scheme = scheme;
            this.ssp = ssp;
            this.fragment = fragment == null ? Part.NULL : fragment;
        }

        @Override public boolean isHierarchical() { return false; }
        @Override public boolean isRelative()     { return scheme == null; }

        @Override public String getScheme() { return this.scheme; }
        @Override public String getEncodedSchemeSpecificPart() { return ssp.getEncoded(); }
        @Override public String getSchemeSpecificPart() { return ssp.getDecoded(); }
        @Override public String getAuthority()          { return null; }
        @Override public String getEncodedAuthority()   { return null; }
        @Override public String getPath()               { return null; }
        @Override public String getEncodedPath()        { return null; }
        @Override public String getQuery()              { return null; }
        @Override public String getEncodedQuery()       { return null; }
        @Override public String getFragment()           { return fragment.getDecoded(); }
        @Override public String getEncodedFragment()    { return fragment.getEncoded(); }
        @Override public List<String> getPathSegments() { return Collections.emptyList(); }
        @Override public String getLastPathSegment()    { return null; }
        @Override public String getUserInfo()           { return null; }
        @Override public String getEncodedUserInfo()    { return null; }
        @Override public String getHost()               { return null; }
        @Override public int getPort()                  { return -1; }

        private volatile String cachedString = AbstractPart.NOT_CACHED;

        @Override public String toString() {
            boolean cached = cachedString != AbstractPart.NOT_CACHED;
            if (cached) {
                return cachedString;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append(':');
            sb.append(getEncodedSchemeSpecificPart());
            // EMPTY, not merely null: fromParts("mailto","x@y","") drops the '#'.
            if (!fragment.isEmpty()) {
                sb.append('#').append(fragment.getEncoded());
            }
            return cachedString = sb.toString();
        }

        @Override public Builder buildUpon() {
            return new Builder()
                    .scheme(this.scheme)
                    .opaquePart(this.ssp)
                    .fragment(this.fragment);
        }
    }

    /** Hierarchical URI assembled from Parts. AOSP Uri.java:1176. */
    private static final class HierarchicalUri extends AbstractHierarchicalUri {

        private final String scheme; // can be null
        private final Part authority;
        private final PathPart path;
        private final Part query;
        private final Part fragment;

        HierarchicalUri(String scheme, Part authority, PathPart path, Part query, Part fragment) {
            this.scheme = scheme;
            this.authority = Part.nonNull(authority);
            this.path = path == null ? PathPart.NULL : path;
            this.query = Part.nonNull(query);
            this.fragment = Part.nonNull(fragment);
        }

        @Override public boolean isHierarchical() { return true; }
        @Override public boolean isRelative()     { return scheme == null; }
        @Override public String getScheme()       { return scheme; }

        private Part ssp;

        private Part getSsp() {
            return ssp == null ? ssp = Part.fromEncoded(makeSchemeSpecificPart()) : ssp;
        }

        @Override public String getEncodedSchemeSpecificPart() { return getSsp().getEncoded(); }
        @Override public String getSchemeSpecificPart()        { return getSsp().getDecoded(); }

        private String makeSchemeSpecificPart() {
            StringBuilder builder = new StringBuilder();
            appendSspTo(builder);
            return builder.toString();
        }

        private void appendSspTo(StringBuilder builder) {
            String encodedAuthority = authority.getEncoded();
            if (encodedAuthority != null) {
                // Even if the authority is "", we still want to append "//"
                // -- that is what makes "file:///a/b" keep its third slash.
                builder.append("//").append(encodedAuthority);
            }

            String encodedPath = path.getEncoded();
            if (encodedPath != null) {
                builder.append(encodedPath);
            }

            // isEmpty(), not != null: a query set to "" contributes no '?'.
            if (!query.isEmpty()) {
                builder.append('?').append(query.getEncoded());
            }
        }

        @Override public String getAuthority()        { return this.authority.getDecoded(); }
        @Override public String getEncodedAuthority() { return this.authority.getEncoded(); }
        @Override public String getEncodedPath()      { return this.path.getEncoded(); }
        @Override public String getPath()             { return this.path.getDecoded(); }
        @Override public String getQuery()            { return this.query.getDecoded(); }
        @Override public String getEncodedQuery()     { return this.query.getEncoded(); }
        @Override public String getFragment()         { return this.fragment.getDecoded(); }
        @Override public String getEncodedFragment()  { return this.fragment.getEncoded(); }

        private volatile String uriString = AbstractPart.NOT_CACHED;

        @Override public String toString() {
            boolean cached = (uriString != AbstractPart.NOT_CACHED);
            return cached ? uriString : (uriString = makeUriString());
        }

        private String makeUriString() {
            StringBuilder builder = new StringBuilder();

            if (scheme != null) {
                builder.append(scheme).append(':');
            }

            appendSspTo(builder);

            // Same isEmpty() rule as the query: an EMPTY fragment contributes no
            // '#', which is why parse("http://h/p#").buildUpon().build() is
            // "http://h/p" on a real device.
            if (!fragment.isEmpty()) {
                builder.append('#').append(fragment.getEncoded());
            }

            return builder.toString();
        }

        @Override public Builder buildUpon() {
            return new Builder()
                    .scheme(scheme)
                    .authority(authority)
                    .path(path)
                    .query(query)
                    .fragment(fragment);
        }
    }

    /**
     * Helper class for building or manipulating URI references. Not safe for
     * concurrent use. AOSP Uri.java:1360.
     *
     * The X(String) setters take the DECODED form and encode it; the
     * encodedX(String) setters take the already-encoded form verbatim. That
     * distinction is the whole point of the Part fields -- authority("u:p@h")
     * yields "u%3Ap%40h" while encodedAuthority("u:p@h") yields "u:p@h".
     *
     * AOSP's Builder is a CONCRETE static class (not abstract). Apps do
     * `new Uri.Builder().scheme("https").path("/x").build()`; witnessed in GD
     * Lite as InstantiationError: android.net.Uri$Builder, which was just our
     * class carrying the abstract keyword.
     */
    public static final class Builder {

        private String scheme;
        private Part opaquePart;
        private Part authority;
        private PathPart path;
        private Part query;
        private Part fragment;

        public Builder() {}

        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        Builder opaquePart(Part opaquePart) {
            this.opaquePart = opaquePart;
            return this;
        }

        /** Encodes and sets the given opaque scheme-specific-part. */
        public Builder opaquePart(String opaquePart) {
            return opaquePart(Part.fromDecoded(opaquePart));
        }

        /** Sets the previously encoded opaque scheme-specific-part. */
        public Builder encodedOpaquePart(String opaquePart) {
            return opaquePart(Part.fromEncoded(opaquePart));
        }

        Builder authority(Part authority) {
            // Setting any hierarchical component makes this URI hierarchical.
            this.opaquePart = null;
            this.authority = authority;
            return this;
        }

        /** Encodes and sets the authority. */
        public Builder authority(String authority) {
            return authority(Part.fromDecoded(authority));
        }

        /** Sets the previously encoded authority. */
        public Builder encodedAuthority(String authority) {
            return authority(Part.fromEncoded(authority));
        }

        Builder path(PathPart path) {
            this.opaquePart = null;
            this.path = path;
            return this;
        }

        /** Sets the path. Leaves '/' characters intact but encodes others as
         *  necessary. If the path is not null and doesn't start with a '/', and
         *  a scheme and/or authority is set, build() prepends a '/'. */
        public Builder path(String path) {
            return path(PathPart.fromDecoded(path));
        }

        /** Sets the previously encoded path. */
        public Builder encodedPath(String path) {
            return path(PathPart.fromEncoded(path));
        }

        /** Encodes the given segment and appends it to the path. */
        public Builder appendPath(String newSegment) {
            return path(PathPart.appendDecodedSegment(path, newSegment));
        }

        /** Appends the given already-encoded segment to the path. */
        public Builder appendEncodedPath(String newSegment) {
            return path(PathPart.appendEncodedSegment(path, newSegment));
        }

        Builder query(Part query) {
            this.opaquePart = null;
            this.query = query;
            return this;
        }

        /** Encodes and sets the query. */
        public Builder query(String query) {
            return query(Part.fromDecoded(query));
        }

        /** Sets the previously encoded query. */
        public Builder encodedQuery(String query) {
            return query(Part.fromEncoded(query));
        }

        Builder fragment(Part fragment) {
            this.fragment = fragment;
            return this;
        }

        /** Encodes and sets the fragment. */
        public Builder fragment(String fragment) {
            return fragment(Part.fromDecoded(fragment));
        }

        /** Sets the previously encoded fragment. */
        public Builder encodedFragment(String fragment) {
            return fragment(Part.fromEncoded(fragment));
        }

        /**
         * Encodes the key and value and appends the parameter to the query.
         * AOSP Uri.java:1522.
         *
         * Note the null value: encode(null, null) returns null and the string
         * concatenation below then renders it as the four characters "null", so
         * appendQueryParameter("a", null) really does produce "?a=null" on a real
         * device. Mirrored deliberately -- an app that round-trips through
         * getQueryParameter reads back the string "null", not "".
         */
        public Builder appendQueryParameter(String key, String value) {
            this.opaquePart = null;

            String encodedParameter = encode(key, null) + "=" + encode(value, null);

            if (query == null) {
                query = Part.fromEncoded(encodedParameter);
                return this;
            }

            String oldQuery = query.getEncoded();
            if (oldQuery == null || oldQuery.length() == 0) {
                query = Part.fromEncoded(encodedParameter);
            } else {
                query = Part.fromEncoded(oldQuery + "&" + encodedParameter);
            }

            return this;
        }

        /** Clears the previously set query. */
        public Builder clearQuery() {
            return query((Part) null);
        }

        /**
         * Constructs a Uri with the current attributes.
         *
         * @throws UnsupportedOperationException if the URI is opaque and the
         *  scheme is null
         */
        public Uri build() {
            if (opaquePart != null) {
                if (this.scheme == null) {
                    throw new UnsupportedOperationException(
                            "An opaque URI must have a scheme.");
                }
                return new OpaqueUri(scheme, opaquePart, fragment);
            } else {
                // Hierarchical URIs should not return null for getPath().
                PathPart path = this.path;
                if (path == null || path == PathPart.NULL) {
                    path = PathPart.EMPTY;
                } else {
                    // With a scheme and/or authority the path must be absolute.
                    if (hasSchemeOrAuthority()) {
                        path = PathPart.makeAbsolute(path);
                    }
                }
                return new HierarchicalUri(scheme, authority, path, query, fragment);
            }
        }

        private boolean hasSchemeOrAuthority() {
            return scheme != null || (authority != null && authority != Part.NULL);
        }

        @Override public String toString() {
            return build().toString();
        }
    }
}
