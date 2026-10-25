package com.google.firebase.dynamiclinks;

import android.net.Uri;
import java.util.Collections;
import java.util.List;

// com.google.firebase.dynamiclinks.ShortDynamicLink -- the result of shortening a
// Dynamic Link. Shortening is a Google-server round-trip; on a de-Googled device
// there is no shortener, so createDynamicLink().buildShortDynamicLink() fails
// cleanly and this result is rarely produced -- it exists so apps that read
// getShortLink()/getPreviewLink() link. NEVER fabricates a short URL.
// Ref: firebase.google.com/docs/reference/android/.../ShortDynamicLink
public interface ShortDynamicLink {

    Uri getShortLink();

    Uri getPreviewLink();

    List<? extends Warning> getWarnings();

    interface Warning {
        String getCode();
        String getMessage();
    }

    // Returned by builders before any real shortening; honest empty result.
    final class Empty implements ShortDynamicLink {
        @Override public Uri getShortLink() { return null; }
        @Override public Uri getPreviewLink() { return null; }
        @Override public List<? extends Warning> getWarnings() { return Collections.emptyList(); }
    }
}
