package android.content;

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

/**
 * AOSP-faithful UriMatcher: a tree of authority/path-segment nodes mapping a
 * matched Uri to an int code. GeoDash Meltdown (and any ContentProvider-using
 * app) constructs one at class-init; a missing class threw NoClassDefFoundError.
 * Mirrors the public contract of android.content.UriMatcher.
 */
public class UriMatcher {
    public static final int NO_MATCH = -1;

    private static final int EXACT = 0;
    private static final int NUMBER = 1;
    private static final int TEXT = 2;

    private int mCode;
    private int mTokenType = EXACT;
    private String mText;
    private final ArrayList<UriMatcher> mChildren = new ArrayList<>();

    public UriMatcher(int code) {
        mCode = code;
        mText = null;
    }

    private UriMatcher() {
        mCode = NO_MATCH;
    }

    public void addURI(String authority, String path, int code) {
        if (code < 0) throw new IllegalArgumentException("code " + code + " is invalid: it must be positive");
        String[] tokens = path != null ? PATH_SPLIT(path) : null;
        int numTokens = tokens != null ? tokens.length : 0;
        UriMatcher node = this;
        for (int i = -1; i < numTokens; i++) {
            String token = i < 0 ? authority : tokens[i];
            ArrayList<UriMatcher> children = node.mChildren;
            int numChildren = children.size();
            UriMatcher child = null;
            int j;
            for (j = 0; j < numChildren; j++) {
                child = children.get(j);
                if (token.equals(child.mText)) { node = child; break; }
            }
            if (j == numChildren) {
                child = new UriMatcher();
                if ("#".equals(token)) child.mTokenType = NUMBER;
                else if ("*".equals(token)) child.mTokenType = TEXT;
                else child.mTokenType = EXACT;
                child.mText = token;
                node.mChildren.add(child);
                node = child;
            }
        }
        node.mCode = code;
    }

    private static String[] PATH_SPLIT(String path) {
        // strip leading slash then split on '/'
        if (path.startsWith("/")) path = path.substring(1);
        if (path.isEmpty()) return new String[0];
        return path.split("/");
    }

    public int match(Uri uri) {
        if (uri == null) return NO_MATCH;
        final List<String> pathSegments = uri.getPathSegments();
        final int li = pathSegments.size();
        UriMatcher node = this;
        if (li == 0 && uri.getAuthority() == null) return mCode;
        for (int i = -1; i < li; i++) {
            String u = i < 0 ? uri.getAuthority() : pathSegments.get(i);
            ArrayList<UriMatcher> list = node.mChildren;
            if (list == null) break;
            node = null;
            int lj = list.size();
            for (int j = 0; j < lj; j++) {
                UriMatcher n = list.get(j);
                switch (n.mTokenType) {
                    case EXACT:  if (n.mText.equals(u)) node = n; break;
                    case NUMBER:
                        int lk = u != null ? u.length() : 0;
                        boolean num = lk > 0;
                        for (int k = 0; k < lk; k++) { char c = u.charAt(k); if (c < '0' || c > '9') { num = false; break; } }
                        if (num) node = n; break;
                    case TEXT:   node = n; break;
                }
                if (node != null) break;
            }
            if (node == null) return NO_MATCH;
        }
        return node.mCode;
    }
}
