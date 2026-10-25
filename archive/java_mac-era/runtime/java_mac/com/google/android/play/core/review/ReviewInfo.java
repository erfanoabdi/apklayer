package com.google.android.play.core.review;

// com.google.android.play.core.review.ReviewInfo -- the opaque token returned by
// ReviewManager.requestReviewFlow() and passed back into launchReviewFlow(). On
// real Android it is a Parcelable wrapping a PendingIntent into the Play Store's
// in-app-review UI. On a de-Googled device there is no Play Store UI, so this is
// an inert token: requestReviewFlow() still hands one back (so the app's normal
// "request then launch" sequence runs), and launchReviewFlow() simply resolves
// without showing anything. The published class has no public methods apps call
// directly (it is treated as an opaque handle), so we expose none.
public final class ReviewInfo {
    ReviewInfo() {}

    @Override
    public String toString() {
        return "ReviewInfo{}";
    }
}
