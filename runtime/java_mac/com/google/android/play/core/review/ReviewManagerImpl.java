package com.google.android.play.core.review;

import android.app.Activity;
import android.content.Context;
import com.google.android.play.core.tasks.Task;
import com.google.android.play.core.tasks.Tasks;

// Internal concrete ReviewManager. NOT part of the published Play Core API (apps
// only see the ReviewManager interface) -- this is our backing impl. Both flows
// resolve cleanly with no UI, matching real Play Core's "we chose not to show
// the dialog" contract.
final class ReviewManagerImpl implements ReviewManager {

    ReviewManagerImpl(Context context) {
        // No backend; nothing to retain.
    }

    @Override
    public Task<ReviewInfo> requestReviewFlow() {
        // Hand back an inert token so the app proceeds to launchReviewFlow().
        return Tasks.forResult(new ReviewInfo());
    }

    @Override
    public Task<Void> launchReviewFlow(Activity activity, ReviewInfo reviewInfo) {
        // Real Play Core resolves this Task whether or not the dialog showed;
        // we resolve immediately (no dialog) so the app continues normally.
        return Tasks.forResult(null);
    }
}
