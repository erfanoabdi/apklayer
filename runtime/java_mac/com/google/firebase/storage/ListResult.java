package com.google.firebase.storage;

import java.util.Collections;
import java.util.List;

// com.google.firebase.storage.ListResult -- the result of StorageReference.list
// / listAll. Real Storage returns the prefixes (subdirectories) and items
// (files) found under a path, plus a page token. On Mac.apk there is no backend,
// so a list query resolves to an EMPTY result (no items, no prefixes, no page
// token) -- an honest empty rather than fabricated entries.
//
// Signatures mirror the published Firebase Storage Android reference.
public class ListResult {

    private final List<StorageReference> prefixes;
    private final List<StorageReference> items;
    private final String pageToken;

    ListResult() {
        this.prefixes = Collections.emptyList();
        this.items = Collections.emptyList();
        this.pageToken = null;
    }

    public List<StorageReference> getPrefixes() { return prefixes; }

    public List<StorageReference> getItems() { return items; }

    public String getPageToken() { return pageToken; }
}
