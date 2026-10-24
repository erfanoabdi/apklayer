package com.google.firebase.dynamiclinks;

import android.net.Uri;

// com.google.firebase.dynamiclinks.PendingDynamicLinkData -- the resolved deep
// link from an incoming Dynamic Link. Dynamic Links resolve through Google's
// servers; on a de-Googled device there is no resolver, so getDynamicLink always
// resolves to null (no pending link) and this type is rarely produced -- it
// exists so apps that read getLink()/getClickTimestamp() link. NEVER fabricates a
// deep link. Ref: firebase.google.com/docs/reference/android/.../PendingDynamicLinkData
public class PendingDynamicLinkData {

    private final Uri link;
    private final long clickTimestamp;
    private final int minimumAppVersion;

    public PendingDynamicLinkData() { this(null, 0L, 0); }
    public PendingDynamicLinkData(Uri link, long clickTimestamp, int minimumAppVersion) {
        this.link = link;
        this.clickTimestamp = clickTimestamp;
        this.minimumAppVersion = minimumAppVersion;
    }

    public Uri getLink() { return link; }
    public long getClickTimestamp() { return clickTimestamp; }
    public int getMinimumAppVersion() { return minimumAppVersion; }
}
