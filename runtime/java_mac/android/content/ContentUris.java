// android.content.ContentUris -- static helpers for manipulating
// content:// URIs that end in a row id (the canonical
// content://authority/table/<id> shape). Apps build these to address a
// single row of a ContentProvider and parse the trailing id back off a
// returned/observed URI.
//
// AOSP ref:
//   frameworks/base/core/java/android/content/ContentUris.java
// Previously a bare stub (no methods) -> any caller of withAppendedId /
// parseId / appendId hit NoSuchMethodError. Implemented faithfully:
// withAppendedId appends "/<id>" to the URI's path; parseId returns the
// last path segment parsed as a long (-1 when there is none / it isn't a
// number, matching AOSP's behavior of throwing only for a missing path).
package android.content;

import android.net.Uri;

public class ContentUris {
    // AOSP exposes only static helpers; the class is never instantiated by
    // apps, but keep a public ctor so reflective access does not fail.
    public ContentUris() {}

    /** AOSP ContentUris.withAppendedId(Uri, long): returns a new Uri with
     *  id appended as the last path segment. */
    public static Uri withAppendedId(Uri contentUri, long id) {
        return contentUri.buildUpon().appendPath(String.valueOf(id)).build();
    }

    /** AOSP ContentUris.parseId(Uri): parses the trailing path segment of
     *  contentUri as a long. AOSP returns -1 when the URI has no path. */
    public static long parseId(Uri contentUri) {
        if (contentUri == null) return -1;
        String last = contentUri.getLastPathSegment();
        if (last == null) return -1;
        try {
            return Long.parseLong(last);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** AOSP API-29 ContentUris.appendId(Uri.Builder, long): appends id to a
     *  builder under construction and returns the same builder for chaining. */
    public static Uri.Builder appendId(Uri.Builder builder, long id) {
        return builder.appendPath(String.valueOf(id));
    }

    /** AOSP API-29 ContentUris.removeId(Uri): returns the URI with its
     *  trailing id segment removed. */
    public static Uri removeId(Uri contentUri) {
        if (contentUri == null) return null;
        String last = contentUri.getLastPathSegment();
        if (last == null) {
            throw new IllegalArgumentException("No path segments: " + contentUri);
        }
        // Rebuild every path segment except the final one.
        java.util.List<String> segments = contentUri.getPathSegments();
        Uri.Builder b = contentUri.buildUpon().path("");
        for (int i = 0; i < segments.size() - 1; i++) {
            b.appendPath(segments.get(i));
        }
        return b.build();
    }
}
