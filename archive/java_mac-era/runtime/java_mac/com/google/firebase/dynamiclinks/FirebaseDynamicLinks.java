package com.google.firebase.dynamiclinks;

import android.content.Intent;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

// com.google.firebase.dynamiclinks.FirebaseDynamicLinks -- entry point for
// Firebase Dynamic Links. Resolving an incoming link is a Google-server round
// trip absent on a de-Googled device, so getDynamicLink always resolves to null
// (no pending link) -- honest "no deep link arrived", NEVER a fabricated one.
// createDynamicLink builds the long link locally. Mirrors the published surface.
// NOTE: Firebase Dynamic Links is deprecated upstream but still widely linked.
// Ref: firebase.google.com/docs/reference/android/.../FirebaseDynamicLinks
public class FirebaseDynamicLinks {

    private static final FirebaseDynamicLinks INSTANCE = new FirebaseDynamicLinks();

    private FirebaseDynamicLinks() {}

    public static FirebaseDynamicLinks getInstance() { return INSTANCE; }
    public static FirebaseDynamicLinks getInstance(com.google.firebase.FirebaseApp app) { return INSTANCE; }

    public Task<PendingDynamicLinkData> getDynamicLink(Intent intent) {
        // No resolver backend: honestly report "no pending dynamic link".
        return Tasks.forResult((PendingDynamicLinkData) null);
    }

    public Task<PendingDynamicLinkData> getDynamicLink(android.net.Uri uri) {
        return Tasks.forResult((PendingDynamicLinkData) null);
    }

    public DynamicLink.Builder createDynamicLink() {
        return new DynamicLink.Builder();
    }
}
