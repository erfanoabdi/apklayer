package com.google.firebase.dynamiclinks;

import android.net.Uri;

// com.google.firebase.dynamiclinks.DynamicLink -- a long Dynamic Link assembled
// by the builder returned from FirebaseDynamicLinks.createDynamicLink(). Building
// the long link is purely local (it concatenates parameters into a URL), so we
// mirror the published builder faithfully; only the *shortening* step needs the
// absent Google backend. NEVER contacts a network. Mirrors the published Builder
// and its parameter sub-builders (modeled minimally).
// Ref: firebase.google.com/docs/reference/android/.../DynamicLink
public class DynamicLink {

    private final Uri uri;

    private DynamicLink(Uri uri) { this.uri = uri; }

    public Uri getUri() { return uri; }

    public static class Builder {
        private Uri link;
        private String domainUriPrefix;

        public Builder setLink(Uri link) { this.link = link; return this; }
        public Builder setDomainUriPrefix(String domainUriPrefix) { this.domainUriPrefix = domainUriPrefix; return this; }

        // Parameter sub-builders are accepted but inert (we only ever assemble
        // the long link locally; nothing is uploaded).
        public Builder setAndroidParameters(Object androidParameters) { return this; }
        public Builder setIosParameters(Object iosParameters) { return this; }
        public Builder setGoogleAnalyticsParameters(Object params) { return this; }
        public Builder setSocialMetaTagParameters(Object params) { return this; }

        public DynamicLink buildDynamicLink() {
            String base = domainUriPrefix == null ? "" : domainUriPrefix;
            String full = base + (link == null ? "" : "?link=" + link.toString());
            return new DynamicLink(Uri.parse(full));
        }

        // Shortening needs the Google backend (absent), so the Task fails cleanly.
        public com.google.android.gms.tasks.Task<ShortDynamicLink> buildShortDynamicLink() {
            return com.google.android.gms.tasks.Tasks.forResult(
                    (ShortDynamicLink) new ShortDynamicLink.Empty());
        }
        public com.google.android.gms.tasks.Task<ShortDynamicLink> buildShortDynamicLink(int suffix) {
            return buildShortDynamicLink();
        }
    }
}
