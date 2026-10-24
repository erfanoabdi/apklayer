package com.google.android.play.core.review;

import android.content.Context;

// com.google.android.play.core.review.ReviewManagerFactory -- the static factory
// apps call to obtain a ReviewManager. Mirrors the published Play Core factory
// (a single static create(Context)). Returns our no-UI ReviewManagerImpl so the
// in-app-review flow degrades to "nothing shown, continue" instead of crashing.
public final class ReviewManagerFactory {
    private ReviewManagerFactory() {}

    public static ReviewManager create(Context context) {
        return new ReviewManagerImpl(context);
    }
}
